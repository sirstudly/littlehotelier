import {
  Agent,
  Cursor,
  CursorAgentError,
  type SDKAgent,
  type SDKImage,
} from "@cursor/sdk";
import { config } from "../env.js";
import { SYSTEM_PROMPT } from "./systemPrompt.js";

interface ChatAgent {
  agent: SDKAgent;
  busy: boolean;
  /** True after the first successful send that included SYSTEM_PROMPT. */
  primed: boolean;
}

const agents = new Map<string, ChatAgent>();

function mcpEnv(): Record<string, string> {
  const env: Record<string, string> = {
    GCP_PROJECT_ID: config.gcpProjectId,
    RONBOT_READ_API_URL: config.ronbotReadApiUrl,
    RONBOT_TOKEN: config.ronbotToken,
    LOG_DIR_CRH: config.logDirCrh,
    LOG_DIR_HSH: config.logDirHsh,
    LOG_DIR_RMB: config.logDirRmb,
    LOG_DIR_LSH: config.logDirLsh,
    // Ensure MCP child inherits enough PATH for `node`
    PATH: process.env.PATH ?? "/usr/local/bin:/usr/bin:/bin",
    HOME: process.env.HOME ?? "/home/node",
  };
  if (config.googleCredentials) {
    env.GOOGLE_APPLICATION_CREDENTIALS = config.googleCredentials;
  }
  return env;
}

async function resolveApiKey(): Promise<string | undefined> {
  if (config.cursorApiKey) return config.cursorApiKey;
  const status = await Cursor.auth.status();
  if (status.status === "logged-in") return undefined;
  throw new Error(
    "Missing CURSOR_API_KEY and SDK is logged out. Set CURSOR_API_KEY or run Cursor.auth.login().",
  );
}

async function getOrCreateAgent(chatId: string): Promise<SDKAgent> {
  const existing = agents.get(chatId);
  if (existing) return existing.agent;

  const apiKey = await resolveApiKey();
  // Do not pass AgentOptions.systemPrompt — the local agent binary rejects
  // unknown option '--system-prompt' (gated / unsupported on this SDK path).
  // Instructions are prepended on the first send per chat instead.
  const agent = await Agent.create({
    ...(apiKey ? { apiKey } : {}),
    model: { id: config.modelId },
    name: `ronbot-${chatId.slice(0, 24)}`,
    local: {
      cwd: config.repoRoot,
      settingSources: [],
    },
    mcpServers: {
      "ronbot-ops": {
        type: "stdio",
        command: "node",
        args: [config.mcpEntry],
        env: mcpEnv(),
      },
    },
  });
  agents.set(chatId, { agent, busy: false, primed: false });
  return agent;
}

export async function askRonbot(
  chatId: string,
  userPrompt: string,
  images?: SDKImage[],
): Promise<string> {
  const slot = agents.get(chatId);
  if (slot?.busy) {
    return "Still working on your previous question — give me a moment, then ask again.";
  }

  const agent = await getOrCreateAgent(chatId);
  const entry = agents.get(chatId)!;
  entry.busy = true;
  try {
    const messageText = entry.primed
      ? userPrompt
      : `${SYSTEM_PROMPT}\n\n---\n\n${userPrompt}`;
    const run = await agent.send(
      images?.length ? { text: messageText, images } : messageText,
    );
    const result = await run.wait();
    if (result.status === "error") {
      const detail = result.error?.message ?? result.result ?? "(no error detail)";
      const code = result.error?.code ? ` [${result.error.code}]` : "";
      console.error(
        `agent run error chat=${chatId} runId=${result.id} durationMs=${result.durationMs ?? "?"}${code}: ${detail}`,
      );
      return `I hit an error running that request (${detail.slice(0, 240)}). Please try again in a minute.`;
    }
    entry.primed = true;
    const text = (result.result ?? "").trim();
    return text || "(no response text)";
  } catch (err) {
    if (err instanceof CursorAgentError) {
      console.error("CursorAgentError", err.message, "retryable=", err.isRetryable);
      return `Agent failed to start: ${err.message}`;
    }
    throw err;
  } finally {
    entry.busy = false;
  }
}

/** Split long answers into WhatsApp-friendly chunks (~3500 chars). */
export function chunkWhatsAppText(text: string, maxLen = 3500): string[] {
  const trimmed = text.trim();
  if (trimmed.length <= maxLen) return [trimmed];
  const chunks: string[] = [];
  let rest = trimmed;
  while (rest.length > maxLen) {
    let cut = rest.lastIndexOf("\n\n", maxLen);
    if (cut < maxLen * 0.4) cut = rest.lastIndexOf("\n", maxLen);
    if (cut < maxLen * 0.4) cut = maxLen;
    chunks.push(rest.slice(0, cut).trim());
    rest = rest.slice(cut).trim();
  }
  if (rest) chunks.push(rest);
  return chunks;
}
