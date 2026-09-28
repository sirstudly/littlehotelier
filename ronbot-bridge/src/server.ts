import { createServer, type IncomingMessage, type ServerResponse } from "node:http";
import { rememberBotIds } from "./botIdentity.js";
import {
  config,
  getGroupProperty,
  isAllowlistedGroup,
  normalizeJid,
  type PropertyId,
} from "./env.js";
import { timingSafeEqual } from "node:crypto";
import { askRonbot, chunkWhatsAppText, hasPrimedAgent } from "./agent/runner.js";
import { extractGuestRequests, guestRequestInputSchema } from "./agent/guestRequests.js";
import { MembershipCache } from "./membership.js";
import {
  TranscriptStore,
  transcriptEntryFromPayload,
  type TranscriptEntry,
} from "./transcript.js";
import { isDirectChat, shouldHandle } from "./triggers.js";
import { resolveRequester, type Requester } from "./users.js";
import { WahaClient } from "./waha/client.js";
import { startSessionMonitor } from "./waha/sessionMonitor.js";
import { normalizeInbound, type WahaWebhookEvent } from "./waha/types.js";

/** Prompt lines for default / candidate property context (empty if none). */
export function propertyContextLines(
  isGroup: boolean,
  chatId: string,
  senderId: string,
  membership: MembershipCache,
): string[] {
  if (isGroup) {
    const property = getGroupProperty(chatId);
    if (!property) return [];
    return [
      `DefaultProperty=${property} (from this group; use unless staff name another property)`,
    ];
  }

  const properties: PropertyId[] = membership.getSenderProperties(senderId);
  if (properties.length === 1) {
    return [
      `DefaultProperty=${properties[0]} (from staff group membership; use unless they name another)`,
    ];
  }
  if (properties.length > 1) {
    return [
      `CandidateProperties=${properties.join(",")} (ask which unless the message already names a property)`,
    ];
  }
  return [];
}

/** Prompt line identifying who asked, so "email me" maps to their alias. */
export function requesterContextLine(requester: Requester | undefined): string {
  if (!requester) {
    return `Requester=unknown (ask for an email or alias if they want something emailed to "me")`;
  }
  return `Requester=${requester.alias} (${requester.email}; use alias "${requester.alias}" when they say me/my email)`;
}

/**
 * All DM chat ids for one person (phone `@c.us` and linked `@lid`), canonical key first.
 * The phone id is preferred as the key so it stays stable once linked ids are known.
 */
export function dmChatIds(chatId: string, membership: MembershipCache): string[] {
  const ids = [
    ...new Set([chatId, ...membership.getLinkedIds(chatId)].map(normalizeJid)),
  ].filter(isDirectChat);
  const phones = ids.filter((id) => id.endsWith("@c.us")).sort();
  const key = phones[0] ?? normalizeJid(chatId);
  return [key, ...ids.filter((id) => id !== key)];
}

const waha = new WahaClient();
const membership = new MembershipCache(waha);
const transcript = new TranscriptStore();
const recentMessageIds = new Set<string>();
const processingChats = new Set<string>();
const historyLoads = new Map<string, Promise<void>>();

/** Load recent WhatsApp history into the transcript once per chat key since startup. */
async function ensureHistory(chatKey: string, chatIds: string[]): Promise<void> {
  if (transcript.isSeeded(chatKey)) return;
  if (config.backfillLimit <= 0) {
    transcript.seed(chatKey, []);
    return;
  }
  let load = historyLoads.get(chatKey);
  if (!load) {
    load = (async () => {
      const batches = await Promise.all(
        chatIds.map((id) => waha.getChatMessages(id, config.backfillLimit)),
      );
      const entries = batches
        .flat()
        .map(transcriptEntryFromPayload)
        .filter((e): e is TranscriptEntry => e !== null);
      transcript.seed(chatKey, entries);
      console.log(
        `history loaded chat=${chatKey} ids=${chatIds.length} messages=${entries.length}`,
      );
    })().finally(() => historyLoads.delete(chatKey));
    historyLoads.set(chatKey, load);
  }
  await load;
}

/** Agent silence contract: exact NO_REPLY (trim + case-insensitive) means skip send. */
export function isNoReply(answer: string): boolean {
  return /^NO_REPLY$/i.test(answer.trim());
}

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

/** Internal endpoints require `Authorization: Bearer $RONBOT_TOKEN`; port 8787 is published on the host. */
export function isAuthorizedInternal(authHeader: string | undefined): boolean {
  if (!config.ronbotToken || !authHeader) return false;
  const expected = Buffer.from(`Bearer ${config.ronbotToken}`);
  const actual = Buffer.from(authHeader);
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}

