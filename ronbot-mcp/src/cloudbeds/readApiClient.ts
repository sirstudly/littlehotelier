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

export async function health(): Promise<unknown> {
  return request("/ronbot/health");
}
