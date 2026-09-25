import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = dirname(fileURLToPath(import.meta.url));

function resolveConfigPath(filename: string): string {
  const candidates = [
    join(__dirname, "..", "..", "config", filename),
    join(__dirname, "..", "config", filename),
    join(process.cwd(), "config", filename),
    join(process.cwd(), "ronbot-mcp", "config", filename),
  ];
  for (const path of candidates) {
    try {
      readFileSync(path);
      return path;
    } catch {
      // try next
    }
  }
  throw new Error(`Cannot find config/${filename}. Searched: ${candidates.join(", ")}`);
}

let cachedAliases: Record<string, string> | null = null;

export function loadEmailAliases(): Record<string, string> {
  if (cachedAliases) return cachedAliases;
  const raw = JSON.parse(readFileSync(resolveConfigPath("email-aliases.json"), "utf8")) as Record<
    string,
    string
  >;
  const normalized: Record<string, string> = {};
  for (const [key, value] of Object.entries(raw)) {
    normalized[key.trim().toLowerCase()] = String(value).trim();
  }
  cachedAliases = normalized;
  return cachedAliases;
}

/**
 * Resolves shorthand aliases (accounts/hannah/jay/ron) or returns a literal email address.
 * Case-insensitive for aliases. Inputs containing `@` are treated as literal addresses.
 */
export function resolveEmailRecipient(input: string): string {
  const trimmed = input.trim();
  if (!trimmed) {
    throw new Error("email is blank");
  }
  if (trimmed.includes("@")) {
    return trimmed;
  }
  const aliases = loadEmailAliases();
  const resolved = aliases[trimmed.toLowerCase()];
  if (!resolved) {
    throw new Error(
      `Unknown email alias '${trimmed}'. Use a full address or one of: ${Object.keys(aliases).sort().join(", ")}`,
    );
  }
  return resolved;
}

/** Resolves each comma-delimited entry (address or alias); blanks are dropped. */
export function resolveEmailRecipientList(input: string | string[]): string[] {
  const entries = (Array.isArray(input) ? input : input.split(","))
    .map((e) => e.trim())
    .filter((e) => e !== "");
  if (entries.length === 0) {
    throw new Error("No email recipients given");
  }
  return [...new Set(entries.map(resolveEmailRecipient))];
}

/**
 * If parameters include `email`, replace it with the resolved address.
 * If they include `to_emails` (comma-delimited), resolve each entry.
 */
export function resolveEmailParamInParameters(
  parameters: Record<string, string>,
): Record<string, string> {
  const resolved = { ...parameters };
  if (parameters.email != null) {
    resolved.email = resolveEmailRecipient(parameters.email);
  }
  if (parameters.to_emails != null) {
    resolved.to_emails = resolveEmailRecipientList(parameters.to_emails).join(",");
  }
  return resolved;
}
