/**
 * Tier 2: materialize a validated SELECT into mac_analytics_scratch.adhoc_* tables.
 */
import type { RowDataPacket } from "mysql2";
import mysql, { type Pool } from "mysql2/promise";
import {
  MATERIALIZE_DEFAULT_TIMEOUT_MS,
  MATERIALIZE_HARD_TIMEOUT_MS,
  validateMaterializeSelect,
} from "./adhocSql.js";

export const SCRATCH_SCHEMA = "mac_analytics_scratch";

/** adhoc_<slug>_<yyyyMMdd>_<HHmm> — Europe/London wall clock at materialize time. */
export const ADHOC_TABLE_RE = /^adhoc_[a-z][a-z0-9_]{0,40}_\d{8}_\d{4}$/;

const SLUG_RE = /^[a-z][a-z0-9_]{0,40}$/;

let pool: Pool | null = null;

function adhocUser(): string {
  return process.env.ANALYTICS_ADHOC_RW_USER?.trim() ?? "";
}

function adhocPassword(): string {
  return process.env.ANALYTICS_ADHOC_RW_PASSWORD ?? "";
}

function adhocHost(): string {
  return (
    process.env.ANALYTICS_ADHOC_RW_HOST?.trim() ||
    process.env.RONBOT_SQL_RO_HOST?.trim() ||
    process.env.DB_HOST?.trim() ||
    "mysql-tailscale"
  );
}

function adhocPort(): number {
  const raw =
    process.env.ANALYTICS_ADHOC_RW_PORT?.trim() ||
    process.env.RONBOT_SQL_RO_PORT?.trim() ||
    process.env.DB_PORT?.trim();
  return raw ? Number(raw) : 3306;
}

export function isScratchMaterializeConfigured(): boolean {
  return adhocUser() !== "" && adhocPassword() !== "";
}

async function getScratchPool(): Promise<Pool> {
  if (!isScratchMaterializeConfigured()) {
    throw new Error(
      "Scratch materialize is not configured (ANALYTICS_ADHOC_RW_USER / ANALYTICS_ADHOC_RW_PASSWORD)",
    );
  }
  if (pool) return pool;
  pool = mysql.createPool({
    host: adhocHost(),
    port: adhocPort(),
    user: adhocUser(),
    password: adhocPassword(),
    database: SCRATCH_SCHEMA,
    waitForConnections: true,
    connectionLimit: 2,
    timezone: "local",
    dateStrings: true,
    supportBigNumbers: true,
    multipleStatements: false,
  });
  return pool;
}

async function killScratchQuery(threadId: number): Promise<void> {
  const conn = await mysql.createConnection({
    host: adhocHost(),
    port: adhocPort(),
    user: adhocUser(),
    password: adhocPassword(),
    database: SCRATCH_SCHEMA,
  });
  try {
    await conn.query("KILL QUERY ?", [threadId]);
  } finally {
    await conn.end();
  }
}

export function assertAdhocTableName(table: string): string {
  const name = table.trim();
  if (!ADHOC_TABLE_RE.test(name)) {
    throw new Error(
      `Invalid adhoc table name "${name}". Expected adhoc_<slug>_<yyyyMMdd>_<HHmm> (slug: lowercase letter then [a-z0-9_]).`,
    );
  }
  return name;
}

export function assertSlug(slug: string): string {
  const s = slug.trim().toLowerCase();
  if (!SLUG_RE.test(s)) {
    throw new Error(
      `Invalid slug "${slug}". Use lowercase letter then up to 40 chars of [a-z0-9_].`,
    );
  }
  return s;
}

/** Europe/London wall-clock parts for table suffix. */
export function londonStamp(d = new Date()): { ymd: string; hm: string } {
  const fmt = new Intl.DateTimeFormat("en-GB", {
    timeZone: "Europe/London",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  });
  const parts = Object.fromEntries(
    fmt.formatToParts(d).filter((p) => p.type !== "literal").map((p) => [p.type, p.value]),
  );
  const ymd = `${parts.year}${parts.month}${parts.day}`;
  const hm = `${parts.hour}${parts.minute}`;
  return { ymd, hm };
}

export function buildAdhocTableName(slug: string, at = new Date()): string {
  const s = assertSlug(slug);
  const { ymd, hm } = londonStamp(at);
  return assertAdhocTableName(`adhoc_${s}_${ymd}_${hm}`);
}

function quoteIdent(name: string): string {
  return `\`${name.replace(/`/g, "``")}\``;
}

