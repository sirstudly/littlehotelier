import type { ResultSetHeader, RowDataPacket } from "mysql2";
import { execute, query, withTransaction } from "./pools.js";

export interface JobRow {
  jobId: number;
  classname: string;
  status: string;
  processedBy: string | null;
  createdDate: string | null;
  startDate: string | null;
  endDate: string | null;
  lastUpdatedDate: string | null;
  parameters: Record<string, string>;
}

interface JobHeaderRow extends RowDataPacket {
  job_id: number;
  classname: string;
  status: string;
  processed_by: string | null;
  created_date: Date | string | null;
  start_date: Date | string | null;
  end_date: Date | string | null;
  last_updated_date: Date | string | null;
}

interface ParamRow extends RowDataPacket {
  job_id: number;
  name: string;
  value: string;
}

function toIso(value: Date | string | null | undefined): string | null {
  if (value == null) return null;
  if (value instanceof Date) return value.toISOString();
  return String(value);
}

async function attachParams(property: string, jobs: JobHeaderRow[]): Promise<JobRow[]> {
  if (jobs.length === 0) return [];
  const ids = jobs.map((j) => j.job_id);
  const placeholders = ids.map(() => "?").join(",");
  const params = await query<ParamRow[]>(
    property,
    `SELECT job_id, name, value FROM wp_lh_job_param WHERE job_id IN (${placeholders})`,
    ids,
  );
  const byJob = new Map<number, Record<string, string>>();
  for (const p of params) {
    const map = byJob.get(p.job_id) ?? {};
    map[p.name] = p.value;
    byJob.set(p.job_id, map);
  }
  return jobs.map((j) => ({
    jobId: j.job_id,
    classname: j.classname,
    status: j.status,
    processedBy: j.processed_by,
    createdDate: toIso(j.created_date),
    startDate: toIso(j.start_date),
    endDate: toIso(j.end_date),
    lastUpdatedDate: toIso(j.last_updated_date),
    parameters: byJob.get(j.job_id) ?? {},
  }));
}

export async function getJob(property: string, jobId: number): Promise<JobRow | null> {
  const rows = await query<JobHeaderRow[]>(
    property,
    `SELECT job_id, classname, status, processed_by, created_date, start_date, end_date, last_updated_date
       FROM wp_lh_jobs WHERE job_id = ?`,
    [jobId],
  );
  if (rows.length === 0) return null;
  const [job] = await attachParams(property, rows);
  return job ?? null;
}

export interface ListJobsFilter {
  status?: string;
  classname?: string;
  reservationId?: string;
  bookingReference?: string;
  since?: string;
  until?: string;
  limit?: number;
}

export async function listJobs(property: string, filter: ListJobsFilter): Promise<JobRow[]> {
  const limit = Math.max(1, Math.min(filter.limit ?? 50, 200));
  const where: string[] = ["1=1"];
  const params: unknown[] = [];

  if (filter.status) {
    where.push("j.status = ?");
    params.push(filter.status);
  }
  if (filter.classname) {
    const cn = filter.classname.includes(".")
      ? filter.classname
      : `com.macbackpackers.jobs.${filter.classname}`;
    where.push("j.classname = ?");
    params.push(cn);
  }
  if (filter.since) {
    where.push("j.created_date >= ?");
    params.push(filter.since);
  }
  if (filter.until) {
    where.push("j.created_date <= ?");
    params.push(filter.until);
  }
  if (filter.reservationId) {
    where.push(
      `EXISTS (SELECT 1 FROM wp_lh_job_param p WHERE p.job_id = j.job_id AND p.name = 'reservation_id' AND p.value = ?)`,
    );
    params.push(filter.reservationId);
  }
  if (filter.bookingReference) {
    where.push(
      `EXISTS (SELECT 1 FROM wp_lh_job_param p WHERE p.job_id = j.job_id AND p.name IN ('booking_reference','reservation_id') AND p.value = ?)`,
    );
    params.push(filter.bookingReference);
  }

  params.push(limit);
  const rows = await query<JobHeaderRow[]>(
    property,
    `SELECT j.job_id, j.classname, j.status, j.processed_by, j.created_date, j.start_date, j.end_date, j.last_updated_date
       FROM wp_lh_jobs j
      WHERE ${where.join(" AND ")}
      ORDER BY j.job_id DESC
      LIMIT ?`,
    params,
  );
  return attachParams(property, rows);
}

export async function insertJob(
  property: string,
  classname: string,
  parameters: Record<string, string>,
): Promise<number> {
  // Single transaction so processors cannot claim the job before params exist.
  return withTransaction(property, async (conn) => {
    const [result] = await conn.query<ResultSetHeader>(
      `INSERT INTO wp_lh_jobs (classname, status, created_date) VALUES (?, 'submitted', NOW())`,
      [classname],
    );
    const jobId = result.insertId;
    for (const [name, value] of Object.entries(parameters)) {
      await conn.query(
        `INSERT INTO wp_lh_job_param (job_id, name, value) VALUES (?, ?, ?)`,
        [jobId, name, value],
      );
    }
    return jobId;
  });
}

