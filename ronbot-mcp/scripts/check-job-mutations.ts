/**
 * Offline checks for job status transitions + scheduler schedule helpers (no DB):
 * npx tsx scripts/check-job-mutations.ts
 */
import {
  assertRepeatSchedule,
  normalizeRepeatDailyAtForTest,
} from "../src/db/jobScheduler.js";
import { assertJobStatusTransition, assertParamValue } from "../src/db/jobs.js";

let failures = 0;

function ok(label: string): void {
  console.log(`ok   ${label}`);
}

function fail(label: string, detail: string): void {
  failures++;
  console.error(`FAIL ${label}: ${detail}`);
}

function expectOk(label: string, fn: () => void): void {
  try {
    fn();
    ok(label);
  } catch (err) {
    fail(label, (err as Error).message);
  }
}

function expectFail(label: string, fn: () => void): void {
  try {
    fn();
    fail(label, "expected throw");
  } catch (err) {
    ok(`${label}  (${(err as Error).message})`);
  }
}

// Same-status no-op
expectOk("noop submitted→submitted", () => assertJobStatusTransition("submitted", "submitted"));

// Abort
expectOk("abort from processing", () => assertJobStatusTransition("processing", "aborted"));
expectOk("abort from submitted", () => assertJobStatusTransition("submitted", "aborted"));
expectFail("abort from completed", () => assertJobStatusTransition("completed", "aborted"));

// Pause
expectOk("pause from submitted", () => assertJobStatusTransition("submitted", "paused"));
expectFail("pause from processing", () => assertJobStatusTransition("processing", "paused"));

// Requeue
expectOk("requeue from completed", () => assertJobStatusTransition("completed", "submitted"));
expectOk("requeue from failed", () => assertJobStatusTransition("failed", "submitted"));
expectFail("requeue from processing", () => assertJobStatusTransition("processing", "submitted"));

// Retry
expectOk("retry from processing", () => assertJobStatusTransition("processing", "retry"));
expectFail("retry from submitted", () => assertJobStatusTransition("submitted", "retry"));

// Forbidden targets
expectFail("set completed", () => assertJobStatusTransition("failed", "completed"));
expectFail("set processing", () => assertJobStatusTransition("submitted", "processing"));

// Params
expectOk("param value ok", () => assertParamValue("horizon_days", "30"));
expectFail("empty param name", () => assertParamValue("", "x"));
expectFail("param too long", () => assertParamValue("x", "a".repeat(256)));

// Scheduler repeat
expectOk("minutes only", () => {
  const s = assertRepeatSchedule(15, null);
  if (s.repeatTimeMinutes !== 15 || s.repeatDailyAt !== null) throw new Error(JSON.stringify(s));
});
expectOk("daily HH:MM", () => {
  const s = assertRepeatSchedule(null, "07:00");
  if (s.repeatDailyAt !== "07:00:00") throw new Error(JSON.stringify(s));
});
expectOk("daily HH:MM:SS", () => {
  const s = assertRepeatSchedule(null, "07:15:30");
  if (s.repeatDailyAt !== "07:15:30") throw new Error(JSON.stringify(s));
});
expectFail("both repeat fields", () => assertRepeatSchedule(10, "07:00"));
expectFail("neither repeat field", () => assertRepeatSchedule(null, null));
expectFail("bad daily", () => normalizeRepeatDailyAtForTest("25:00"));
expectFail("zero minutes", () => assertRepeatSchedule(0, null));

if (failures > 0) {
  console.error(`\n${failures} failure(s)`);
  process.exit(1);
}
console.log("\nall checks passed");
