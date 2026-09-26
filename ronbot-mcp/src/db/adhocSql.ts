import type { FieldPacket, RowDataPacket } from "mysql2";
import mysql from "mysql2/promise";
import pkg from "node-sql-parser";
import { getReadOnlyPool, killReadOnlyQuery } from "./readOnlyPool.js";

const { Parser } = pkg;
const parser = new Parser();
const PARSE_OPT = { database: "MySQL" };

/** Hints for the agent only; queries are never restricted to these. */
export const KEY_TABLES = [
  "v_wp_lh_booking_reservation",
  "v_wp_lh_booking_current",
  "v_wp_lh_booking_removed",
  "wp_lh_booking_assignment",
  "wp_lh_rooms",
  "wp_lh_jobs",
  "wp_lh_job_param",
  "job_scheduler",
  "job_scheduler_param",
  "wp_booking_lookup_key",
  "wp_invoice",
  "wp_invoice_notes",
  "wp_lh_bedcounts",
  "wp_lh_group_bookings",
  "wp_lh_housekeeping_bed",
  "wp_lh_occupancy",
  "wp_lh_rpt_guest_comments",
  "wp_lh_rpt_mostly_full_dorms",
  "wp_lh_rpt_split_rooms",
  "wp_lh_rpt_unpaid_deposit",
  "wp_stripe_transaction",
  "wp_stripe_tx_refund",
  "wp_tx_refund",
  "wp_hwl_cancel_booking_exempt",
  "wp_options",
] as const;

/** Still written, but superseded; use the replacement noted in TABLE_NOTES. */
export const LEGACY_TABLES = ["wp_lh_calendar"] as const;

const BOOKING_ASSIGNMENT_NOTES =
  "SCD2 snapshot of Cloudbeds bed assignments; the default source for cached booking/stay data. " +
  "Current rows: valid_to IS NULL (includes departed stays back to 2024-01-01; departed means checkout_date < CURDATE(), not a bed_status — some past stays were never moved off 'confirmed'). " +
  "As of time T: valid_from <= T AND (valid_to IS NULL OR valid_to > T). " +
  "One row per bed: payment_total, payment_outstanding, visitor_levy_total and num_guests are reservation-level values repeated on every bed row — never SUM them over bed rows; use v_wp_lh_booking_reservation or MAX() per reservation_id. " +
  "source='guest' is a stay, source='closure' a room block (out_of_service / blocked_dates). " +
  "Cancellations are closed rows (see v_wp_lh_booking_removed), not current rows. " +
  "guest_name, email, notes and comments are NULL on stays before ~2026-08 (privacy), so never filter on guest_name (e.g. LIKE '%tour%') for historical questions: call search_reservations (query + date range) to get the reservation ids from Cloudbeds, then filter here with reservation_id IN (...). " +
  "Join wp_lh_rooms ON wp_lh_rooms.id = room_id for room_type / capacity. " +
  "crh history is still being backfilled; hsh/rmb/lsh history is complete.";

/** Per-table guidance returned by describe_sql_tables. */
export const TABLE_NOTES: Record<string, string> = {
  wp_lh_booking_assignment: BOOKING_ASSIGNMENT_NOTES,
  v_wp_lh_booking_reservation:
    "One row per current reservation (reservation_id) from wp_lh_booking_assignment: stay dates, nights, departed_yn, num_beds, rooms, bed_statuses, and reservation-level money (payment_total, payment_outstanding, visitor_levy_total) safe to SUM. Materialised per query; for big ranges filter wp_lh_booking_assignment directly.",
  v_wp_lh_booking_current:
    "One row per bed for current guest stays (valid_to IS NULL, source='guest') with room_type, capacity, nights and departed_yn. Money columns repeat per bed — do not SUM them here.",
  v_wp_lh_booking_removed:
    "Latest closed version of each guest assignment that no longer has a current row: cancellations, deletions and beds removed from a reservation (reservation_current_yn='Y' means the reservation still has other beds). removed_at is when it was closed. Backfilled cancellations show bed_status canceled/no_show; live cancellations keep their previous status.",
  wp_lh_calendar:
    "Legacy: per-job allocation snapshots, recent jobs only (no history). Still dual-written by AllocationScraperJob but superseded — use wp_lh_booking_assignment / v_wp_lh_booking_* instead.",
};

