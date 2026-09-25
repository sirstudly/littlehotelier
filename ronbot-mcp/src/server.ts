import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import * as readApi from "./cloudbeds/readApiClient.js";
import {
  resolveEmailParamInParameters,
  resolveEmailRecipient,
  resolveEmailRecipientList,
} from "./config/emailAliases.js";
import { assertProperty, loadJobAllowlist, loadProperties } from "./config/properties.js";
import * as jobsDb from "./db/jobs.js";
import { searchLogs } from "./db/logs.js";
import { getJobQueueStats } from "./db/queue.js";
import * as scheduledDb from "./db/scheduled.js";
import { audit } from "./lib/audit.js";

function ok(data: unknown) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(data, null, 2) }],
  };
}

function fail(err: unknown) {
  const message = err instanceof Error ? err.message : String(err);
  return {
    content: [{ type: "text" as const, text: JSON.stringify({ error: message }, null, 2) }],
    isError: true,
  };
}

const propertySchema = z.enum(["crh", "hsh", "rmb", "lsh"]);
const edinburghPropertySchema = z.enum(["crh", "hsh", "rmb"]);
const EDINBURGH_PROPERTIES = ["crh", "hsh", "rmb"] as const;
const ALL_PROPERTIES = ["crh", "hsh", "rmb", "lsh"] as const;
type PropertyId = (typeof ALL_PROPERTIES)[number];
const yearMonth = z.string().regex(/^\d{4}-(0[1-9]|1[0-2])$/, "Expected YYYY-MM");

