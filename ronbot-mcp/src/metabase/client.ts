/**
 * Headless Metabase REST client (X-API-Key). Used by gated MCP tools for
 * WhatsApp-driven dashboard/question authoring. Tools are hidden when unset.
 */

const DEFAULT_COLLECTION = "Hostel analytics";

export function isMetabaseConfigured(): boolean {
  return Boolean(metabaseUrl() && metabaseApiKey());
}

function metabaseUrl(): string {
  return (process.env.METABASE_URL ?? "").replace(/\/$/, "").trim();
}

function metabaseApiKey(): string {
  return (process.env.METABASE_API_KEY ?? "").trim();
}

function dashboardUrl(id: number): string {
  return `${metabaseUrl()}/dashboard/${id}`;
}

function questionUrl(id: number): string {
  return `${metabaseUrl()}/question/${id}`;
}

async function mbFetch<T>(
  method: string,
  path: string,
  body?: unknown,
): Promise<T> {
  const base = metabaseUrl();
  const key = metabaseApiKey();
  if (!base || !key) {
    throw new Error("Metabase is not configured (METABASE_URL / METABASE_API_KEY)");
  }
  const headers: Record<string, string> = {
    Accept: "application/json",
    "X-API-Key": key,
  };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  const res = await fetch(`${base}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let parsed: unknown;
  try {
    parsed = text ? JSON.parse(text) : null;
  } catch {
    parsed = { error: text };
  }
  if (!res.ok) {
    const errMsg =
      typeof parsed === "object" && parsed && "message" in parsed
        ? String((parsed as { message: unknown }).message)
        : typeof parsed === "object" && parsed && "error" in parsed
          ? String((parsed as { error: unknown }).error)
          : `HTTP ${res.status}`;
    throw new Error(errMsg);
  }
  return parsed as T;
}

type CollectionRow = {
  id: number | string;
  name: string;
  location?: string;
  archived?: boolean;
};

type DatabaseRow = {
  id: number;
  name: string;
  is_sample?: boolean;
};

type Dashcard = {
  id: number;
  card_id?: number | null;
  row: number;
  col: number;
  size_x: number;
  size_y: number;
  visualization_settings?: Record<string, unknown>;
  parameter_mappings?: unknown[];
  card?: { id: number; name?: string };
};

type Dashboard = {
  id: number;
  name: string;
  description?: string | null;
  collection_id?: number | null;
  dashcards?: Dashcard[];
  ordered_cards?: Dashcard[];
  parameters?: unknown[];
};

function dashcardsOf(d: Dashboard): Dashcard[] {
  return d.dashcards ?? d.ordered_cards ?? [];
}

export async function listCollections(): Promise<{
  collections: Array<{ id: number | string; name: string; location?: string }>;
}> {
  const rows = await mbFetch<CollectionRow[]>("GET", "/api/collection");
  return {
    collections: rows
      .filter((c) => !c.archived)
      .map((c) => ({ id: c.id, name: c.name, location: c.location })),
  };
}

export async function ensureCollection(name?: string): Promise<{
  id: number | string;
  name: string;
  created: boolean;
  url_hint: string;
}> {
  const target = (name ?? DEFAULT_COLLECTION).trim() || DEFAULT_COLLECTION;
  const { collections } = await listCollections();
  const existing = collections.find(
    (c) => typeof c.id === "number" && c.name.toLowerCase() === target.toLowerCase(),
  );
  if (existing) {
    return {
      id: existing.id,
      name: existing.name,
      created: false,
      url_hint: `${metabaseUrl()}/collection/${existing.id}`,
    };
  }
  const created = await mbFetch<CollectionRow>("POST", "/api/collection", {
    name: target,
    color: "#509EE3",
  });
  return {
    id: created.id,
    name: created.name,
    created: true,
    url_hint: `${metabaseUrl()}/collection/${created.id}`,
  };
}

export async function listDatabases(): Promise<{
  databases: Array<{ id: number; name: string }>;
}> {
  const body = await mbFetch<{ data?: DatabaseRow[] } | DatabaseRow[]>(
    "GET",
    "/api/database",
  );
  const rows = Array.isArray(body) ? body : (body.data ?? []);
  return {
    databases: rows
      .filter((d) => !d.is_sample)
      .map((d) => ({ id: d.id, name: d.name })),
  };
}

async function resolveDatabaseId(databaseId?: number, databaseName?: string): Promise<number> {
  if (databaseId != null) return databaseId;
  const name = (databaseName ?? "mac_analytics").trim();
  const { databases } = await listDatabases();
  const match = databases.find((d) => d.name.toLowerCase() === name.toLowerCase());
  if (!match) {
    throw new Error(
      `Metabase database "${name}" not found. Connected: ${databases.map((d) => d.name).join(", ") || "(none)"}`,
    );
  }
  return match.id;
}

const DISPLAY_TYPES = [
  "table",
  "bar",
  "line",
  "pie",
  "area",
  "row",
  "scalar",
  "combo",
  "waterfall",
  "scatter",
] as const;

export type DisplayType = (typeof DISPLAY_TYPES)[number];

export async function createNativeQuestion(params: {
  name: string;
  sql: string;
  display?: DisplayType;
  description?: string;
  collection_id?: number;
  database_id?: number;
  database_name?: string;
  visualization_settings?: Record<string, unknown>;
}): Promise<{
  id: number;
  name: string;
  display: string;
  collection_id: number | null;
  url: string;
}> {
  const databaseId = await resolveDatabaseId(params.database_id, params.database_name);
  const display = params.display ?? "table";
  const card = await mbFetch<{
    id: number;
    name: string;
    display: string;
    collection_id: number | null;
  }>("POST", "/api/card", {
    name: params.name,
    description: params.description ?? null,
    collection_id: params.collection_id ?? null,
    display,
    visualization_settings: params.visualization_settings ?? {},
    dataset_query: {
      type: "native",
      database: databaseId,
      native: {
        query: params.sql,
        "template-tags": {},
      },
    },
  });
  return {
    id: card.id,
    name: card.name,
    display: card.display,
    collection_id: card.collection_id,
    url: questionUrl(card.id),
  };
}

export async function createDashboard(params: {
  name: string;
  description?: string;
  collection_id?: number;
}): Promise<{ id: number; name: string; collection_id: number | null; url: string }> {
  const dash = await mbFetch<{
    id: number;
    name: string;
    collection_id: number | null;
  }>("POST", "/api/dashboard", {
    name: params.name,
    description: params.description ?? null,
    collection_id: params.collection_id ?? null,
    parameters: [],
  });
  return {
    id: dash.id,
    name: dash.name,
    collection_id: dash.collection_id,
    url: dashboardUrl(dash.id),
  };
}

export async function getDashboard(id: number): Promise<{
  id: number;
  name: string;
  description: string | null;
  collection_id: number | null;
  url: string;
  dashcards: Array<{
    id: number;
    card_id: number | null;
    card_name?: string;
    row: number;
    col: number;
    size_x: number;
    size_y: number;
  }>;
}> {
  const dash = await mbFetch<Dashboard>("GET", `/api/dashboard/${id}`);
  const cards = dashcardsOf(dash);
  return {
    id: dash.id,
    name: dash.name,
    description: dash.description ?? null,
    collection_id: dash.collection_id ?? null,
    url: dashboardUrl(dash.id),
    dashcards: cards.map((c) => ({
      id: c.id,
      card_id: c.card_id ?? c.card?.id ?? null,
      card_name: c.card?.name,
      row: c.row,
      col: c.col,
      size_x: c.size_x,
      size_y: c.size_y,
    })),
  };
}

export type DashcardAdd = {
  card_id: number;
  row?: number;
  col?: number;
  size_x?: number;
  size_y?: number;
};

/**
 * Append question cards onto a dashboard. New dashcards use negative temporary ids
 * (Metabase API convention). Existing cards are preserved.
 */
export async function updateDashboardCards(params: {
  dashboard_id: number;
  add: DashcardAdd[];
}): Promise<{
  id: number;
  name: string;
  url: string;
  dashcards: Awaited<ReturnType<typeof getDashboard>>["dashcards"];
}> {
  if (!params.add.length) {
    throw new Error("add must contain at least one card");
  }
  const dash = await mbFetch<Dashboard>("GET", `/api/dashboard/${params.dashboard_id}`);
  const existing = dashcardsOf(dash);
  let nextRow =
    existing.reduce((max, c) => Math.max(max, c.row + c.size_y), 0) || 0;
  let tempId = -1;
  const appended: Dashcard[] = params.add.map((a) => {
    const size_x = a.size_x ?? 12;
    const size_y = a.size_y ?? 6;
    const row = a.row ?? nextRow;
    const col = a.col ?? 0;
    if (a.row == null) nextRow = row + size_y;
    return {
      id: tempId--,
      card_id: a.card_id,
      row,
      col,
      size_x,
      size_y,
      visualization_settings: {},
      parameter_mappings: [],
    };
  });
  const merged = [...existing, ...appended];
  await mbFetch("PUT", `/api/dashboard/${params.dashboard_id}`, {
    name: dash.name,
    description: dash.description ?? null,
    parameters: dash.parameters ?? [],
    dashcards: merged.map((c) => ({
      id: c.id,
      card_id: c.card_id ?? c.card?.id ?? null,
      row: c.row,
      col: c.col,
      size_x: c.size_x,
      size_y: c.size_y,
      visualization_settings: c.visualization_settings ?? {},
      parameter_mappings: c.parameter_mappings ?? [],
    })),
  });
  const updated = await getDashboard(params.dashboard_id);
  return {
    id: updated.id,
    name: updated.name,
    url: updated.url,
    dashcards: updated.dashcards,
  };
}

export { DISPLAY_TYPES, DEFAULT_COLLECTION };
