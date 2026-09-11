import { normalizeJid } from "./env.js";

/** Phone / LID identities for the linked WAHA session (ronbot). */
const botIds = new Set<string>();
const botUserParts = new Set<string>();

function userPart(id: string): string {
  return id.split("@")[0]?.split(":")[0] ?? "";
}

export function rememberBotIds(ids: Array<string | undefined | null>): void {
  for (const raw of ids) {
    if (!raw || typeof raw !== "string") continue;
    const trimmed = raw.trim();
    if (!trimmed) continue;
    const jid = normalizeJid(trimmed);
    botIds.add(jid);
    const user = userPart(jid);
    if (user) botUserParts.add(user);
  }
}

export function getBotIds(): ReadonlySet<string> {
  return botIds;
}

export function getBotUserParts(): ReadonlySet<string> {
  return botUserParts;
}

/** True if `id` is the bot (phone, LID, or bare numeric user part). */
export function isBotId(id: string): boolean {
  const jid = normalizeJid(id.trim());
  if (botIds.has(jid)) return true;
  const user = userPart(jid);
  return Boolean(user && botUserParts.has(user));
}
