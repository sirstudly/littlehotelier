import type { ResultSetHeader, RowDataPacket } from "mysql2";
import { assertParamValue } from "./jobs.js";
import { query, withTransaction } from "./pools.js";

export interface JobSchedulerRow {
  schedulerId: number;
  classname: string;
  repeatTimeMinutes: number | null;
  repeatDailyAt: string | null;
  active: boolean;
  lastRunDate: string | null;
  createdDate: string | null;
  lastUpdatedDate: string | null;
  parameters: Record<string, string>;
}

interface SchedHeader extends RowDataPacket {
  job_id: number;
  classname: string;
  repeat_time_minutes: number | null;
  repeat_daily_at: string | null;
  active_yn: string | null;
  last_run_date: Date | string | null;
  created_date: Date | string | null;
  last_updated_date: Date | string | null;
}

interface SchedParam extends RowDataPacket {
  job_id: number;
  name: string;
  value: string;
}

const DAILY_AT_RE = /^([01]\d|2[0-3]):([0-5]\d)(:([0-5]\d))?$/;

function toIso(value: Date | string | null | undefined): string | null {
  if (value == null) return null;
  if (value instanceof Date) return value.toISOString();
  return String(value);
}

function isActive(yn: string | null | undefined): boolean {
  return yn === "Y" || yn === "1";
}

function normalizeDailyAt(value: string): string {
  const m = DAILY_AT_RE.exec(value.trim());
  if (!m) {
    throw new Error(`repeat_daily_at must be HH:MM or HH:MM:SS (24h), got '${value}'`);
  }
  const hour = m[1];
  const minute = m[2];
  const second = m[4] ?? "00";
  return `${hour}:${minute}:${second}`;
}

export function assertRepeatSchedule(
  repeatTimeMinutes: number | null | undefined,
  repeatDailyAt: string | null | undefined,
): { repeatTimeMinutes: number | null; repeatDailyAt: string | null } {
  const hasMinutes = repeatTimeMinutes != null;
  const hasDaily = repeatDailyAt != null && String(repeatDailyAt).trim() !== "";
  if (hasMinutes === hasDaily) {
    throw new Error("Provide exactly one of repeat_time_minutes or repeat_daily_at");
  }
  if (hasMinutes) {
    const mins = Number(repeatTimeMinutes);
    if (!Number.isInteger(mins) || mins < 1) {
      throw new Error("repeat_time_minutes must be a positive integer");
    }
    return { repeatTimeMinutes: mins, repeatDailyAt: null };
  }
  return { repeatTimeMinutes: null, repeatDailyAt: normalizeDailyAt(String(repeatDailyAt)) };
}

async function withParams(property: string, rows: SchedHeader[]): Promise<JobSchedulerRow[]> {
  if (rows.length === 0) return [];
  const ids = rows.map((r) => r.job_id);
  const placeholders = ids.map(() => "?").join(",");
  const params = await query<SchedParam[]>(
    property,
    `SELECT job_id, name, value FROM job_scheduler_param WHERE job_id IN (${placeholders})`,
    ids,
  );
  const byJob = new Map<number, Record<string, string>>();
  for (const p of params) {
    const map = byJob.get(p.job_id) ?? {};
    map[p.name] = p.value;
    byJob.set(p.job_id, map);
  }
  return rows.map((r) => ({
    schedulerId: r.job_id,
    classname: r.classname,
    repeatTimeMinutes: r.repeat_time_minutes,
    repeatDailyAt: r.repeat_daily_at,
    active: isActive(r.active_yn),
    lastRunDate: toIso(r.last_run_date),
    createdDate: toIso(r.created_date),
    lastUpdatedDate: toIso(r.last_updated_date),
    parameters: byJob.get(r.job_id) ?? {},
  }));
}

export interface ListJobSchedulersFilter {
  activeOnly?: boolean;
  classname?: string;
}

export async function listJobSchedulers(
  property: string,
  filter: ListJobSchedulersFilter = {},
): Promise<JobSchedulerRow[]> {
  const where: string[] = ["1=1"];
  const params: unknown[] = [];
  if (filter.activeOnly !== false) {
    where.push("active_yn IN ('Y','1')");
  }
  if (filter.classname) {
    const cn = filter.classname.includes(".")
      ? filter.classname
      : `com.macbackpackers.jobs.${filter.classname}`;
    where.push("classname = ?");
    params.push(cn);
  }
  const rows = await query<SchedHeader[]>(
    property,
    `SELECT job_id, classname, repeat_time_minutes, repeat_daily_at, active_yn,
            last_run_date, created_date, last_updated_date
       FROM job_scheduler
      WHERE ${where.join(" AND ")}
      ORDER BY job_id`,
    params,
  );
  return withParams(property, rows);
}

export async function getJobScheduler(
  property: string,
  schedulerId: number,
): Promise<JobSchedulerRow | null> {
  const rows = await query<SchedHeader[]>(
    property,
    `SELECT job_id, classname, repeat_time_minutes, repeat_daily_at, active_yn,
            last_run_date, created_date, last_updated_date
       FROM job_scheduler WHERE job_id = ?`,
    [schedulerId],
  );
  if (rows.length === 0) return null;
  const [row] = await withParams(property, rows);
  return row ?? null;
}

export interface CreateJobSchedulerInput {
  classname: string;
  repeatTimeMinutes?: number | null;
  repeatDailyAt?: string | null;
  active?: boolean;
  parameters?: Record<string, string>;
}