/** One-line pointer for tool descriptions. */
export const BOOKING_SQL_HINT =
  "For booking/stay questions query v_wp_lh_booking_reservation (per reservation, money-safe) or v_wp_lh_booking_current (per bed) first, then wp_lh_booking_assignment for history / point-in-time (describe_sql_tables with table=wp_lh_booking_assignment explains it); wp_lh_calendar is legacy. Guest names are NULL on older stays: to filter by name (e.g. LSH \"tour\" group bookings), get reservation ids from search_reservations first, then use reservation_id IN (...).";

/** No longer written to; do not use for current data. */
export const DEFUNCT_TABLES = [
  "wp_hw_booking",
  "wp_hw_booking_dates",
  "wp_pxpost_transaction",
  "wp_sagepay_transaction",
  "wp_sagepay_tx_auth",
  "wp_sagepay_tx_refund",
] as const;

export const DEFAULT_MAX_ROWS = 200;
export const HARD_MAX_ROWS = 1000;
export const DEFAULT_TIMEOUT_MS = 15_000;
export const HARD_TIMEOUT_MS = 30_000;

// The read-only user blocks writes, but not reads that stall or hold locks.
const BANNED_FUNCTIONS = new Set([
  "SLEEP",
  "BENCHMARK",
  "GET_LOCK",
  "RELEASE_LOCK",
  "RELEASE_ALL_LOCKS",
  "IS_FREE_LOCK",
  "IS_USED_LOCK",
  "LOAD_FILE",
]);
const BANNED_FUNCTION_RE = new RegExp(`\\b(${[...BANNED_FUNCTIONS].join("|")})\\s*\\(`, "i");
const INTO_FILE_RE = /\binto\s+(outfile|dumpfile)\b/i;

export interface ValidatedSql {
  /** SQL that will be sent to MySQL (may differ from input by an added/clamped LIMIT). */
  sql: string;
  /** Rows fetched at most; one more than maxRows so truncation can be detected. */
  fetchLimit: number;
}

type AstNode = Record<string, unknown>;

function functionName(node: AstNode): string {
  const name = node.name as unknown;
  if (typeof name === "string") return name.toUpperCase();
  if (name && typeof name === "object") {
    const parts = (name as { name?: Array<{ value?: unknown }> }).name;
    if (Array.isArray(parts)) {
      return parts.map((p) => String(p?.value ?? "")).join(".").toUpperCase();
    }
  }
  return "";
}

function walk(value: unknown, visit: (node: AstNode) => void, seen = new Set<unknown>()): void {
  if (value === null || typeof value !== "object" || seen.has(value)) return;
  seen.add(value);
  if (Array.isArray(value)) {
    for (const item of value) walk(item, visit, seen);
    return;
  }
  const node = value as AstNode;
  visit(node);
  for (const child of Object.values(node)) walk(child, visit, seen);
}

function assertSafeNode(node: AstNode): void {
  if (node.type === "function") {
    const name = functionName(node);
    const bare = name.split(".").pop() ?? name;
    if (BANNED_FUNCTIONS.has(bare)) {
      throw new Error(`Function ${bare}() is not allowed in ad-hoc SQL`);
    }
  }
  const into = node.into as { position?: unknown } | undefined;
  if (into && typeof into === "object" && into.position) {
    throw new Error("SELECT ... INTO is not allowed in ad-hoc SQL");
  }
  if (typeof node.locking === "string" && node.locking) {
    throw new Error(`${node.locking} is not allowed in ad-hoc SQL`);
  }
  if (typeof node.locking_read === "string" && node.locking_read) {
    throw new Error(`${node.locking_read} is not allowed in ad-hoc SQL`);
  }
}

interface LimitNode {
  seperator: string;
  value: Array<{ type: string; value: unknown }>;
}

