import type { RowDataPacket } from "mysql2";
import { query } from "./pools.js";

interface CountRow extends RowDataPacket {
  status: string;
  cnt: number;
}

export interface JobQueueStats {
  property: string;
  submitted: number;
  processing: number;
  retry: number;
  totalOutstanding: number;
}

export async function getJobQueueStats(property: string): Promise<JobQueueStats> {
  const rows = await query<CountRow[]>(
    property,
    `SELECT status, COUNT(1) AS cnt
       FROM wp_lh_jobs
      WHERE status IN ('submitted', 'processing', 'retry')
      GROUP BY status`,
  );
  const counts: Record<string, number> = {
    submitted: 0,
    processing: 0,
    retry: 0,
  };
  for (const row of rows) {
    counts[row.status] = Number(row.cnt);
  }
  return {
    property,
    submitted: counts.submitted ?? 0,
    processing: counts.processing ?? 0,
    retry: counts.retry ?? 0,
    totalOutstanding:
      (counts.submitted ?? 0) + (counts.processing ?? 0) + (counts.retry ?? 0),
  };
}
