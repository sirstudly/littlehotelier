import { isBotId, getBotUserParts } from "./botIdentity.js";
import { config, isAllowlistedGroup, normalizeJid } from "./env.js";
import type { MembershipCache } from "./membership.js";
import type { TranscriptStore } from "./transcript.js";
import type { NormalizedInbound } from "./waha/types.js";

export type TriggerReason = "mention" | "followup" | "dm" | null;

export function shouldHandle(
  msg: NormalizedInbound,
  membership: MembershipCache,
  transcript: TranscriptStore,
): TriggerReason {
  if (msg.fromMe) return null;

  const chatId = normalizeJid(msg.chatId);
  const senderId = normalizeJid(msg.senderId);

  if (msg.isGroup) {
    if (!isAllowlistedGroup(chatId)) return null;
    if (mentionsRonbot(msg)) return "mention";
    if (!msg.body) return null;
    if (isFollowUp(chatId, msg, transcript)) return "followup";
    return null;
  }

  // 1:1 DM — WAHA/NOWEB often uses @lid (not @c.us) for private chats
  if (!msg.body) return null;
  if (!isDirectChat(chatId)) return null;
  if (!membership.isAuthorizedDmSender(senderId)) return null;
  return "dm";
}

/** Private chat JIDs (phone or Linked ID). Groups are @g.us. */
export function isDirectChat(chatId: string): boolean {
  const id = normalizeJid(chatId);
  return id.endsWith("@c.us") || id.endsWith("@lid");
}

/**
 * Group triggers:
 * - WhatsApp @-mention of the bot (LID / phone in `mentionedIds` or `@<lid>` in body)
 * - Text alias: configured `RONBOT_MENTION`, `@~ronbot`, or token `ronbot`
 */
export function mentionsRonbot(msg: Pick<NormalizedInbound, "body" | "mentionedIds">): boolean {
  for (const id of msg.mentionedIds ?? []) {
    if (isBotId(id)) return true;
  }

  const body = msg.body ?? "";
  if (!body) return false;

  // `@99900011122233 …` style that WAHA puts in body for LID mentions
  for (const m of body.matchAll(/@(\d{5,})/g)) {
    if (isBotId(m[1]!) || getBotUserParts().has(m[1]!)) return true;
  }

  const mention = config.mention.toLowerCase();
  const text = body.toLowerCase();
  if (mention && text.includes(mention)) return true;
  // push-name UI (`@~ronbot`) and bare token
  if (text.includes("@~ronbot")) return true;
  return /(?:^|[\s@~])ronbot(?:\b|$)/i.test(body);
}

function isFollowUp(
  chatId: string,
  msg: NormalizedInbound,
  transcript: TranscriptStore,
): boolean {
  if (transcript.isReplyToBot(chatId, msg.replyToId)) return true;
  const age = transcript.lastBotReplyAgeMs(chatId);
  if (age == null) return false;
  const windowMs = config.followUpMinutes * 60 * 1000;
  if (age > windowMs) return false;
  // Prefer short / question-like follow-ups to reduce false positives
  if (msg.body.length <= 280) return true;
  if (/[?]/.test(msg.body)) return true;
  return false;
}
