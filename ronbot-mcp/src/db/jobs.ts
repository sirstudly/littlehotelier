import type { RowDataPacket } from "mysql2";
import { execute, query } from "./pools.js";

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
  const result = await execute(
    property,
    `INSERT INTO wp_lh_jobs (classname, status, created_date) VALUES (?, 'submitted', NOW())`,
    [classname],
  );
  const jobId = result.insertId;
  for (const [name, value] of Object.entries(parameters)) {
    await execute(
      property,
      `INSERT INTO wp_lh_job_param (job_id, name, value) VALUES (?, ?, ?)`,
      [jobId, name, value],
    );
  }
  return jobId;
}
