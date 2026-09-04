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

export async function getBooking(params: {
  property: string;
  reservationId?: string;
  bookingReference?: string;
}): Promise<unknown> {
  const { property, reservationId, bookingReference } = params;
  if (reservationId) {
    return request(`/ronbot/${property}/reservations/${encodeURIComponent(reservationId)}`);
  }
  if (bookingReference) {
    const q = new URLSearchParams({ booking_reference: bookingReference });
    return request(`/ronbot/${property}/reservations?${q}`);
  }
  throw new Error("reservation_id or booking_reference is required");
}

export async function listTransactions(property: string, reservationId: string): Promise<unknown> {
  return request(
    `/ronbot/${property}/reservations/${encodeURIComponent(reservationId)}/transactions`,
  );
}

export async function getBookingTimeline(
  property: string,
  reservationId: string,
): Promise<unknown> {
  return request(
    `/ronbot/${property}/reservations/${encodeURIComponent(reservationId)}/timeline`,
  );
}

export async function health(): Promise<unknown> {
  return request("/ronbot/health");
}
