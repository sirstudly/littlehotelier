const baseUrl = (process.env.RONBOT_READ_API_URL ?? "http://ronbot-read-api:8080").replace(
  /\/$/,
  "",
);
const token = process.env.RONBOT_TOKEN ?? "";

async function request<T>(path: string): Promise<T> {
  const headers: Record<string, string> = {
    Accept: "application/json",
  };
  if (token) {
    headers["X-Ronbot-Token"] = token;
  }
  const res = await fetch(`${baseUrl}${path}`, { headers });
  const text = await res.text();
  let body: unknown;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = { error: text };
  }
  if (!res.ok) {
    const errMsg =
      typeof body === "object" && body && "error" in body
        ? String((body as { error: unknown }).error)
        : `HTTP ${res.status}`;
    throw new Error(errMsg);
  }
  return body as T;
}

/**
 * Search live Cloudbeds bookings. `query` may be a visible reservation id,
 * third-party/OTA reference, guest name, or internal Cloudbeds id.
 * Returns a list of matching booking summaries (exact id matches preferred).
 */
export async function getBooking(params: {
  property: string;
  query: string;
}): Promise<unknown> {
  const { property, query } = params;
  if (!query?.trim()) {
    throw new Error("query is required");
  }
  const q = new URLSearchParams({ query: query.trim() });
  return request(`/ronbot/${property}/reservations?${q}`);
}

export type ReservationSearchParams = {
  property: string;
  query?: string;
  stayFrom?: string;
  stayTo?: string;
  checkinFrom?: string;
  checkinTo?: string;
  checkoutFrom?: string;
  checkoutTo?: string;
  bookedFrom?: string;
  bookedTo?: string;
  statuses?: string;
  sources?: string;
  limit: number;
};

/**
 * Cloudbeds reservation list (up to `limit` rows; `truncated` when more matched) for free text
 * and/or inclusive date ranges, statuses and OTA source names. Requires query or at least one date range.
 */
export async function searchReservations(params: ReservationSearchParams): Promise<unknown> {
  const { property, limit, ...criteria } = params;
  const q = new URLSearchParams({ limit: String(limit) });
  for (const [key, value] of Object.entries(criteria)) {
    if (value?.trim()) q.set(key, value.trim());
  }
  return request(`/ronbot/${property}/reservation-search?${q}`);
}

/**
 * Folio transactions. `query` must resolve to a unique reservation
 * (prefer the internal reservationId from a prior get_booking result).
 */
export async function listTransactions(property: string, query: string): Promise<unknown> {
  return request(
    `/ronbot/${property}/reservations/${encodeURIComponent(query)}/transactions`,
  );
}

/**
 * Booking + folio + job history. `query` must resolve uniquely (same as listTransactions).
 */
export async function getBookingTimeline(property: string, query: string): Promise<unknown> {
  return request(
    `/ronbot/${property}/reservations/${encodeURIComponent(query)}/timeline`,
  );
}

/**
 * Whether the guest is staying on past asOf (same reservation extended, or a linked
 * follow-on booking in the same beds). `query` must resolve uniquely.
 * Omit asOf for today (Europe/London).
 */
export async function getStayContinuation(params: {
  property: string;
  query: string;
  asOf?: string;
}): Promise<unknown> {
  const { property, query, asOf } = params;
  if (!query?.trim()) {
    throw new Error("query is required");
  }
  const q = new URLSearchParams();
  if (asOf?.trim()) q.set("asOf", asOf.trim());
  const qs = q.toString();
  return request(
    `/ronbot/${property}/reservations/${encodeURIComponent(query.trim())}/stay-continuation${qs ? `?${qs}` : ""}`,
  );
}

/**
 * Live sellable availability by room type for an inclusive date range.
 * Omit from/to to use read-api defaults (today → tomorrow, Europe/London).
 */
export async function getAvailability(params: {
  property: string;
  from?: string;
  to?: string;
}): Promise<unknown> {
  const { property, from, to } = params;
  const q = new URLSearchParams();
  if (from?.trim()) q.set("from", from.trim());
  if (to?.trim()) q.set("to", to.trim());
  const qs = q.toString();
  return request(`/ronbot/${property}/availability${qs ? `?${qs}` : ""}`);
}

/**
 * Live Channel Production totals by source for one stay-date month (YYYY-MM).
 */
export async function getChannelProduction(params: {
  property: string;
  month: string;
}): Promise<unknown> {
  const { property, month } = params;
  const q = new URLSearchParams({ month: month.trim() });
  return request(`/ronbot/${property}/channel-production?${q}`);
}

/**
 * Beds-occupied rate over an inclusive stay-date range (YYYY-MM-DD), with a monthly breakdown.
 */
export async function getOccupancy(params: {
  property: string;
  from: string;
  to: string;
}): Promise<unknown> {
  const { property, from, to } = params;
  const q = new URLSearchParams({ from: from.trim(), to: to.trim() });
  return request(`/ronbot/${property}/occupancy?${q}`);
}

export async function health(): Promise<unknown> {
  return request("/ronbot/health");
}
