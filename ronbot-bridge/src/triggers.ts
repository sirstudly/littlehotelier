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
  if (!msg.body) return null;

  const chatId = normalizeJid(msg.chatId);
  const senderId = normalizeJid(msg.senderId);

  if (msg.isGroup) {
    if (!isAllowlistedGroup(chatId)) return null;
    if (mentionsRonbot(msg.body)) return "mention";
    if (isFollowUp(chatId, msg, transcript)) return "followup";
    return null;
  }

  // DM (@c.us)
  if (!chatId.endsWith("@c.us")) return null;
  if (!membership.isAuthorizedDmSender(senderId)) return null;
  return "dm";
}

function mentionsRonbot(body: string): boolean {
  const mention = config.mention.toLowerCase();
  const text = body.toLowerCase();
  if (text.includes(mention)) return true;
  // bare "ronbot" as a token
  return /(?:^|[\s@])ronbot(?:\b|$)/i.test(body);
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