export async function createJobScheduler(
  property: string,
  input: CreateJobSchedulerInput,
): Promise<JobSchedulerRow> {
  const schedule = assertRepeatSchedule(input.repeatTimeMinutes, input.repeatDailyAt);
  const parameters = input.parameters ?? {};
  for (const [name, value] of Object.entries(parameters)) {
    assertParamValue(name, value);
  }
  const activeYn = input.active === false ? "N" : "Y";

  const schedulerId = await withTransaction(property, async (conn) => {
    const [result] = await conn.query<ResultSetHeader>(
      `INSERT INTO job_scheduler
         (classname, repeat_time_minutes, repeat_daily_at, active_yn, created_date, last_updated_date)
       VALUES (?, ?, ?, ?, NOW(), NOW())`,
      [input.classname, schedule.repeatTimeMinutes, schedule.repeatDailyAt, activeYn],
    );
    const id = result.insertId;
    for (const [name, value] of Object.entries(parameters)) {
      await conn.query(`INSERT INTO job_scheduler_param (job_id, name, value) VALUES (?, ?, ?)`, [
        id,
        name,
        value,
      ]);
    }
    return id;
  });

  const created = await getJobScheduler(property, schedulerId);
  if (!created) {
    throw new Error(`Scheduler ${schedulerId} missing after create`);
  }
  return created;
}

export interface UpdateJobSchedulerInput {
  active?: boolean;
  repeatTimeMinutes?: number | null;
  repeatDailyAt?: string | null;
  set?: Record<string, string>;
  delete?: string[];
}

export async function updateJobScheduler(
  property: string,
  schedulerId: number,
  input: UpdateJobSchedulerInput,
): Promise<JobSchedulerRow> {
  const existing = await getJobScheduler(property, schedulerId);
  if (!existing) {
    throw new Error(`Scheduler ${schedulerId} not found`);
  }

  const set = input.set ?? {};
  const del = input.delete ?? [];
  const touchingSchedule =
    input.repeatTimeMinutes !== undefined || input.repeatDailyAt !== undefined;
  const touchingActive = input.active !== undefined;
  if (!touchingSchedule && !touchingActive && Object.keys(set).length === 0 && del.length === 0) {
    throw new Error("Provide active, repeat schedule, and/or parameters to change");
  }

  for (const [name, value] of Object.entries(set)) {
    assertParamValue(name, value);
  }
  for (const name of del) {
    if (!name || name.trim() === "") {
      throw new Error("Parameter name to delete must be non-empty");
    }
  }

  let schedule: { repeatTimeMinutes: number | null; repeatDailyAt: string | null } | null = null;
  if (touchingSchedule) {
    // Setting one clears the other: if only one field is passed, treat the other as null.
    const minutes =
      input.repeatTimeMinutes !== undefined
        ? input.repeatTimeMinutes
        : input.repeatDailyAt != null
          ? null
          : existing.repeatTimeMinutes;
    const daily =
      input.repeatDailyAt !== undefined
        ? input.repeatDailyAt
        : input.repeatTimeMinutes != null
          ? null
          : existing.repeatDailyAt;
    schedule = assertRepeatSchedule(minutes, daily);
  }

  await withTransaction(property, async (conn) => {
    if (touchingActive || schedule) {
      const sets: string[] = ["last_updated_date = NOW()"];
      const params: unknown[] = [];
      if (touchingActive) {
        sets.push("active_yn = ?");
        params.push(input.active ? "Y" : "N");
      }
      if (schedule) {
        sets.push("repeat_time_minutes = ?", "repeat_daily_at = ?");
        params.push(schedule.repeatTimeMinutes, schedule.repeatDailyAt);
      }
      params.push(schedulerId);
      await conn.query(`UPDATE job_scheduler SET ${sets.join(", ")} WHERE job_id = ?`, params);
    } else {
      await conn.query(`UPDATE job_scheduler SET last_updated_date = NOW() WHERE job_id = ?`, [
        schedulerId,
      ]);
    }

    for (const name of del) {
      await conn.query(`DELETE FROM job_scheduler_param WHERE job_id = ? AND name = ?`, [
        schedulerId,
        name,
      ]);
    }
    for (const [name, value] of Object.entries(set)) {
      const [rows] = await conn.query<RowDataPacket[]>(
        `SELECT job_param_id FROM job_scheduler_param WHERE job_id = ? AND name = ? LIMIT 1`,
        [schedulerId, name],
      );
      if (rows.length > 0) {
        await conn.query(
          `UPDATE job_scheduler_param SET value = ? WHERE job_id = ? AND name = ?`,
          [value, schedulerId, name],
        );
      } else {
        await conn.query(
          `INSERT INTO job_scheduler_param (job_id, name, value) VALUES (?, ?, ?)`,
          [schedulerId, name, value],
        );
      }
    }
  });

  const updated = await getJobScheduler(property, schedulerId);
  if (!updated) {
    throw new Error(`Scheduler ${schedulerId} disappeared after update`);
  }
  return updated;
}

export async function deleteJobScheduler(property: string, schedulerId: number): Promise<void> {
  const existing = await getJobScheduler(property, schedulerId);
  if (!existing) {
    throw new Error(`Scheduler ${schedulerId} not found`);
  }
  await withTransaction(property, async (conn) => {
    await conn.query(`DELETE FROM job_scheduler_param WHERE job_id = ?`, [schedulerId]);
    await conn.query(`DELETE FROM job_scheduler WHERE job_id = ?`, [schedulerId]);
  });
}

/** Exported for offline checks. */
export function normalizeRepeatDailyAtForTest(value: string): string {
  return normalizeDailyAt(value);
}
