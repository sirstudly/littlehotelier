import { readFileSync, statSync } from "node:fs";
import { config, normalizeJid } from "./env.js";

export interface Requester {
  alias: string;
  email: string;
}

interface DirectoryUser extends Requester {
  whatsapp: string[];
}

type RawAliasValue = string | { email?: unknown; whatsapp?: unknown };

/** Keep in sync with `normalizeWhatsAppId` in ronbot-mcp/src/config/emailAliases.ts. */
export function normalizeWhatsAppId(input: string): string {
  const trimmed = input.trim();
  if (trimmed.includes("@")) return normalizeJid(trimmed);
  const digits = trimmed.replace(/[\s\-+()]/g, "");
  return digits ? `${digits}@c.us` : "";
}

/** Parse email-aliases.json (values: address string or `{ email, whatsapp[] }`). */
export function parseUserDirectory(raw: unknown): DirectoryUser[] {
  if (!raw || typeof raw !== "object") return [];
  const users: DirectoryUser[] = [];
  for (const [key, value] of Object.entries(raw as Record<string, RawAliasValue>)) {
    const alias = key.trim().toLowerCase();
    if (typeof value === "string") {
      users.push({ alias, email: value.trim(), whatsapp: [] });
      continue;
    }
    const email = typeof value?.email === "string" ? value.email.trim() : "";
    if (!email) continue;
    const whatsapp = Array.isArray(value.whatsapp)
      ? value.whatsapp
          .filter((v): v is string => typeof v === "string")
          .map(normalizeWhatsAppId)
          .filter((v) => v !== "")
      : [];
    users.push({ alias, email, whatsapp });
  }
  return users;
}

let cachedUsers: DirectoryUser[] | null = null;
/** File mtime of the cached copy; null when set via `setUserDirectory`. */
let cachedMtimeMs: number | null = null;
let pinned = false;

function loadUsers(): DirectoryUser[] {
  if (pinned && cachedUsers) return cachedUsers;
  try {
    const mtimeMs = statSync(config.emailAliasesPath).mtimeMs;
    if (cachedUsers && cachedMtimeMs === mtimeMs) return cachedUsers;
    cachedUsers = parseUserDirectory(
      JSON.parse(readFileSync(config.emailAliasesPath, "utf8")) as unknown,
    );
    cachedMtimeMs = mtimeMs;
  } catch (err) {
    if (!cachedUsers) {
      console.warn(`email aliases unavailable at ${config.emailAliasesPath}`, err);
      cachedUsers = [];
    }
  }
  return cachedUsers;
}

/** Test hook: replace the loaded directory (disables file reloads). */
export function setUserDirectory(raw: unknown): void {
  cachedUsers = parseUserDirectory(raw);
  cachedMtimeMs = null;
  pinned = true;
}

/**
 * Match the sender (and any linked phone/LID identities) against configured WhatsApp ids.
 * Aliases without `whatsapp` entries never match.
 */
export function resolveRequester(
  senderId: string,
  linkedIds: string[] = [],
): Requester | undefined {
  const ids = new Set([senderId, ...linkedIds].map(normalizeJid));
  for (const user of loadUsers()) {
    if (user.whatsapp.some((id) => ids.has(id))) {
      return { alias: user.alias, email: user.email };
    }
  }
  return undefined;
}
