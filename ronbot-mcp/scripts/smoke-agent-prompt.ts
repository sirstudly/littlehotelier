/**
 * Phase 2 smoke: local Agent.prompt against the repo + inline ronbot-ops MCP.
 *
 * Exit codes:
 *   0 — run finished successfully
 *   1 — CursorAgentError (did not start: auth/config/network)
 *   2 — run executed but result.status === "error"
 */
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { parse as parseEnv } from "dotenv";
import { Agent, Cursor, CursorAgentError } from "@cursor/sdk";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ronbotMcpRoot = path.resolve(__dirname, "..");
const repoRoot = path.resolve(ronbotMcpRoot, "..");
const mcpEntry = path.join(ronbotMcpRoot, "dist", "index.js");

/** Keys present before loading files — shell / parent process always wins. */
const shellEnvKeys = new Set(Object.keys(process.env));

function applyEnvFile(filePath: string, overridePriorFiles: boolean): void {
  if (!existsSync(filePath)) {
    return;
  }
  const parsed = parseEnv(readFileSync(filePath));
  for (const [key, value] of Object.entries(parsed)) {
    if (shellEnvKeys.has(key)) {
      continue;
    }
    if (overridePriorFiles || process.env[key] === undefined) {
      process.env[key] = value;
    }
  }
}

// Repo root first, then ronbot-mcp/.env (overrides root). Shell env wins over both.
applyEnvFile(path.join(repoRoot, ".env"), false);
applyEnvFile(path.join(ronbotMcpRoot, ".env"), true);

function requireEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    console.error(`Missing required env: ${name}`);
    process.exit(1);
  }
  return value;
}

function logDir(envName: string, property: string): string {
  return process.env[envName]?.trim() || path.join(repoRoot, "logs", property);
}

async function resolveApiKey(): Promise<string | undefined> {
  const fromEnv = process.env.CURSOR_API_KEY?.trim();
  if (fromEnv) {
    return fromEnv;
  }
  const status = await Cursor.auth.status();
  if (status.status === "logged-in") {
    // FileCredentialStore (~/.cursor/sdk/auth.json) — omit explicit key
    console.log("Using stored Cursor SDK login (CURSOR_API_KEY unset).");
    return undefined;
  }
  console.error(
    "Missing CURSOR_API_KEY and SDK is logged out.\n" +
      "  Put CURSOR_API_KEY in ronbot-mcp/.env or the repo-root .env\n" +
      "  (https://cursor.com/dashboard/integrations)\n" +
      "  or: node --input-type=module -e 'import { Cursor } from \"@cursor/sdk\"; await Cursor.auth.login()'",
  );
  process.exit(1);
}

async function readApiHealthy(baseUrl: string): Promise<boolean> {
  try {
    const res = await fetch(`${baseUrl.replace(/\/$/, "")}/ronbot/health`, {
      signal: AbortSignal.timeout(5000),
    });
    return res.ok;
  } catch {
    return false;
  }
}

function buildMcpEnv(): Record<string, string> {
  return {
    GOOGLE_APPLICATION_CREDENTIALS: requireEnv("GOOGLE_APPLICATION_CREDENTIALS"),
    GCP_PROJECT_ID: process.env.GCP_PROJECT_ID?.trim() || "macbackpackers-backoffice",
    RONBOT_READ_API_URL: process.env.RONBOT_READ_API_URL?.trim() || "http://127.0.0.1:8080",
    RONBOT_TOKEN: process.env.RONBOT_TOKEN?.trim() || "",
    LOG_DIR_CRH: logDir("LOG_DIR_CRH", "crh"),
    LOG_DIR_HSH: logDir("LOG_DIR_HSH", "hsh"),
    LOG_DIR_RMB: logDir("LOG_DIR_RMB", "rmb"),
    LOG_DIR_LSH: logDir("LOG_DIR_LSH", "lsh"),
  };
}

function agentOptions(mcpEnv: Record<string, string>, apiKey?: string) {
  return {
    ...(apiKey ? { apiKey } : {}),
    model: { id: "composer-2.5" as const },
    local: {
      cwd: repoRoot,
      settingSources: [] as [],
    },
    mcpServers: {
      "ronbot-ops": {
        type: "stdio" as const,
        command: "node",
        args: [mcpEntry],
        env: mcpEnv,
      },
    },
  };
}

const MAIN_PROMPT = `You are verifying ronbot phase-2 wiring in this repository.

1) Codebase (read the repo; do not guess):
   In this repo, which class/method does CloudbedsScraper use to load a reservation by id?
   Cite the Java file path and method name.

2) Ops (must call the tool; do not guess numbers):
   Using the ronbot-ops MCP tool get_job_queue_stats for property "hsh",
   report submitted / processing / retry counts from the tool result.

Answer both parts clearly.`;

async function runMainSmoke(mcpEnv: Record<string, string>, apiKey?: string): Promise<void> {
  console.log("Running main Agent.prompt (codebase + get_job_queue_stats)...");
  const result = await Agent.prompt(MAIN_PROMPT, agentOptions(mcpEnv, apiKey));
  console.log("status:", result.status);
  if ("id" in result && result.id) {
    console.log("runId:", result.id);
  }
  console.log("result:\n", result.result ?? "(no result text)");
  if (result.status === "error") {
    process.exit(2);
  }
}

async function runOptionalBookingSmoke(
  mcpEnv: Record<string, string>,
  apiKey?: string,
): Promise<void> {
  const reservation = process.env.RONBOT_SMOKE_RESERVATION?.trim();
  if (!reservation) {
    console.log("Skipping live get_booking smoke (RONBOT_SMOKE_RESERVATION unset).");
    return;
  }

  const readApiUrl = mcpEnv.RONBOT_READ_API_URL;
  const healthy = await readApiHealthy(readApiUrl);
  if (!healthy) {
    console.error(
      `RONBOT_SMOKE_RESERVATION is set but ${readApiUrl}/ronbot/health is not OK — skipping live booking prompt.`,
    );
    return;
  }

  const property = process.env.RONBOT_SMOKE_PROPERTY?.trim() || "crh";
  const bookingPrompt = `Using the ronbot-ops MCP tool get_booking, fetch reservation "${reservation}" for property "${property}". Summarize guest name, status, and balance from the tool result. Do not invent data.`;

  console.log(`Running optional get_booking Agent.prompt (${property} / ${reservation})...`);
  const result = await Agent.prompt(bookingPrompt, agentOptions(mcpEnv, apiKey));
  console.log("status:", result.status);
  if ("id" in result && result.id) {
    console.log("runId:", result.id);
  }
  console.log("result:\n", result.result ?? "(no result text)");
  if (result.status === "error") {
    process.exit(2);
  }
}

async function main(): Promise<void> {
  if (!existsSync(mcpEntry)) {
    console.error(`MCP entry missing: ${mcpEntry}\nRun: npm run build`);
    process.exit(1);
  }

  const apiKey = await resolveApiKey();
  const mcpEnv = buildMcpEnv();

  try {
    await runMainSmoke(mcpEnv, apiKey);
    await runOptionalBookingSmoke(mcpEnv, apiKey);
  } catch (err) {
    if (err instanceof CursorAgentError) {
      console.error(
        `startup failed: ${err.message}, retryable=${String(err.isRetryable)}`,
      );
      process.exit(1);
    }
    throw err;
  }
}

await main();