/** Returns the row-count part of a LIMIT clause, or null when there is none. */
function limitCount(limit: LimitNode | null | undefined): { count: number; index: number } | null {
  if (!limit || !Array.isArray(limit.value) || limit.value.length === 0) return null;
  // `LIMIT offset, count` puts count second; `LIMIT count` / `LIMIT count OFFSET n` put it first.
  const index = limit.seperator === "," ? 1 : 0;
  const entry = limit.value[index];
  if (!entry || entry.type !== "number" || typeof entry.value !== "number") {
    throw new Error("LIMIT must be a literal number");
  }
  return { count: entry.value, index };
}

function stripTrailing(sql: string): string {
  return sql.trim().replace(/;+\s*$/, "").trim();
}

export function clampMaxRows(maxRows?: number): number {
  if (!maxRows || !Number.isFinite(maxRows)) return DEFAULT_MAX_ROWS;
  return Math.max(1, Math.min(Math.floor(maxRows), HARD_MAX_ROWS));
}

/**
 * Accepts exactly one SELECT (UNIONs and subqueries allowed) against any table.
 * Rejects locking reads, INTO, and stall/lock functions; caps the row count.
 */
export function validateSql(rawSql: string, maxRows = DEFAULT_MAX_ROWS): ValidatedSql {
  const cap = clampMaxRows(maxRows);
  const fetchLimit = cap + 1;
  const sql = stripTrailing(rawSql);
  if (sql === "") throw new Error("SQL is empty");

  if (BANNED_FUNCTION_RE.test(sql)) {
    throw new Error(`Query uses a function that is not allowed (${[...BANNED_FUNCTIONS].join(", ")})`);
  }
  if (INTO_FILE_RE.test(sql)) {
    throw new Error("SELECT ... INTO OUTFILE/DUMPFILE is not allowed in ad-hoc SQL");
  }

  let ast: unknown;
  try {
    ast = parser.astify(sql, PARSE_OPT);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    throw new Error(`Could not parse SQL (MySQL dialect): ${message}`);
  }

  const statements = Array.isArray(ast) ? ast : [ast];
  if (statements.length !== 1) {
    throw new Error(`Exactly one statement is allowed (got ${statements.length})`);
  }
  const stmt = statements[0] as AstNode;
  if (stmt.type !== "select") {
    throw new Error(`Only SELECT statements are allowed (got ${String(stmt.type).toUpperCase()})`);
  }
  walk(stmt, assertSafeNode);

  if (stmt._next) {
    return {
      sql: `SELECT * FROM (\n${sql}\n) AS adhoc_union LIMIT ${fetchLimit}`,
      fetchLimit,
    };
  }

  const limit = limitCount(stmt.limit as LimitNode | null);
  if (!limit) {
    return { sql: `${sql}\nLIMIT ${fetchLimit}`, fetchLimit };
  }
  if (limit.count <= fetchLimit) {
    return { sql, fetchLimit };
  }
  (stmt.limit as LimitNode).value[limit.index] = { type: "number", value: fetchLimit };
  return { sql: parser.sqlify(stmt as never, PARSE_OPT), fetchLimit };
}

function uniqueColumnNames(fields: FieldPacket[]): string[] {
  const counts = new Map<string, number>();
  for (const f of fields) counts.set(f.name, (counts.get(f.name) ?? 0) + 1);
  const used = new Set<string>();
  return fields.map((f) => {
    let name = (counts.get(f.name) ?? 0) > 1 && f.table ? `${f.table}.${f.name}` : f.name;
    const base = name;
    for (let i = 2; used.has(name); i++) name = `${base}_${i}`;
    used.add(name);
    return name;
  });
}

function toJsonValue(value: unknown): unknown {
  if (value instanceof Date) return value.toISOString();
  if (Buffer.isBuffer(value)) return value.toString("utf8");
  if (typeof value === "bigint") return value.toString();
  return value;
}

export interface AdhocResult {
  sql: string;
  rowCount: number;
  truncated: boolean;
  columns: string[];
  rows: Array<Record<string, unknown>>;
}