export function createServer(): McpServer {
  const server = new McpServer({
    name: "ronbot-ops",
    version: "1.0.0",
  });

  server.tool("list_properties", "List hostel properties available to ronbot", {}, async () => {
    try {
      const props = loadProperties();
      return ok(
        Object.entries(props).map(([id, cfg]) => ({
          id,
          displayName: cfg.displayName,
        })),
      );
    } catch (err) {
      return fail(err);
    }
  });

  server.tool(
    "get_job",
    "Get a single job by id from wp_lh_jobs",
    {
      property: propertySchema,
      job_id: z.number().int().positive(),
    },
    async ({ property, job_id }) => {
      try {
        const job = await jobsDb.getJob(property, job_id);
        if (!job) return fail(new Error(`Job ${job_id} not found`));
        return ok(job);
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "list_jobs",
    "List recent jobs, optionally filtered by status/classname/reservation",
    {
      property: propertySchema,
      status: z.string().optional(),
      classname: z.string().optional(),
      reservation_id: z.string().optional(),
      booking_reference: z.string().optional(),
      since: z.string().optional(),
      until: z.string().optional(),
      limit: z.number().int().positive().max(200).optional(),
    },
    async (args) => {
      try {
        const rows = await jobsDb.listJobs(args.property, {
          status: args.status,
          classname: args.classname,
          reservationId: args.reservation_id,
          bookingReference: args.booking_reference,
          since: args.since,
          until: args.until,
          limit: args.limit,
        });
        return ok(rows);
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "list_scheduled_jobs",
    "List scheduled jobs (cron templates) for a property",
    {
      property: propertySchema,
      active_only: z.boolean().optional(),
    },
    async ({ property, active_only }) => {
      try {
        return ok(await scheduledDb.listScheduledJobs(property, active_only ?? true));
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "get_scheduled_job",
    "Get one scheduled job by id",
    {
      property: propertySchema,
      scheduled_job_id: z.number().int().positive(),
    },
    async ({ property, scheduled_job_id }) => {
      try {
        const job = await scheduledDb.getScheduledJob(property, scheduled_job_id);
        if (!job) return fail(new Error(`Scheduled job ${scheduled_job_id} not found`));
        return ok(job);
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "get_job_queue_stats",
    "Queue depth for server load: submitted, processing, retry counts",
    { property: propertySchema },
    async ({ property }) => {
      try {
        return ok(await getJobQueueStats(property));
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "search_logs",
    "Search processor log files for a property",
    {
      property: propertySchema,
      pattern: z.string().min(1),
      max_lines: z.number().int().positive().max(200).optional(),
      file: z.string().optional(),
    },
    async ({ property, pattern, max_lines, file }) => {
      try {
        return ok(searchLogs(property, pattern, { maxLines: max_lines, file }));
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "get_booking",
    "Booking search (via ronbot-read-api). query may be a visible reservation id, third-party/OTA ref, or guest name. Name-like queries resolve via local calendar DB first, then Cloudbeds get_reservation by id; Cloudbeds free-text search runs only when the calendar has no matches. Identifier-like queries go straight to Cloudbeds. Returns a list of matches (exact id matches preferred) — enough to pick a booking; for folio/notes/rooms call get_booking_timeline with reservationId. Prefer get_booking_timeline once when staff already have a unique ref.",
    {
      property: propertySchema,
      query: z.string().min(1),
    },
    async ({ property, query }) => {
      try {
        return ok(await readApi.getBooking({ property, query }));
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "list_transactions",
    "Live Cloudbeds folio transactions. Prefer reservationId from get_booking/get_booking_timeline; otherwise a unique searchable query. Fails if ambiguous. Skip if you already called get_booking_timeline.",
    {
      property: propertySchema,
      reservation_id: z.string().min(1),
    },
    async ({ property, reservation_id }) => {
      try {
        return ok(await readApi.listTransactions(property, reservation_id));
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "get_booking_timeline",
    "Preferred one-shot live booking + folio transactions + DB job history. Pass the exact staff ref (visible id / OTA ref / HW number) once; do not also call get_booking or list_transactions, and do not probe spelling variants unless this returns no match. Name-like queries resolve via local calendar first (then Cloudbeds by id) before free-text Cloudbeds search. Fails if the query is ambiguous.",
    {
      property: propertySchema,
      reservation_id: z.string().min(1),
    },
    async ({ property, reservation_id }) => {
      try {
        return ok(await readApi.getBookingTimeline(property, reservation_id));
      } catch (err) {
        return fail(err);
      }
    },
  );

  const isoDate = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Expected YYYY-MM-DD");

  server.tool(
    "check_stay_continuation",
    "Cleaning / extension check: is this guest staying on past checkout (same reservation extended, or a new linked booking in the same beds)? Pass property + query (prefer reservationId from get_booking). Optional as_of (YYYY-MM-DD; defaults today). Returns stayingOn, kind (none|same_reservation|linked_reservation), per-bed continuingRooms, and followingBookings when linked. Do not guess from get_booking alone when staff ask about a follow-on booking.",
    {
      property: propertySchema,
      query: z.string().min(1),
      as_of: isoDate.optional(),
    },
    async ({ property, query, as_of }) => {
      try {
        return ok(await readApi.getStayContinuation({ property, query, asOf: as_of }));
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "get_availability",
    "Live Cloudbeds sellable availability (beds for dorms, rooms for privates) via ronbot-read-api. Pass properties for one or more hostels, or omit both properties and property to query all (crh/hsh/rmb/lsh) in one call. Optional from/to (YYYY-MM-DD, inclusive; defaults today→tomorrow). Do not issue multiple get_availability calls for a multi-property question — use one fan-out call.",
    {
      properties: z.array(propertySchema).min(1).max(4).optional(),
      property: propertySchema.optional(),
      from: isoDate.optional(),
      to: isoDate.optional(),
    },
    async ({ properties, property, from, to }) => {
      try {
        let targets: Array<"crh" | "hsh" | "rmb" | "lsh">;
        if (properties && properties.length > 0) {
          targets = [...new Set(properties)];
        } else if (property) {
          targets = [property];
        } else {
          targets = ["crh", "hsh", "rmb", "lsh"];
        }

        const settled = await Promise.allSettled(
          targets.map((p) => readApi.getAvailability({ property: p, from, to })),
        );

        const results = settled.map((outcome, i) => {
          const prop = targets[i];
          if (outcome.status === "fulfilled") {
            return { property: prop, ok: true as const, data: outcome.value };
          }
          const message =
            outcome.reason instanceof Error ? outcome.reason.message : String(outcome.reason);
          return { property: prop, ok: false as const, error: message };
        });

        const anyOk = results.some((r) => r.ok);
        const payload = {
          from: from ?? null,
          to: to ?? null,
          results,
        };
        if (!anyOk) {
          return {
            content: [{ type: "text" as const, text: JSON.stringify(payload, null, 2) }],
            isError: true,
          };
        }
        return ok(payload);
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "get_channel_production",
    "Live Channel Production numbers (Cloudbeds Data Insights) for one stay-date month via ronbot-read-api: revenue, room nights and ADR by source (Booking.com, Hostelworld, Website, Walk-In, ...) with % shares, plus totals (revenue, roomsSold, bdcCommission = Booking.com revenue / 6.67, netRevenue, avgPricePerBed, occupancyPct = % of beds occupied for the month). month is YYYY-MM. Pass properties for one or more hostels, or omit both properties and property to query all (crh/hsh/rmb/lsh) in one call. For year-on-year comparisons call once per month needed. To email the spreadsheet instead, use enqueue_channel_production_report.",
    {
      month: yearMonth,
      properties: z.array(propertySchema).min(1).max(4).optional(),
      property: propertySchema.optional(),
    },
    async ({ month, properties, property }) => {
      try {
        let targets: PropertyId[];
        if (properties && properties.length > 0) {
          targets = [...new Set(properties)];
        } else if (property) {
          targets = [property];
        } else {
          targets = [...ALL_PROPERTIES];
        }

        const settled = await Promise.allSettled(
          targets.map((p) => readApi.getChannelProduction({ property: p, month })),
        );
        const results = settled.map((outcome, i) => {
          const prop = targets[i];
          if (outcome.status === "fulfilled") {
            return { property: prop, ok: true as const, data: outcome.value };
          }
          const message =
            outcome.reason instanceof Error ? outcome.reason.message : String(outcome.reason);
          return { property: prop, ok: false as const, error: message };
        });

        const payload = { month, results };
        if (!results.some((r) => r.ok)) {
          return {
            content: [{ type: "text" as const, text: JSON.stringify(payload, null, 2) }],
            isError: true,
          };
        }
        return ok(payload);
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "get_occupancy",
    "Occupancy rate (% of beds occupied, Cloudbeds Data Insights Occupancy report) over an inclusive stay-date range via ronbot-read-api. Returns overall occupancyPct (night-weighted across the range), bedsBooked, revenue, and a month-by-month breakdown. from/to are YYYY-MM-DD (max 366 nights). Pass properties for one or more hostels, or omit both properties and property to query all (crh/hsh/rmb/lsh) in one call.",
    {
      from: isoDate,
      to: isoDate,
      properties: z.array(propertySchema).min(1).max(4).optional(),
      property: propertySchema.optional(),
    },
    async ({ from, to, properties, property }) => {
      try {
        let targets: PropertyId[];
        if (properties && properties.length > 0) {
          targets = [...new Set(properties)];
        } else if (property) {
          targets = [property];
        } else {
          targets = [...ALL_PROPERTIES];
        }

        const settled = await Promise.allSettled(
          targets.map((p) => readApi.getOccupancy({ property: p, from, to })),
        );
        const results = settled.map((outcome, i) => {
          const prop = targets[i];
          if (outcome.status === "fulfilled") {
            return { property: prop, ok: true as const, data: outcome.value };
          }
          const message =
            outcome.reason instanceof Error ? outcome.reason.message : String(outcome.reason);
          return { property: prop, ok: false as const, error: message };
        });

        const payload = { from, to, results };
        if (!results.some((r) => r.ok)) {
          return {
            content: [{ type: "text" as const, text: JSON.stringify(payload, null, 2) }],
            isError: true,
          };
        }
        return ok(payload);
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "insert_job",
    "Enqueue an allowlisted job into wp_lh_jobs (status=submitted). If parameters include email or to_emails (comma-delimited), shorthand aliases (accounts/hannah/jay/ron) are resolved to full addresses.",
    {
      property: propertySchema,
      job_type: z.string().min(1),
      parameters: z.record(z.string()).default({}),
      requested_by: z.string().optional(),
    },
    async ({ property, job_type, parameters, requested_by }) => {
      try {
        assertProperty(property);
        const allowlist = loadJobAllowlist();
        const entry = allowlist[job_type];
        if (!entry) {
          throw new Error(
            `Job type '${job_type}' is not allowlisted. Allowed: ${Object.keys(allowlist).join(", ")}`,
          );
        }
        for (const required of entry.requiredParams) {
          if (!parameters[required] || String(parameters[required]).trim() === "") {
            throw new Error(`Missing required parameter '${required}' for ${job_type}`);
          }
        }
        // Reject unexpected classname-style input
        if (job_type.includes(".") || job_type.includes("/")) {
          throw new Error("job_type must be a short allowlisted name, not a classname");
        }

        const resolvedParameters = resolveEmailParamInParameters(parameters);
        const jobId = await jobsDb.insertJob(property, entry.classname, resolvedParameters);
        audit("insert_job", {
          property,
          job_type: job_type,
          classname: entry.classname,
          job_id: jobId,
          parameters: resolvedParameters,
          requested_by: requested_by ?? null,
        });
        return ok({
          jobId,
          property,
          jobType: job_type,
          classname: entry.classname,
          status: "submitted",
          parameters: resolvedParameters,
        });
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "enqueue_quarterly_evl_6plus_report",
    "Enqueue RunQuarterlyEvl6PlusNightsReportJob: builds EVL nights-6+ / over-5-nights room revenue xlsx and emails it. Edinburgh hostels only (crh/hsh/rmb). Pass property (or properties), or property=all for all three. Ask the user if property is missing — do not assume. email may be a full address or shorthand accounts|hannah|jay|ron. Synonyms: quarterly EVL report, EVL over 5 nights, nights 6+ room revenue.",
    {
      email: z.string().min(1),
      property: z.union([edinburghPropertySchema, z.literal("all")]).optional(),
      properties: z.array(edinburghPropertySchema).min(1).max(3).optional(),
      requested_by: z.string().optional(),
    },
    async ({ email, property, properties, requested_by }) => {
      try {
        const resolvedEmail = resolveEmailRecipient(email);
        let targets: Array<"crh" | "hsh" | "rmb">;
        if (properties && properties.length > 0) {
          targets = [...new Set(properties)];
        } else if (property === "all") {
          targets = [...EDINBURGH_PROPERTIES];
        } else if (property) {
          targets = [property];
        } else {
          throw new Error(
            "Specify which Edinburgh hostel: crh, hsh, or rmb (or property=all / properties list). EVL does not apply to lsh.",
          );
        }

        const allowlist = loadJobAllowlist();
        const entry = allowlist.RunQuarterlyEvl6PlusNightsReportJob;
        if (!entry) {
          throw new Error("RunQuarterlyEvl6PlusNightsReportJob is not allowlisted");
        }

        const parameters = { email: resolvedEmail };
        const enqueued: Array<{ property: string; jobId: number }> = [];
        for (const target of targets) {
          const jobId = await jobsDb.insertJob(target, entry.classname, parameters);
          audit("enqueue_quarterly_evl_6plus_report", {
            property: target,
            job_type: "RunQuarterlyEvl6PlusNightsReportJob",
            classname: entry.classname,
            job_id: jobId,
            parameters,
            requested_by: requested_by ?? null,
          });
          enqueued.push({ property: target, jobId });
        }

        return ok({
          email: resolvedEmail,
          enqueued,
          status: "submitted",
        });
      } catch (err) {
        return fail(err);
      }
    },
  );

  server.tool(
    "enqueue_channel_production_report",
    "Enqueue RunChannelProductionReportJob: builds the Channel Production xlsx (revenue / room nights / ADR by source for year_month, compared with the same month in the previous 2 years, plus BDC commission and net revenue) and emails it. Pass property (or properties), or property=all for every hostel. Ask the user if property or month is missing — do not assume. to_emails entries may be full addresses or shorthand accounts|hannah|jay|ron. Synonyms: channel report, channel production report, revenue by source/channel, monthly channel report. For numbers in chat instead of an email, use get_channel_production.",
    {
      year_month: yearMonth,
      to_emails: z.array(z.string().min(1)).min(1),
      property: z.union([propertySchema, z.literal("all")]).optional(),
      properties: z.array(propertySchema).min(1).max(4).optional(),
      requested_by: z.string().optional(),
    },
    async ({ year_month, to_emails, property, properties, requested_by }) => {
      try {
        const resolvedEmails = resolveEmailRecipientList(to_emails);
        let targets: PropertyId[];
        if (properties && properties.length > 0) {
          targets = [...new Set(properties)];
        } else if (property === "all") {
          targets = [...ALL_PROPERTIES];
        } else if (property) {
          targets = [property];
        } else {
          throw new Error(
            "Specify which hostel: crh, hsh, rmb or lsh (or property=all / properties list).",
          );
        }

        const allowlist = loadJobAllowlist();
        const entry = allowlist.RunChannelProductionReportJob;
        if (!entry) {
          throw new Error("RunChannelProductionReportJob is not allowlisted");
        }

        const parameters = { year_month, to_emails: resolvedEmails.join(",") };
        const enqueued: Array<{ property: string; jobId: number }> = [];
        for (const target of targets) {
          const jobId = await jobsDb.insertJob(target, entry.classname, parameters);
          audit("enqueue_channel_production_report", {
            property: target,
            job_type: "RunChannelProductionReportJob",
            classname: entry.classname,
            job_id: jobId,
            parameters,
            requested_by: requested_by ?? null,
          });
          enqueued.push({ property: target, jobId });
        }

        return ok({
          yearMonth: year_month,
          toEmails: resolvedEmails,
          enqueued,
          status: "submitted",
        });
      } catch (err) {
        return fail(err);
      }
    },
  );

  return server;
}

export async function startStdio(): Promise<void> {
  const server = createServer();
  const transport = new StdioServerTransport();
  await server.connect(transport);
}
