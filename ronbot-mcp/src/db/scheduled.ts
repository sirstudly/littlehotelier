import type { RowDataPacket } from "mysql2";
import { query } from "./pools.js";

export interface ScheduledJobRow {
  jobId: number;
  classname: string;
  cronSchedule: string;
  active: boolean;
  lastScheduledDate: string | null;
  lastUpdatedDate: string | null;
  parameters: Record<string, string>;
}

interface SchedHeader extends RowDataPacket {
  job_id: number;
  classname: string;
  cron_schedule: string;
  active_yn: string;
  last_scheduled_date: Date | string | null;
  last_updated_date: Date | string | null;
}

interface SchedParam extends RowDataPacket {
  job_id: number;
  name: string;
  value: string;
}

function toIso(value: Date | string | null | undefined): string | null {
  if (value == null) return null;
  if (value instanceof Date) return value.toISOString();
  return String(value);
}

async function withParams(property: string, rows: SchedHeader[]): Promise<ScheduledJobRow[]> {
  if (rows.length === 0) return [];
  const ids = rows.map((r) => r.job_id);
  const placeholders = ids.map(() => "?").join(",");
  const params = await query<SchedParam[]>(
    property,
    `SELECT job_id, name, value FROM wp_lh_scheduled_job_param WHERE job_id IN (${placeholders})`,
    ids,
  );
  const byJob = new Map<number, Record<string, string>>();
  for (const p of params) {
    const map = byJob.get(p.job_id) ?? {};
    map[p.name] = p.value;
    byJob.set(p.job_id, map);
  }
  return rows.map((r) => ({
    jobId: r.job_id,
    classname: r.classname,
    cronSchedule: r.cron_schedule,
    active: r.active_yn === "Y" || r.active_yn === "1",
    lastScheduledDate: toIso(r.last_scheduled_date),
    lastUpdatedDate: toIso(r.last_updated_date),
    parameters: byJob.get(r.job_id) ?? {},
  }));
}

export async function listScheduledJobs(
  property: string,
  activeOnly = true,
): Promise<ScheduledJobRow[]> {
  const sql = activeOnly
    ? `SELECT job_id, classname, cron_schedule, active_yn, last_scheduled_date, last_updated_date
         FROM wp_lh_scheduled_jobs WHERE active_yn IN ('Y','1') ORDER BY job_id`
    : `SELECT job_id, classname, cron_schedule, active_yn, last_scheduled_date, last_updated_date
         FROM wp_lh_scheduled_jobs ORDER BY job_id`;
  const rows = await query<SchedHeader[]>(property, sql);
  return withParams(property, rows);
}

export async function getScheduledJob(
  property: string,
  scheduledJobId: number,
): Promise<ScheduledJobRow | null> {
  const rows = await query<SchedHeader[]>(
    property,
    `SELECT job_id, classname, cron_schedule, active_yn, last_scheduled_date, last_updated_date
       FROM wp_lh_scheduled_jobs WHERE job_id = ?`,
    [scheduledJobId],
  );
  if (rows.length === 0) return null;
  const [job] = await withParams(property, rows);
  return job ?? null;
}