export async function runAdhocSelect(
  property: string,
  rawSql: string,
  options: { maxRows?: number; timeoutMs?: number } = {},
): Promise<AdhocResult> {
  const maxRows = clampMaxRows(options.maxRows);
  const timeoutMs = Math.max(1000, Math.min(options.timeoutMs ?? DEFAULT_TIMEOUT_MS, HARD_TIMEOUT_MS));
  const { sql } = validateSql(rawSql, maxRows);

  const pool = await getReadOnlyPool(property);
  const conn = await pool.getConnection();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    void killReadOnlyQuery(property, conn.threadId).catch((err) => {
      console.error("[ronbot-mcp] KILL QUERY failed", err);
    });
  }, timeoutMs);

  try {
    const [rows, fields] = await conn.query<RowDataPacket[][]>({ sql, rowsAsArray: true });
    const columns = uniqueColumnNames(fields ?? []);
    const truncated = rows.length > maxRows;
    const kept = truncated ? rows.slice(0, maxRows) : rows;
    return {
      sql,
      rowCount: kept.length,
      truncated,
      columns,
      rows: kept.map((row) => {
        const obj: Record<string, unknown> = {};
        columns.forEach((col, i) => {
          obj[col] = toJsonValue((row as unknown[])[i]);
        });
        return obj;
      }),
    };
  } catch (err) {
    if (timedOut) {
      throw new Error(`Query cancelled after ${timeoutMs} ms; narrow the WHERE clause (filter on indexed columns)`);
    }
    throw err;
  } finally {
    clearTimeout(timer);
    conn.release();
  }
}

export type TableHint = "key" | "legacy" | "defunct" | "other";

function tableHint(name: string): TableHint {
  if ((KEY_TABLES as readonly string[]).includes(name)) return "key";
  if ((LEGACY_TABLES as readonly string[]).includes(name)) return "legacy";
  if ((DEFUNCT_TABLES as readonly string[]).includes(name)) return "defunct";
  return "other";
}

async function listTables(
  property: string,
): Promise<Array<{ name: string; type: string; hint: TableHint; notes?: string }>> {
  const pool = await getReadOnlyPool(property);
  const [rows] = await pool.query<RowDataPacket[][]>({ sql: "SHOW FULL TABLES", rowsAsArray: true });
  return rows.map((r) => {
    const name = String((r as unknown[])[0]);
    const notes = TABLE_NOTES[name];
    return {
      name,
      type: String((r as unknown[])[1] ?? "BASE TABLE"),
      hint: tableHint(name),
      ...(notes ? { notes } : {}),
    };
  });
}

export async function describeTables(property: string, table?: string): Promise<unknown> {
  const tables = await listTables(property);
  if (!table) {
    const keyOrder = (name: string) => (KEY_TABLES as readonly string[]).indexOf(name);
    const order: Record<TableHint, number> = { key: 0, legacy: 1, other: 2, defunct: 3 };
    return {
      tables: [...tables].sort(
        (a, b) =>
          order[a.hint] - order[b.hint] ||
          (a.hint === "key" ? keyOrder(a.name) - keyOrder(b.name) : 0) ||
          a.name.localeCompare(b.name),
      ),
    };
  }

  const name = table.trim();
  if (!/^[A-Za-z0-9_]+$/.test(name)) {
    throw new Error("table must contain only letters, digits and underscores");
  }
  const match = tables.find((t) => t.name.toLowerCase() === name.toLowerCase());
  if (!match) {
    throw new Error(`Table '${name}' not found. Call describe_sql_tables without table to list tables.`);
  }
  const pool = await getReadOnlyPool(property);
  const [cols] = await pool.query<RowDataPacket[]>(`SHOW COLUMNS FROM ${mysql.escapeId(match.name)}`);
  return {
    table: match.name,
    type: match.type,
    hint: match.hint,
    ...(match.notes ? { notes: match.notes } : {}),
    columns: cols.map((c) => ({
      name: c.Field,
      type: c.Type,
      nullable: c.Null === "YES",
      key: c.Key || null,
      default: c.Default ?? null,
      extra: c.Extra || null,
    })),
  };
}
