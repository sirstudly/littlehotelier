/**
 * Offline checks for validateSql (no DB needed): npx tsx scripts/check-adhoc-sql.ts
 */
import { validateSql } from "../src/db/adhocSql.js";

let failures = 0;

function accept(sql: string, expectSql?: RegExp): void {
  try {
    const out = validateSql(sql, 200);
    if (expectSql && !expectSql.test(out.sql)) {
      failures++;
      console.error(`FAIL accept (sql mismatch): ${sql}\n  got: ${out.sql}`);
      return;
    }
    console.log(`ok   accept: ${sql.replace(/\s+/g, " ")}`);
  } catch (err) {
    failures++;
    console.error(`FAIL accept: ${sql}\n  ${(err as Error).message}`);
  }
}

function reject(sql: string): void {
  try {
    const out = validateSql(sql, 200);
    failures++;
    console.error(`FAIL reject: ${sql}\n  accepted as: ${out.sql}`);
  } catch (err) {
    console.log(`ok   reject: ${sql.replace(/\s+/g, " ")}  (${(err as Error).message})`);
  }
}

accept("SELECT * FROM wp_lh_jobs WHERE status = 'failed'", /LIMIT 201$/);
accept("SELECT COUNT(*) FROM wp_lh_calendar;", /LIMIT 201$/);
accept(
  "SELECT c.room, j.status FROM wp_lh_calendar c JOIN wp_lh_jobs j ON j.job_id = c.job_id WHERE c.checkin_date = '2026-09-25'",
);
accept("SELECT * FROM wp_hsh_backoffice.wp_lh_calendar LIMIT 10", /LIMIT 10$/);
accept("SELECT * FROM wp_options WHERE option_name IN (SELECT option_name FROM wp_options WHERE autoload = 'yes')");
accept("SELECT a FROM t UNION SELECT b FROM u", /^SELECT \* FROM \([\s\S]*\) AS adhoc_union LIMIT 201$/);
accept("SELECT * FROM wp_lh_jobs LIMIT 5000", /LIMIT 201/);
accept("SELECT * FROM wp_lh_jobs LIMIT 10, 5000", /LIMIT 10, 201/);
accept("SELECT * FROM wp_lh_jobs LIMIT 5000 OFFSET 10", /LIMIT 201 OFFSET 10/);
accept("SELECT DATE_FORMAT(checkin_date, '%Y-%m') m, COUNT(*) FROM wp_lh_calendar GROUP BY m");

reject("UPDATE wp_lh_jobs SET status = 'x'");
reject("DELETE FROM wp_lh_jobs");
reject("INSERT INTO wp_lh_jobs (classname) VALUES ('x')");
reject("DROP TABLE wp_lh_jobs");
reject("SET @a = 1");
reject("SELECT 1; DELETE FROM wp_lh_jobs");
reject("SELECT * FROM wp_lh_jobs INTO OUTFILE '/tmp/x'");
reject("SELECT job_id INTO @v FROM wp_lh_jobs");
reject("SELECT SLEEP(5)");
reject("SELECT * FROM wp_lh_jobs WHERE job_id = 1 AND BENCHMARK(1000000, MD5('x'))");
reject("SELECT * FROM wp_lh_jobs WHERE job_id IN (SELECT GET_LOCK('x', 10))");
reject("SELECT * FROM wp_lh_jobs FOR UPDATE");
reject("SELECT * FROM wp_lh_jobs LOCK IN SHARE MODE");
reject("SHOW TABLES");
reject("");

if (failures > 0) {
  console.error(`\n${failures} check(s) failed`);
  process.exit(1);
}
console.log("\nall checks passed");
