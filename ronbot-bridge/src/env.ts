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

function loadGroupAllowlist(): string[] {
  const fromEnv = env("RONBOT_WHATSAPP_GROUPS")
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  if (fromEnv.length > 0) return fromEnv;

  const configPath =
    env("RONBOT_GROUPS_CONFIG") ||
    path.join(bridgeRoot, "config", "groups.json");
  try {
    const raw = JSON.parse(readFileSync(configPath, "utf8")) as { groups?: string[] };
    return (raw.groups ?? []).map((s) => String(s).trim()).filter(Boolean);
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
};

export function isAllowlistedGroup(chatId: string): boolean {
  return config.groups.includes(normalizeJid(chatId));
}

export function normalizeJid(id: string): string {
  let jid = id.trim();
  if (jid.endsWith("@s.whatsapp.net")) {
    jid = jid.replace(/@s\.whatsapp\.net$/, "@c.us");
  }
  return jid;
}
