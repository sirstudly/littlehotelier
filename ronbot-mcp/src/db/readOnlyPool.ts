import mysql, { type Pool } from "mysql2/promise";
import { assertProperty, loadProperties, type PropertyId } from "../config/properties.js";
import { getDbCredentials, parseJdbcUrl } from "../config/secrets.js";

interface Target {
  host: string;
  port: number;
  database: string;
}

const pools = new Map<PropertyId, Pool>();
const targets = new Map<PropertyId, Target>();

function roUser(): string {
  return process.env.RONBOT_SQL_RO_USER?.trim() ?? "";
}

function roPassword(): string {
  return process.env.RONBOT_SQL_RO_PASSWORD ?? "";
}

/** Ad-hoc SQL tools are only exposed when the dedicated read-only DB user is configured. */
export function isReadOnlySqlConfigured(): boolean {
  return roUser() !== "" && roPassword() !== "";
}

async function resolveTarget(id: PropertyId): Promise<Target> {
  const cached = targets.get(id);
  if (cached) return cached;

  const hostOverride = process.env.RONBOT_SQL_RO_HOST?.trim();
  const portOverride = process.env.RONBOT_SQL_RO_PORT?.trim();
  const meta = loadProperties()[id];

  let parsed: Target | null = null;
  try {
    parsed = parseJdbcUrl((await getDbCredentials(meta.secretSuffix)).url);
  } catch (err) {
    if (!hostOverride) throw err;
  }

  const target: Target = {
    host: hostOverride || parsed!.host,
    port: portOverride ? Number(portOverride) : (parsed?.port ?? 3306),
    database: parsed?.database || `wp_${id}_backoffice`,
  };
  targets.set(id, target);
  return target;
}

export async function getReadOnlyPool(property: string): Promise<Pool> {
  if (!isReadOnlySqlConfigured()) {
    throw new Error("Read-only SQL is not configured (RONBOT_SQL_RO_USER / RONBOT_SQL_RO_PASSWORD)");
  }
  const id = assertProperty(property);
  const existing = pools.get(id);
  if (existing) {
    return existing;
  }

  const { host, port, database } = await resolveTarget(id);
  const pool = mysql.createPool({
    host,
    port,
    user: roUser(),
    password: roPassword(),
    database,
    waitForConnections: true,
    connectionLimit: 2,
    timezone: "local",
    dateStrings: true,
    supportBigNumbers: true,
    multipleStatements: false,
  });

  pools.set(id, pool);
  return pool;
}

/**
 * Cancels a running statement. Uses a one-off connection so it cannot queue behind
 * busy pool connections. MySQL 5.5 has no max_execution_time.
 */
export async function killReadOnlyQuery(property: string, threadId: number): Promise<void> {
  const id = assertProperty(property);
  const { host, port, database } = await resolveTarget(id);
  const conn = await mysql.createConnection({
    host,
    port,
    user: roUser(),
    password: roPassword(),
    database,
  });
  try {
    await conn.query("KILL QUERY ?", [threadId]);
  } finally {
    await conn.end();
  }
}

export async function closeReadOnlyPools(): Promise<void> {
  for (const pool of pools.values()) {
    await pool.end();
  }
  pools.clear();
}
