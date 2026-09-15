import mysql, { type Pool, type RowDataPacket, type ResultSetHeader } from "mysql2/promise";
import { assertProperty, loadProperties, type PropertyId } from "../config/properties.js";
import { getDbCredentials, parseJdbcUrl } from "../config/secrets.js";

const pools = new Map<PropertyId, Pool>();

export async function getPool(property: string): Promise<Pool> {
  const id = assertProperty(property);
  const existing = pools.get(id);
  if (existing) {
    return existing;
  }

  const meta = loadProperties()[id];
  const creds = await getDbCredentials(meta.secretSuffix);
  const { host, port, database } = parseJdbcUrl(creds.url);

  const pool = mysql.createPool({
    host,
    port,
    user: creds.username,
    password: creds.password,
    database,
    waitForConnections: true,
    connectionLimit: 3,
    timezone: "local",
  });

  pools.set(id, pool);
  return pool;
}

export async function query<T extends RowDataPacket[]>(
  property: string,
  sql: string,
  params: unknown[] = [],
): Promise<T> {
  const pool = await getPool(property);
  const [rows] = await pool.query<T>(sql, params as never);
  return rows;
}

export async function execute(
  property: string,
  sql: string,
  params: unknown[] = [],
): Promise<ResultSetHeader> {
  const pool = await getPool(property);
  const [result] = await pool.query<ResultSetHeader>(sql, params as never);
  return result;
}

/**
 * Runs {@code fn} on a dedicated connection inside BEGIN…COMMIT.
 * Rolls back on error. Use for multi-statement inserts that must appear atomically
 * to other clients (e.g. job row + job params).
 */
export async function withTransaction<T>(
  property: string,
  fn: (conn: mysql.PoolConnection) => Promise<T>,
): Promise<T> {
  const pool = await getPool(property);
  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();
    const result = await fn(conn);
    await conn.commit();
    return result;
  } catch (err) {
    try {
      await conn.rollback();
    } catch {
      // ignore rollback errors
    }
    throw err;
  } finally {
    conn.release();
  }
}

export async function closePools(): Promise<void> {
  for (const pool of pools.values()) {
    await pool.end();
  }
  pools.clear();
}
