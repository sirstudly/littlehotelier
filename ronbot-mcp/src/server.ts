import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";
import * as readApi from "./cloudbeds/readApiClient.js";
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
    "Live Cloudbeds booking search (via ronbot-read-api). query may be a visible reservation id, third-party/OTA ref, or guest name. Returns a list of matches (exact id matches preferred).",
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
    "Live Cloudbeds folio transactions. Prefer reservationId from get_booking; otherwise a unique searchable query (visible id / OTA ref). Fails if ambiguous.",
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
    "Live booking + folio transactions + DB job history. Prefer reservationId from get_booking; otherwise a unique searchable query. Fails if ambiguous.",
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

  server.tool(
    "insert_job",
    "Enqueue an allowlisted job into wp_lh_jobs (status=submitted)",
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

        const jobId = await jobsDb.insertJob(property, entry.classname, parameters);
        audit("insert_job", {
          property,
          job_type: job_type,
          classname: entry.classname,
          job_id: jobId,
          parameters,
          requested_by: requested_by ?? null,
        });
        return ok({
          jobId,
          property,
          jobType: job_type,
          classname: entry.classname,
          status: "submitted",
          parameters,
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
