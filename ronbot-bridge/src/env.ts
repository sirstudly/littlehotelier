import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { parse as parseEnv } from "dotenv";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
export const bridgeRoot = path.resolve(__dirname, "..");
export const repoRootDefault = path.resolve(bridgeRoot, "..");

const shellEnvKeys = new Set(Object.keys(process.env));

function applyEnvFile(filePath: string, overridePriorFiles: boolean): void {
  if (!existsSync(filePath)) return;
  const parsed = parseEnv(readFileSync(filePath));
  for (const [key, value] of Object.entries(parsed)) {
    if (shellEnvKeys.has(key)) continue;
    if (overridePriorFiles || process.env[key] === undefined) {
      process.env[key] = value;
    }
  }
}

applyEnvFile(path.join(repoRootDefault, ".env"), false);
applyEnvFile(path.join(bridgeRoot, ".env"), true);

function env(name: string, fallback = ""): string {
  return process.env[name]?.trim() || fallback;
}

function envInt(name: string, fallback: number): number {
  const raw = process.env[name]?.trim();
  if (!raw) return fallback;
  const n = Number(raw);
  return Number.isFinite(n) ? n : fallback;
}

export const PROPERTY_IDS = ["crh", "hsh", "rmb", "lsh"] as const;
export type PropertyId = (typeof PROPERTY_IDS)[number];

export type GroupEntry = { id: string; property?: PropertyId };

const PROPERTY_SET = new Set<string>(PROPERTY_IDS);

export function normalizePropertyId(raw: string): PropertyId | undefined {
  const code = raw.trim().toLowerCase();
  return PROPERTY_SET.has(code) ? (code as PropertyId) : undefined;
}

/** Parse one allowlist token: `jid` or `jid:property` (property optional). */
export function parseGroupEntry(token: string): GroupEntry | null {
  const trimmed = token.trim();
  if (!trimmed) return null;

  const colon = trimmed.lastIndexOf(":");
  if (colon > 0) {
    const idPart = trimmed.slice(0, colon).trim();
    const maybeProp = trimmed.slice(colon + 1).trim();
    if (idPart && maybeProp) {
      const property = normalizePropertyId(maybeProp);
      if (property) {
        return { id: normalizeJid(idPart), property };
      }
      console.warn(
        `invalid property "${maybeProp}" for group ${idPart}; allowlisting without property`,
      );
      return { id: normalizeJid(idPart) };
    }
  }

  return { id: normalizeJid(trimmed) };
}

/** Comma-separated `jid` / `jid:property` entries from env. */
export function parseGroupEntriesFromEnv(raw: string): GroupEntry[] {
  return raw
    .split(",")
    .map((s) => parseGroupEntry(s))
    .filter((e): e is GroupEntry => e !== null);
}

type GroupsJsonRaw = {
  groups?: Array<string | { id?: unknown; property?: unknown }>;
};

/** Parse groups.json shape (legacy string[] or objects with optional property). */
export function parseGroupsJson(raw: unknown): GroupEntry[] {
  const groups = (raw as GroupsJsonRaw)?.groups;
  if (!Array.isArray(groups)) return [];

  const out: GroupEntry[] = [];
  for (const item of groups) {
    if (typeof item === "string") {
      const entry = parseGroupEntry(item);
      if (entry) out.push(entry);
      continue;
    }
    if (item && typeof item === "object" && item.id != null) {
      const id = normalizeJid(String(item.id));
      if (!id) continue;
      const property =
        item.property != null ? normalizePropertyId(String(item.property)) : undefined;
      if (item.property != null && !property) {
        console.warn(
          `invalid property "${String(item.property)}" for group ${id}; allowlisting without property`,
        );
      }
      out.push(property ? { id, property } : { id });
    }
  }
  return out;
}

function loadGroupAllowlist(): GroupEntry[] {
  const fromEnv = parseGroupEntriesFromEnv(env("RONBOT_WHATSAPP_GROUPS"));
  if (fromEnv.length > 0) return fromEnv;

  const configPath =
    env("RONBOT_GROUPS_CONFIG") ||
    path.join(bridgeRoot, "config", "groups.json");
  try {
    const raw = JSON.parse(readFileSync(configPath, "utf8")) as unknown;
    return parseGroupsJson(raw);
  } catch {
    return [];
  }
}

export const config = {
  port: envInt("PORT", 8787),
  wahaBaseUrl: env("WAHA_BASE_URL", "http://127.0.0.1:3000").replace(/\/$/, ""),
  wahaApiKey: env("WAHA_API_KEY"),
  wahaSession: env("WAHA_SESSION", "default"),
  mention: env("RONBOT_MENTION", "@ronbot"),
  followUpMinutes: envInt("RONBOT_FOLLOWUP_MINUTES", 15),
  transcriptLimit: envInt("RONBOT_TRANSCRIPT_LIMIT", 40),
  membershipRefreshMs: envInt("RONBOT_MEMBERSHIP_REFRESH_MS", 5 * 60 * 1000),
  groups: loadGroupAllowlist(),
  repoRoot: env("REPO_ROOT", repoRootDefault),
  mcpEntry:
    env("RONBOT_MCP_ENTRY") ||
    path.join(repoRootDefault, "ronbot-mcp", "dist", "index.js"),
  /** Shared with MCP; defaults to `<mcp package>/config/email-aliases.json` next to `mcpEntry`. */
  emailAliasesPath:
    env("RONBOT_EMAIL_ALIASES_PATH") ||
    path.join(
      path.dirname(
        path.dirname(
          env("RONBOT_MCP_ENTRY") || path.join(repoRootDefault, "ronbot-mcp", "dist", "index.js"),
        ),
      ),
      "config",
      "email-aliases.json",
    ),
  cursorApiKey: env("CURSOR_API_KEY") || undefined,
  modelId: env("RONBOT_MODEL", "composer-2.5"),
  googleCredentials: env("GOOGLE_APPLICATION_CREDENTIALS"),
  gcpProjectId: env("GCP_PROJECT_ID", "macbackpackers-backoffice"),
  ronbotReadApiUrl: env("RONBOT_READ_API_URL", "http://127.0.0.1:8080"),
  ronbotToken: env("RONBOT_TOKEN"),
  logDirCrh: env("LOG_DIR_CRH", path.join(repoRootDefault, "logs", "crh")),
  logDirHsh: env("LOG_DIR_HSH", path.join(repoRootDefault, "logs", "hsh")),
  logDirRmb: env("LOG_DIR_RMB", path.join(repoRootDefault, "logs", "rmb")),
  logDirLsh: env("LOG_DIR_LSH", path.join(repoRootDefault, "logs", "lsh")),
  /** Read-only MySQL user for MCP run_sql / describe_sql_tables; tools are hidden when unset. */
  sqlRoHost: env("RONBOT_SQL_RO_HOST"),
  sqlRoPort: env("RONBOT_SQL_RO_PORT"),
  sqlRoUser: env("RONBOT_SQL_RO_USER"),
  sqlRoPassword: process.env.RONBOT_SQL_RO_PASSWORD ?? "",
};

export function isAllowlistedGroup(chatId: string): boolean {
  const jid = normalizeJid(chatId);
  return config.groups.some((g) => g.id === jid);
}

export function getGroupProperty(chatId: string): PropertyId | undefined {
  const jid = normalizeJid(chatId);
  return config.groups.find((g) => g.id === jid)?.property;
}

export function normalizeJid(id: string): string {
  let jid = id.trim();
  if (jid.endsWith("@s.whatsapp.net")) {
    jid = jid.replace(/@s\.whatsapp\.net$/, "@c.us");
  }
  return jid;
}
