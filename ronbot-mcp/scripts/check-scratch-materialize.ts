/**
 * Offline checks for Tier 2 scratch naming + validateMaterializeSelect.
 * npx tsx scripts/check-scratch-materialize.ts
 */
import { validateMaterializeSelect } from "../src/db/adhocSql.js";
import {
  ADHOC_TABLE_RE,
  assertAdhocTableName,
  assertSlug,
  buildAdhocTableName,
} from "../src/db/scratchMaterialize.js";

let failures = 0;

function ok(label: string): void {
  console.log(`ok   ${label}`);
}

function fail(label: string, detail: string): void {
  failures++;
  console.error(`FAIL ${label}: ${detail}`);
}

try {
  assertSlug("evl_by_source");
  ok("slug evl_by_source");
} catch (e) {
  fail("slug", (e as Error).message);
}

try {
  assertSlug("1bad");
  fail("slug 1bad", "should reject");
} catch {
  ok("reject slug 1bad");
}

const name = buildAdhocTableName("evl", new Date("2026-11-15T21:05:00Z"));
if (!ADHOC_TABLE_RE.test(name)) fail("build name", name);
else ok(`buildAdhocTableName -> ${name}`);

try {
  assertAdhocTableName("adhoc_evl_20261115_2105");
  ok("assertAdhocTableName good");
} catch (e) {
  fail("assert good", (e as Error).message);
}

try {
  assertAdhocTableName("fact_booking_created");
  fail("assert bad", "should reject fact_");
} catch {
  ok("reject non-adhoc table");
}

try {
  const out = validateMaterializeSelect(
    "SELECT booking_source, SUM(visitor_levy_total) AS evl FROM wp_crh_backoffice.v_wp_lh_booking_reservation GROUP BY booking_source",
  );
  if (!/LIMIT 100001/.test(out.sql)) fail("materialize limit", out.sql);
  else ok("validateMaterializeSelect adds LIMIT 100001");
} catch (e) {
  fail("validateMaterializeSelect", (e as Error).message);
}

try {
  validateMaterializeSelect("DROP TABLE mac_analytics_scratch.x");
  fail("reject drop", "accepted");
} catch {
  ok("reject DROP in materialize select");
}

if (failures > 0) {
  console.error(`\n${failures} failure(s)`);
  process.exit(1);
}
console.log("\nall checks passed");