/** Valid JobStatus values on wp_lh_jobs.status. */
export const JOB_STATUSES = [
  "submitted",
  "processing",
  "completed",
  "failed",
  "aborted",
  "retry",
  "paused",
] as const;

export type JobStatus = (typeof JOB_STATUSES)[number];

const MAX_PARAM_VALUE_LEN = 255;

/** Ops-safe transitions for WhatsApp / MCP. Never set completed or processing from chat. */
const ALLOWED_TRANSITIONS: Record<string, ReadonlySet<string>> = {
  aborted: new Set(["submitted", "retry", "paused", "failed", "processing"]),
  paused: new Set(["submitted", "retry"]),
  submitted: new Set(["failed", "aborted", "paused", "retry", "completed"]),
  retry: new Set(["processing", "failed", "aborted"]),
};

export function assertJobStatusTransition(from: string, to: string): void {
  if (from === to) return;
  if (to === "completed" || to === "processing") {
    throw new Error(`Cannot set status to '${to}' from chat (ops-safe policy)`);
  }
  const allowedFrom = ALLOWED_TRANSITIONS[to];
  if (!allowedFrom) {
    throw new Error(
      `Target status '${to}' is not allowed. Allowed targets: ${Object.keys(ALLOWED_TRANSITIONS).join(", ")}`,
    );
  }
  if (!allowedFrom.has(from)) {
    throw new Error(
      `Cannot change status from '${from}' to '${to}'. Allowed sources for '${to}': ${[...allowedFrom].join(", ")}`,
    );
  }
}

export function assertParamValue(name: string, value: string): void {
  if (!name || name.trim() === "") {
    throw new Error("Parameter name must be non-empty");
  }
  if (value.length > MAX_PARAM_VALUE_LEN) {
    throw new Error(`Parameter '${name}' value exceeds ${MAX_PARAM_VALUE_LEN} characters`);
  }
}

/**
 * Update wp_lh_jobs.status with ops-safe transition rules.
 * Requeue to submitted clears processed_by / start_date / end_date.
 */
export async function updateJobStatus(
  property: string,
  jobId: number,
  status: string,
): Promise<JobRow> {
  const existing = await getJob(property, jobId);
  if (!existing) {
    throw new Error(`Job ${jobId} not found`);
  }
  assertJobStatusTransition(existing.status, status);
  if (existing.status === status) {
    return existing;
  }

  const requeue = status === "submitted";
  if (requeue) {
    await execute(
      property,
      `UPDATE wp_lh_jobs
          SET status = ?, processed_by = NULL, start_date = NULL, end_date = NULL, last_updated_date = NOW()
        WHERE job_id = ?`,
      [status, jobId],
    );
  } else {
    await execute(
      property,
      `UPDATE wp_lh_jobs SET status = ?, last_updated_date = NOW() WHERE job_id = ?`,
      [status, jobId],
    );
  }

  const updated = await getJob(property, jobId);
  if (!updated) {
    throw new Error(`Job ${jobId} disappeared after status update`);
  }
  return updated;
}

export interface UpdateJobParamsInput {
  set?: Record<string, string>;
  delete?: string[];
}

/**
 * Upsert / delete wp_lh_job_param rows. Refuses while status is processing.
 */
export async function updateJobParams(
  property: string,
  jobId: number,
  input: UpdateJobParamsInput,
): Promise<JobRow> {
  const set = input.set ?? {};
  const del = input.delete ?? [];
  if (Object.keys(set).length === 0 && del.length === 0) {
    throw new Error("Provide at least one parameter to set or delete");
  }

  const existing = await getJob(property, jobId);
  if (!existing) {
    throw new Error(`Job ${jobId} not found`);
  }
  if (existing.status === "processing") {
    throw new Error(`Cannot modify parameters while job ${jobId} is processing`);
  }

  for (const [name, value] of Object.entries(set)) {
    assertParamValue(name, value);
  }
  for (const name of del) {
    if (!name || name.trim() === "") {
      throw new Error("Parameter name to delete must be non-empty");
    }
  }

  await withTransaction(property, async (conn) => {
    for (const name of del) {
      await conn.query(`DELETE FROM wp_lh_job_param WHERE job_id = ? AND name = ?`, [jobId, name]);
    }
    for (const [name, value] of Object.entries(set)) {
      const [rows] = await conn.query<RowDataPacket[]>(
        `SELECT job_param_id FROM wp_lh_job_param WHERE job_id = ? AND name = ? LIMIT 1`,
        [jobId, name],
      );
      if (rows.length > 0) {
        await conn.query(`UPDATE wp_lh_job_param SET value = ? WHERE job_id = ? AND name = ?`, [
          value,
          jobId,
          name,
        ]);
      } else {
        await conn.query(`INSERT INTO wp_lh_job_param (job_id, name, value) VALUES (?, ?, ?)`, [
          jobId,
          name,
          value,
        ]);
      }
    }
    await conn.query(`UPDATE wp_lh_jobs SET last_updated_date = NOW() WHERE job_id = ?`, [jobId]);
  });

  const updated = await getJob(property, jobId);
  if (!updated) {
    throw new Error(`Job ${jobId} disappeared after param update`);
  }
  return updated;
}