export interface MaterializeResult {
  table: string;
  qualified_name: string;
  row_count: number;
  create_ms: number;
  source_sql: string;
}

export async function materializeSql(params: {
  sql: string;
  slug?: string;
  table_name?: string;
  max_rows?: number;
  timeout_ms?: number;
}): Promise<MaterializeResult> {
  const table = params.table_name
    ? assertAdhocTableName(params.table_name)
    : buildAdhocTableName(params.slug?.trim() ? params.slug : "snapshot");

  const { sql: sourceSql, fetchLimit } = validateMaterializeSelect(params.sql, params.max_rows);
  const timeoutMs = Math.max(
    1000,
    Math.min(params.timeout_ms ?? MATERIALIZE_DEFAULT_TIMEOUT_MS, MATERIALIZE_HARD_TIMEOUT_MS),
  );

  const p = await getScratchPool();
  const conn = await p.getConnection();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    void killScratchQuery(conn.threadId).catch((err) => {
      console.error("[ronbot-mcp] scratch KILL QUERY failed", err);
    });
  }, timeoutMs);

  const started = Date.now();
  try {
    const [existing] = await conn.query<RowDataPacket[]>(
      `SELECT 1 AS ok FROM information_schema.tables
       WHERE table_schema = ? AND table_name = ? LIMIT 1`,
      [SCRATCH_SCHEMA, table],
    );
    if (existing.length > 0) {
      throw new Error(
        `Table ${SCRATCH_SCHEMA}.${table} already exists. Drop it with metabase_drop_adhoc_table or choose a new slug.`,
      );
    }

    const ddl = `CREATE TABLE ${quoteIdent(SCRATCH_SCHEMA)}.${quoteIdent(table)} AS ${sourceSql}`;
    await conn.query(ddl);

    const [countRows] = await conn.query<RowDataPacket[]>(
      `SELECT COUNT(*) AS n FROM ${quoteIdent(SCRATCH_SCHEMA)}.${quoteIdent(table)}`,
    );
    const rowCount = Number(countRows[0]?.n ?? 0);
    if (rowCount >= fetchLimit) {
      await conn.query(`DROP TABLE IF EXISTS ${quoteIdent(SCRATCH_SCHEMA)}.${quoteIdent(table)}`);
      throw new Error(
        `Materialize would exceed ${fetchLimit - 1} rows (got >= ${fetchLimit}). Tighten filters or lower the result set, then retry.`,
      );
    }

    return {
      table,
      qualified_name: `${SCRATCH_SCHEMA}.${table}`,
      row_count: rowCount,
      create_ms: Date.now() - started,
      source_sql: sourceSql,
    };
  } catch (err) {
    if (timedOut) {
      throw new Error(`Materialize timed out after ${timeoutMs}ms`);
    }
    throw err;
  } finally {
    clearTimeout(timer);
    conn.release();
  }
}

export interface AdhocTableInfo {
  table: string;
  qualified_name: string;
  create_time: string | null;
  table_rows: number | null;
}

export async function listAdhocTables(): Promise<{ tables: AdhocTableInfo[] }> {
  const p = await getScratchPool();
  const [rows] = await p.query<RowDataPacket[]>(
    `SELECT table_name AS table_name, create_time, table_rows
     FROM information_schema.tables
     WHERE table_schema = ?
       AND table_name REGEXP '^adhoc_'
     ORDER BY create_time IS NULL, create_time DESC, table_name ASC`,
    [SCRATCH_SCHEMA],
  );
  const tables: AdhocTableInfo[] = rows
    .map((r) => {
      const name = String(r.table_name ?? "");
      if (!ADHOC_TABLE_RE.test(name)) return null;
      return {
        table: name,
        qualified_name: `${SCRATCH_SCHEMA}.${name}`,
        create_time: r.create_time == null ? null : String(r.create_time),
        table_rows: r.table_rows == null ? null : Number(r.table_rows),
      };
    })
    .filter((t): t is AdhocTableInfo => t != null);
  return { tables };
}

export async function dropAdhocTable(table: string): Promise<{ dropped: string; qualified_name: string }> {
  const name = assertAdhocTableName(table);
  const p = await getScratchPool();
  await p.query(`DROP TABLE IF EXISTS ${quoteIdent(SCRATCH_SCHEMA)}.${quoteIdent(name)}`);
  return { dropped: name, qualified_name: `${SCRATCH_SCHEMA}.${name}` };
}

export async function closeScratchPool(): Promise<void> {
  if (pool) {
    await pool.end();
    pool = null;
  }
}
