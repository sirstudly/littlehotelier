import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

export type PropertyId = "crh" | "hsh" | "rmb" | "lsh";

export interface PropertyConfig {
  displayName: string;
  logDir: string;
  secretSuffix: string;
}

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

export function loadProperties(): Record<PropertyId, PropertyConfig> {
  const raw = JSON.parse(readFileSync(resolveConfigPath("properties.json"), "utf8"));
  return raw as Record<PropertyId, PropertyConfig>;
}

export function assertProperty(property: string): PropertyId {
  const key = property.trim().toLowerCase();
  if (key !== "crh" && key !== "hsh" && key !== "rmb" && key !== "lsh") {
    throw new Error(`Unknown property '${property}'. Use crh|hsh|rmb|lsh.`);
  }
  return key;
}

export interface JobAllowlistEntry {
  classname: string;
  requiredParams: string[];
  description: string;
}

export function loadJobAllowlist(): Record<string, JobAllowlistEntry> {
  return JSON.parse(readFileSync(resolveConfigPath("job-allowlist.json"), "utf8"));
}
