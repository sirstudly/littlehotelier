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
  config.groups.push({ id: groupId });
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
    hasMedia: false,
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

assert(
  shouldHandle(
    msg({
      chatId: groupId,
      senderId: member,
      body: "",
      isGroup: true,
      hasMedia: true,
      media: {
        url: "http://localhost:3000/api/files/x.jpg",
        mimetype: "image/jpeg",
      },
    }),
    membership,
    transcript,
  ) === null,
  "group image-only without follow-up should ignore",
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

transcript.markBotReply(groupId, undefined, member);
assert(
  shouldHandle(
    msg({ chatId: groupId, senderId: member, body: "and for rmb?", isGroup: true }),
    membership,
    transcript,
  ) === "followup",
  "same-sender short follow-up after bot reply should trigger",
);

const otherMember = "447700900002@c.us";
assert(
  shouldHandle(
    msg({ chatId: groupId, senderId: otherMember, body: "unrelated short chatter", isGroup: true }),
    membership,
    transcript,
  ) === null,
  "other member short chatter during follow-up window should ignore",
);

const botMsgId = "bot-msg-1";
transcript.markBotReply(groupId, botMsgId, member);
assert(
  shouldHandle(
    msg({
      chatId: groupId,
      senderId: otherMember,
      body: "also check guest X",
      isGroup: true,
      replyToId: botMsgId,
    }),
    membership,
    transcript,
  ) === "followup",
  "reply-to-bot from another member should trigger",
);

const { isNoReply } = await import("../src/server.js");
assert(isNoReply("NO_REPLY"), "NO_REPLY sentinel recognized");
assert(isNoReply("  no_reply  "), "NO_REPLY is case-insensitive and trimmed");
assert(!isNoReply("NO_REPLY thanks"), "NO_REPLY must be exact");
assert(!isNoReply("ok"), "ordinary text is not silence");

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

assert(
  shouldHandle(
    msg({
      chatId: memberLid,
      senderId: memberLid,
      body: "",
      isGroup: false,
      hasMedia: true,
      media: {
        url: "http://localhost:3000/api/files/x.jpg",
        mimetype: "image/jpeg",
      },
    }),
    membership,
    transcript,
  ) === "dm",
  "authorized LID DM image-only should handle",
);

const { extractAllowedImageMedia } = await import("../src/waha/types.js");
assert(
  extractAllowedImageMedia({
    hasMedia: true,
    media: { url: "http://waha:3000/api/files/a.jpg", mimetype: "image/jpeg" },
  })?.mimetype === "image/jpeg",
  "allowed jpeg media extracted",
);
assert(
  extractAllowedImageMedia({
    hasMedia: true,
    media: { url: "http://waha:3000/api/files/a.pdf", mimetype: "application/pdf" },
  }) === undefined,
  "pdf media rejected",
);

const { rewriteMediaUrl } = await import("../src/waha/client.js");
assert(
  rewriteMediaUrl(
    "http://localhost:3000/api/files/x.jpg",
    "http://waha:3000",
  ) === "http://waha:3000/api/files/x.jpg",
  "rewrite localhost media URL for Docker",
);

assert(normalizeJid("447700900001@s.whatsapp.net") === "447700900001@c.us", "jid normalize");

const {
  parseGroupEntry,
  parseGroupEntriesFromEnv,
  parseGroupsJson,
} = await import("../src/env.js");
assert(
  JSON.stringify(parseGroupEntry("120363aaa@g.us")) ===
    JSON.stringify({ id: "120363aaa@g.us" }),
  "parse plain group jid",
);
assert(
  JSON.stringify(parseGroupEntry("120363aaa@g.us:hsh")) ===
    JSON.stringify({ id: "120363aaa@g.us", property: "hsh" }),
  "parse group jid with property",
);
assert(
  JSON.stringify(parseGroupEntry("120363aaa@g.us:HSH")) ===
    JSON.stringify({ id: "120363aaa@g.us", property: "hsh" }),
  "parse property case-insensitive",
);
assert(
  JSON.stringify(parseGroupEntry("120363aaa@g.us:nope")) ===
    JSON.stringify({ id: "120363aaa@g.us" }),
  "invalid property ignored, still allowlisted",
);
assert(
  JSON.stringify(
    parseGroupEntriesFromEnv(
      "120363aaa@g.us:hsh, 120363bbb@g.us ,120363ccc@g.us:rmb",
    ),
  ) ===
    JSON.stringify([
      { id: "120363aaa@g.us", property: "hsh" },
      { id: "120363bbb@g.us" },
      { id: "120363ccc@g.us", property: "rmb" },
    ]),
  "parse env comma list",
);
assert(
  JSON.stringify(
    parseGroupsJson({
      groups: [
        { id: "120363aaa@g.us", property: "hsh" },
        "120363ccc@g.us",
        { id: "120363ddd@g.us", property: "bad" },
      ],
    }),
  ) ===
    JSON.stringify([
      { id: "120363aaa@g.us", property: "hsh" },
      { id: "120363ccc@g.us" },
      { id: "120363ddd@g.us" },
    ]),
  "parse groups.json mixed shapes",
);

const { propertyContextLines } = await import("../src/server.js");
class PropMembership extends MembershipCache {
  constructor(private readonly props: Map<string, string[]>) {
    super({} as never);
  }
  override getSenderProperties(senderId: string) {
    return (this.props.get(normalizeJid(senderId)) ?? []) as (
      | "crh"
      | "hsh"
      | "rmb"
      | "lsh"
    )[];
  }
}
const hshGroup = "120363hsh000000000@g.us";
{
  const { config } = await import("../src/env.js");
  if (!config.groups.some((g) => g.id === hshGroup)) {
    config.groups.push({ id: hshGroup, property: "hsh" });
  }
}
assert(
  propertyContextLines(true, hshGroup, member, membership).some((l) =>
    l.startsWith("DefaultProperty=hsh"),
  ),
  "group prompt gets DefaultProperty from tag",
);
assert(
  propertyContextLines(true, groupId, member, membership).length === 0,
  "untagged group has no property context",
);
assert(
  propertyContextLines(
    false,
    member,
    member,
    new PropMembership(new Map([[member, ["lsh"]]])),
  ).some((l) => l.startsWith("DefaultProperty=lsh")),
  "DM single membership property → DefaultProperty",
);
assert(
  propertyContextLines(
    false,
    member,
    member,
    new PropMembership(new Map([[member, ["hsh", "rmb"]]])),
  ).some((l) => l.startsWith("CandidateProperties=hsh,rmb")),
  "DM multi membership → CandidateProperties",
);
assert(
  propertyContextLines(
    false,
    member,
    member,
    new PropMembership(new Map()),
  ).length === 0,
  "DM with no tagged groups → no property context",
);

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
