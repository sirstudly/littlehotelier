/**
 * Offline smoke: trigger policy + transcript + JID normalize (no WAHA / Cursor).
 * Run: npx tsx scripts/smoke-triggers.ts
 */
import { rememberBotIds } from "../src/botIdentity.js";
import { MembershipCache } from "../src/membership.js";
import { TranscriptStore } from "../src/transcript.js";
import { shouldHandle } from "../src/triggers.js";
import type { NormalizedInbound } from "../src/waha/types.js";
import { extractMentionedIds } from "../src/waha/types.js";
import { isAllowlistedGroup, normalizeJid } from "../src/env.js";

rememberBotIds(["447700900010@c.us", "99900011122233@lid"]);

function assert(cond: unknown, msg: string): void {
  if (!cond) throw new Error(msg);
}

class FakeMembership extends MembershipCache {
  constructor(private readonly allowed: Set<string>) {
    // MembershipCache needs a WahaClient; we never call refresh in this smoke.
    super({} as never);
  }
  override isAuthorizedDmSender(senderId: string): boolean {
    return this.allowed.has(normalizeJid(senderId));
  }
}

const groupId = "120363999999999999@g.us";
const member = "447700900001@c.us";
const memberLid = "99900011122211@lid";
const stranger = "447700900099@c.us";

// Patch allowlist for this process if empty
if (!isAllowlistedGroup(groupId)) {
  const { config } = await import("../src/env.js");
  (config.groups as string[]).push(groupId);
}

const membership = new FakeMembership(new Set([member, memberLid]));
const transcript = new TranscriptStore();

function msg(partial: Partial<NormalizedInbound> & Pick<NormalizedInbound, "chatId" | "senderId" | "body" | "isGroup">): NormalizedInbound {
  return {
    event: "message",
    messageId: partial.messageId ?? `m-${Math.random()}`,
    fromMe: false,
    timestampMs: Date.now(),
    mentionedIds: [],
    ...partial,
  };
}

assert(
  shouldHandle(
    msg({ chatId: groupId, senderId: member, body: "hey @ronbot what is EVL?", isGroup: true }),
    membership,
    transcript,
  ) === "mention",
  "group @ronbot should trigger mention",
);

assert(
  shouldHandle(
    msg({
      chatId: groupId,
      senderId: member,
      body: "@99900011122233 looking for a booking?",
      isGroup: true,
      mentionedIds: ["99900011122233@lid"],
    }),
    membership,
    transcript,
  ) === "mention",
  "group LID @-mention should trigger",
);

assert(
  shouldHandle(
    msg({
      chatId: groupId,
      senderId: member,
      body: "@~ronbot ping",
      isGroup: true,
    }),
    membership,
    transcript,
  ) === "mention",
  "group @~ronbot push-name text should trigger",
);

assert(
  shouldHandle(
    msg({ chatId: groupId, senderId: member, body: "unrelated chatter", isGroup: true }),
    membership,
    transcript,
  ) === null,
  "group without mention/follow-up should ignore",
);

const extractedMentions = extractMentionedIds({
  _data: {
    message: {
      extendedTextMessage: {
        text: "@99900011122233 hi",
        contextInfo: { mentionedJid: ["99900011122233@lid"] },
      },
    },
  },
});
assert(extractedMentions.includes("99900011122233@lid"), "extract mentionedJid from _data");

transcript.markBotReply(groupId);
assert(
  shouldHandle(
    msg({ chatId: groupId, senderId: member, body: "and for rmb?", isGroup: true }),
    membership,
    transcript,
  ) === "followup",
  "short follow-up after bot reply should trigger",
);

assert(
  shouldHandle(
    msg({ chatId: member, senderId: member, body: "timeline for 123?", isGroup: false }),
    membership,
    transcript,
  ) === "dm",
  "authorized DM should always handle",
);

assert(
  shouldHandle(
    msg({
      chatId: memberLid,
      senderId: memberLid,
      body: "could you lookup a booking?",
      isGroup: false,
    }),
    membership,
    transcript,
  ) === "dm",
  "authorized LID DM should handle",
);

assert(
  shouldHandle(
    msg({ chatId: stranger, senderId: stranger, body: "hello", isGroup: false }),
    membership,
    transcript,
  ) === null,
  "unauthorized DM should ignore",
);

assert(normalizeJid("447700900001@s.whatsapp.net") === "447700900001@c.us", "jid normalize");

// LID + phoneNumber extraction (WAHA NOWEB / addressingMode=lid)
const { extractParticipantIds } = await import("../src/waha/client.js");
const extracted = extractParticipantIds([
  { id: "99900011122211@lid", pn: "447700900003@c.us", role: "superadmin" },
  { id: "99900011122233@lid", phoneNumber: "447700900010@s.whatsapp.net", admin: null },
]).map(normalizeJid);
assert(extracted.includes("99900011122211@lid"), "keep @lid");
assert(extracted.includes("447700900003@c.us"), "keep pn as @c.us");
assert(extracted.includes("447700900010@c.us"), "normalize phoneNumber to @c.us");

console.log("smoke-triggers: ok");
