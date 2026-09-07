import { config, normalizeJid } from "../env.js";

export class WahaClient {
  constructor(
    private readonly baseUrl = config.wahaBaseUrl,
    private readonly apiKey = config.wahaApiKey,
    private readonly session = config.wahaSession,
  ) {}

  private headers(): HeadersInit {
    const h: Record<string, string> = { "Content-Type": "application/json" };
    if (this.apiKey) h["X-Api-Key"] = this.apiKey;
    return h;
  }

  /** Returns WAHA message id when the API includes one (for reply-to follow-ups). */
  async sendText(chatId: string, text: string): Promise<string | undefined> {
    const res = await fetch(`${this.baseUrl}/api/sendText`, {
      method: "POST",
      headers: this.headers(),
      body: JSON.stringify({
        session: this.session,
        chatId: normalizeJid(chatId),
        text,
      }),
    });
    if (!res.ok) {
      const body = await res.text().catch(() => "");
      throw new Error(`WAHA sendText ${res.status}: ${body.slice(0, 500)}`);
    }
    try {
      const data = (await res.json()) as { id?: string; key?: { id?: string } };
      return data.id ?? data.key?.id;
    } catch {
      return undefined;
    }
  }

  async getGroupParticipants(groupId: string): Promise<string[]> {
    const id = encodeURIComponent(normalizeJid(groupId));
    const session = encodeURIComponent(this.session);
    const urls = [
      `${this.baseUrl}/api/${session}/groups/${id}/participants/v2`,
      `${this.baseUrl}/api/${session}/groups/${id}/participants`,
      // Fallback: full group object often embeds participants
      `${this.baseUrl}/api/${session}/groups/${id}`,
    ];
    let lastErr: Error | undefined;
    for (const url of urls) {
      try {
        const res = await fetch(url, { headers: this.headers() });
        if (!res.ok) {
          const body = await res.text().catch(() => "");
          lastErr = new Error(
            `WAHA participants ${res.status} for ${groupId}: ${body.slice(0, 200)}`,
          );
          continue;
        }
        const data = (await res.json()) as unknown;
        const ids = [...new Set(extractParticipantIds(data).map(normalizeJid))];
        if (ids.length > 0) return ids;
        lastErr = new Error(`WAHA participants empty for ${groupId} via ${url}`);
      } catch (err) {
        lastErr = err instanceof Error ? err : new Error(String(err));
      }
    }
    throw lastErr ?? new Error(`Failed to load participants for ${groupId}`);
  }
}

/**
 * Collect every usable identity for a participant.
 * Modern WAHA/NOWEB often returns `@lid` as `id` and the phone as `pn` / `phoneNumber`.
 * DM ACL needs both so senders match whether webhook uses LID or @c.us.
 */
export function extractParticipantIds(data: unknown): string[] {
  if (Array.isArray(data)) {
    return data.flatMap((item) => extractOneParticipant(item));
  }
  if (data && typeof data === "object") {
    const o = data as Record<string, unknown>;
    if (Array.isArray(o.participants)) return extractParticipantIds(o.participants);
    if (Array.isArray(o.data)) return extractParticipantIds(o.data);
  }
  return [];
}

function extractOneParticipant(item: unknown): string[] {
  if (typeof item === "string") return [item];
  if (!item || typeof item !== "object") return [];
  const o = item as Record<string, unknown>;
  const ids: string[] = [];
  for (const key of ["id", "jid", "participant", "pn", "phoneNumber", "phone"]) {
    const v = o[key];
    if (typeof v === "string" && v.trim()) ids.push(v.trim());
  }
  return ids;
}
