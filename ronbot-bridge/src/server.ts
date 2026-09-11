import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { rememberBotIds } from "./botIdentity.js";
import { config, isAllowlistedGroup, normalizeJid } from "./env.js";
import { askRonbot, chunkWhatsAppText } from "./agent/runner.js";
import { MembershipCache } from "./membership.js";
import { TranscriptStore } from "./transcript.js";
import { isDirectChat, shouldHandle } from "./triggers.js";
import { WahaClient } from "./waha/client.js";
import { normalizeInbound, type WahaWebhookEvent } from "./waha/types.js";

const waha = new WahaClient();
const membership = new MembershipCache(waha);
const transcript = new TranscriptStore();
const recentMessageIds = new Set<string>();
const processingChats = new Set<string>();

function readJson(req: IncomingMessage): Promise<unknown> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = [];
    req.on("data", (c) => chunks.push(Buffer.from(c)));
    req.on("end", () => {
      try {
        const raw = Buffer.concat(chunks).toString("utf8");
        resolve(raw ? JSON.parse(raw) : {});
      } catch (err) {
        reject(err);
      }
    });
    req.on("error", reject);
  });
}

function send(res: ServerResponse, status: number, body: unknown): void {
  const payload = typeof body === "string" ? body : JSON.stringify(body);
  res.writeHead(status, { "Content-Type": "application/json; charset=utf-8" });
  res.end(payload);
}

async function handleWahaWebhook(event: WahaWebhookEvent): Promise<void> {
  if (event.me) {
    rememberBotIds([event.me.id, event.me.lid]);
  }

  if (event.event === "group.v2.participants") {
    membership.applyParticipantEvent(event);
    return;
  }

  const msg = normalizeInbound(event);
  if (!msg) return;

  const chatId = normalizeJid(msg.chatId);
  const senderId = normalizeJid(msg.senderId);

  // Always record non-empty inbound into transcript for allowlisted groups / authorized DMs
  const trackGroup = msg.isGroup && isAllowlistedGroup(chatId);
  const trackDm =
    !msg.isGroup && isDirectChat(chatId) && membership.isAuthorizedDmSender(senderId);
  if ((trackGroup || trackDm) && msg.body) {
    transcript.append(chatId, {
      at: msg.timestampMs,
      senderId,
      body: msg.body,
      fromMe: msg.fromMe,
      messageId: msg.messageId,
    });
  }

  const reason = shouldHandle(msg, membership, transcript);
  if (!reason) return;

  if (recentMessageIds.has(msg.messageId)) return;
  recentMessageIds.add(msg.messageId);
  if (recentMessageIds.size > 500) {
    const first = recentMessageIds.values().next().value;
    if (first) recentMessageIds.delete(first);
  }

  if (processingChats.has(chatId)) {
    await waha.sendText(
      chatId,
      "Still working on the previous question in this chat — please wait a moment.",
    );
    return;
  }

  processingChats.add(chatId);
  let typingTimer: ReturnType<typeof setInterval> | undefined;
  try {
    await waha.startTyping(chatId);
    typingTimer = setInterval(() => {
      void waha.startTyping(chatId);
    }, 15_000);

    const context = transcript.formatForPrompt(chatId);
    const prompt = [
      `WhatsApp ${msg.isGroup ? "group" : "DM"} chatId=${chatId}`,
      `Trigger=${reason}`,
      `From=${senderId}`,
      "",
      "Recent chat context:",
      context || "(empty)",
      "",
      "Latest message to answer:",
      msg.body,
    ].join("\n");

    console.log(`handling ${reason} chat=${chatId} from=${senderId}`);
    const answer = await askRonbot(chatId, prompt);
    const chunks = chunkWhatsAppText(answer);
    let lastId: string | undefined;
    for (const chunk of chunks) {
      lastId = await waha.sendText(chatId, chunk);
      transcript.append(chatId, {
        at: Date.now(),
        senderId: "ronbot",
        body: chunk,
        fromMe: true,
        messageId: lastId,
      });
    }
    transcript.markBotReply(chatId, lastId);
  } catch (err) {
    console.error("handle failed", err);
    try {
      await waha.sendText(
        chatId,
        "Sorry — I hit an internal error answering that. Please try again shortly.",
      );
    } catch (sendErr) {
      console.error("error reply failed", sendErr);
    }
  } finally {
    if (typingTimer) clearInterval(typingTimer);
    await waha.stopTyping(chatId);
    processingChats.delete(chatId);
  }
}

export function startServer(): void {
  const server = createServer(async (req, res) => {
    const url = new URL(req.url ?? "/", `http://${req.headers.host ?? "localhost"}`);

    if (req.method === "GET" && url.pathname === "/health") {
      send(res, 200, {
        ok: true,
        groups: config.groups.length,
        membersCached: membership.size(),
      });
      return;
    }

    if (req.method === "POST" && url.pathname === "/webhooks/waha") {
      try {
        const body = (await readJson(req)) as WahaWebhookEvent;
        // Ack quickly; process async
        send(res, 200, { ok: true });
        void handleWahaWebhook(body);
      } catch (err) {
        console.error("webhook parse error", err);
        send(res, 400, { error: "invalid json" });
      }
      return;
    }

    send(res, 404, { error: "not found" });
  });

  server.listen(config.port, () => {
    console.log(`ronbot-bridge listening on :${config.port}`);
    console.log(`allowlisted groups: ${config.groups.length}`);
    void refreshBotIdentity();
    void membership.refresh(true);
    setInterval(() => void membership.refresh(false), config.membershipRefreshMs);
    setInterval(() => void refreshBotIdentity(), config.membershipRefreshMs);
  });
}

async function refreshBotIdentity(): Promise<void> {
  try {
    const me = await waha.getSessionMe();
    if (!me) {
      console.warn("WAHA session me unavailable — LID mentions may not match until a webhook arrives");
      return;
    }
    rememberBotIds([me.id, me.lid]);
    console.log(
      `bot identity: ${[me.pushName, me.id, me.lid].filter(Boolean).join(" / ")}`,
    );
  } catch (err) {
    console.warn("failed to refresh WAHA bot identity", err);
  }
}