async function handleGuestRequests(req: IncomingMessage, res: ServerResponse): Promise<void> {
  if (!isAuthorizedInternal(req.headers.authorization)) {
    send(res, 401, { error: "unauthorized" });
    return;
  }
  let body: unknown;
  try {
    body = await readJson(req);
  } catch {
    send(res, 400, { error: "invalid json" });
    return;
  }
  const input = guestRequestInputSchema.safeParse(body);
  if (!input.success) {
    send(res, 400, { error: input.error.message });
    return;
  }
  try {
    send(res, 200, await extractGuestRequests(input.data));
  } catch (err) {
    console.error("guest-requests failed", err);
    send(res, 502, { error: (err as Error).message.slice(0, 500) });
  }
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
  const transcriptBody = msg.body || (msg.hasMedia ? "[image]" : "");

  // Always record inbound into transcript for allowlisted groups / authorized DMs
  const trackGroup = msg.isGroup && isAllowlistedGroup(chatId);
  const trackDm =
    !msg.isGroup && isDirectChat(chatId) && membership.isAuthorizedDmSender(senderId);
  const chatIds = msg.isGroup ? [chatId] : dmChatIds(chatId, membership);
  const chatKey = chatIds[0]!;
  if (trackGroup || trackDm) {
    await ensureHistory(chatKey, chatIds);
  }
  if ((trackGroup || trackDm) && transcriptBody) {
    transcript.append(chatKey, {
      at: msg.timestampMs,
      senderId,
      body: transcriptBody,
      fromMe: msg.fromMe,
      messageId: msg.messageId,
    });
  }

  const reason = shouldHandle(msg, membership, transcript, chatKey);
  if (!reason) return;

  if (recentMessageIds.has(msg.messageId)) return;
  recentMessageIds.add(msg.messageId);
  if (recentMessageIds.size > 500) {
    const first = recentMessageIds.values().next().value;
    if (first) recentMessageIds.delete(first);
  }

  if (processingChats.has(chatKey)) {
    await waha.sendText(
      chatId,
      "Still working on the previous question in this chat — please wait a moment.",
    );
    return;
  }

  processingChats.add(chatKey);
  let typingTimer: ReturnType<typeof setInterval> | undefined;
  try {
    await waha.startTyping(chatId);
    typingTimer = setInterval(() => {
      void waha.startTyping(chatId);
    }, 15_000);

    let images: { data: string; mimeType: string }[] | undefined;
    if (msg.media) {
      try {
        const downloaded = await waha.downloadMedia(msg.media.url, msg.media.mimetype);
        images = [downloaded];
      } catch (err) {
        console.error("media download failed", err);
        await waha.sendText(
          chatId,
          "I couldn't download that image — please resend it, or paste the reservation id / guest name as text.",
        );
        return;
      }
    }

    const context = transcript.formatForPrompt(chatKey);
    const exchanges = transcript.formatRonbotExchanges(chatKey);
    const restartLines =
      !hasPrimedAgent(chatKey) && transcript.hasBackfilledHistory(chatKey)
        ? [
            "Note: bridge restarted; the history below was reloaded from WhatsApp and you have no memory of earlier turns beyond it.",
          ]
        : [];
    const latestLines = [
      msg.body || "(no caption)",
      images?.length
        ? `Attached image: ${images[0]!.mimeType} (screenshot from WhatsApp)`
        : null,
    ].filter(Boolean);
    const propertyLines = propertyContextLines(
      msg.isGroup,
      chatId,
      senderId,
      membership,
    );
    const requester = resolveRequester(senderId, membership.getLinkedIds(senderId));
    const prompt = [
      `WhatsApp ${msg.isGroup ? "group" : "DM"} chatId=${chatId}`,
      `Trigger=${reason}`,
      `From=${senderId}`,
      `Today=${new Date().toLocaleDateString("en-CA", { timeZone: "Europe/London" })}`,
      requesterContextLine(requester),
      ...propertyLines,
      ...restartLines,
      "",
      "Recent ronbot exchanges (oldest first):",
      exchanges || "(none)",
      "",
      "Recent chat context:",
      context || "(empty)",
      "",
      "Latest message to answer:",
      ...latestLines,
    ].join("\n");

    console.log(
      `handling ${reason} chat=${chatId}${chatKey !== chatId ? ` key=${chatKey}` : ""} from=${senderId} requester=${requester?.alias ?? "unknown"}${images?.length ? " images=1" : ""}${restartLines.length ? " history=reloaded" : ""}`,
    );
    const answer = await askRonbot(chatKey, prompt, images);
    if (isNoReply(answer)) {
      console.log(`silence NO_REPLY chat=${chatId} from=${senderId}`);
      return;
    }
    const chunks = chunkWhatsAppText(answer);
    let lastId: string | undefined;
    for (const chunk of chunks) {
      lastId = await waha.sendText(chatId, chunk);
      transcript.append(chatKey, {
        at: Date.now(),
        senderId: "ronbot",
        body: chunk,
        fromMe: true,
        messageId: lastId,
      });
    }
    transcript.markBotReply(chatKey, lastId, senderId);
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
    processingChats.delete(chatKey);
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

    if (req.method === "POST" && url.pathname === "/internal/guest-requests") {
      await handleGuestRequests(req, res);
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
    startSessionMonitor(waha);
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
