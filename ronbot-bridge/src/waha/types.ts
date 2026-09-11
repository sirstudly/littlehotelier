export interface WahaMessagePayload {
  id?: string;
  timestamp?: number;
  from?: string;
  to?: string;
  participant?: string;
  fromMe?: boolean;
  body?: string | null;
  hasMedia?: boolean;
  replyTo?: { id?: string; participant?: string; body?: string };
  /** WAHA sometimes surfaces mentions at the top level. */
  mentionedIds?: string[];
  mentionIds?: string[];
  _data?: unknown;
}

export interface WahaWebhookEvent {
  event: string;
  session?: string;
  payload?: WahaMessagePayload & {
    group?: { id?: string };
    type?: string;
    participants?: Array<{ id?: string; jid?: string } | string>;
  };
  me?: { id?: string; lid?: string; pushName?: string };
  engine?: string;
}

export interface NormalizedInbound {
  event: string;
  messageId: string;
  chatId: string;
  senderId: string;
  body: string;
  fromMe: boolean;
  isGroup: boolean;
  timestampMs: number;
  /** Explicit @-mentions from WhatsApp (often the bot LID, not the push name). */
  mentionedIds: string[];
  replyToId?: string;
  replyToBody?: string;
}

export function normalizeInbound(event: WahaWebhookEvent): NormalizedInbound | null {
  if (event.event !== "message") return null;
  const p = event.payload;
  if (!p?.from) return null;

  const from = String(p.from);
  const isGroup = from.endsWith("@g.us");
  const chatId = from;
  const senderId = isGroup
    ? String(p.participant || p.from)
    : from;
  const body = (p.body ?? "").toString().trim();
  const ts = p.timestamp ?? Math.floor(Date.now() / 1000);
  const timestampMs = ts > 1e12 ? ts : ts * 1000;

  return {
    event: event.event,
    messageId: String(p.id ?? `${chatId}:${timestampMs}`),
    chatId,
    senderId,
    body,
    fromMe: Boolean(p.fromMe),
    isGroup,
    timestampMs,
    mentionedIds: extractMentionedIds(p),
    replyToId: p.replyTo?.id,
    replyToBody: p.replyTo?.body,
  };
}

/** Collect mentioned JIDs from WAHA / Baileys payload shapes. */
export function extractMentionedIds(payload: WahaMessagePayload): string[] {
  const found: string[] = [];
  const push = (v: unknown) => {
    if (typeof v === "string" && v.trim()) found.push(v.trim());
  };

  for (const list of [payload.mentionedIds, payload.mentionIds]) {
    if (Array.isArray(list)) list.forEach(push);
  }

  const data = payload._data;
  if (data && typeof data === "object") {
    const root = data as Record<string, unknown>;
    const message = (root.message ?? root) as Record<string, unknown>;
    for (const key of ["extendedTextMessage", "imageMessage", "videoMessage", "documentMessage"]) {
      const part = message[key];
      if (!part || typeof part !== "object") continue;
      const ctx = (part as Record<string, unknown>).contextInfo;
      if (!ctx || typeof ctx !== "object") continue;
      const mentioned = (ctx as Record<string, unknown>).mentionedJid;
      if (Array.isArray(mentioned)) mentioned.forEach(push);
    }
  }

  return [...new Set(found)];
}
