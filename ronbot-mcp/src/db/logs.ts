import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { assertProperty, loadProperties } from "../config/properties.js";

export interface LogHit {
  file: string;
  lineNumber: number;
  line: string;
}

export interface SearchLogsResult {
  property: string;
  pattern: string;
  hits: LogHit[];
  filesScanned: number;
}

function listLogFiles(dir: string, preferredFile?: string): string[] {
  if (preferredFile) {
    return [join(dir, preferredFile)];
  }
  let entries: string[];
  try {
    entries = readdirSync(dir);
  } catch {
    return [];
  }
  return entries
    .filter((name) => !name.startsWith("."))
    .map((name) => join(dir, name))
    .filter((path) => {
      try {
        return statSync(path).isFile();
      } catch {
        return false;
      }
    })
    .sort((a, b) => {
      try {
        return statSync(b).mtimeMs - statSync(a).mtimeMs;
      } catch {
        return 0;
      }
    });
}

export function searchLogs(
  property: string,
  pattern: string,
  options: { maxLines?: number; file?: string } = {},
): SearchLogsResult {
  const id = assertProperty(property);
  const meta = loadProperties()[id];
  // Allow host-relative override for local dev
  const logDir = process.env[`LOG_DIR_${id.toUpperCase()}`] ?? meta.logDir;
  const maxLines = Math.max(1, Math.min(options.maxLines ?? 50, 200));
  const regex = new RegExp(pattern, "i");
  const files = listLogFiles(logDir, options.file);
  const hits: LogHit[] = [];
  let filesScanned = 0;

  for (const filePath of files) {
    if (hits.length >= maxLines) break;
    filesScanned += 1;
    let content: string;
    try {
      // Cap read size to avoid blowing memory on huge logs
      const buf = readFileSync(filePath);
      content = buf.subarray(Math.max(0, buf.length - 2_000_000)).toString("utf8");
    } catch {
      continue;
    }
    const lines = content.split(/\r?\n/);
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i]!;
      if (regex.test(line)) {
        hits.push({
          file: filePath,
          lineNumber: i + 1,
          line: line.slice(0, 2000),
        });
        if (hits.length >= maxLines) break;
      }
    }
  }

  return { property: id, pattern, hits, filesScanned };
}
