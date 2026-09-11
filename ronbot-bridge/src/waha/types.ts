export const ALLOWED_IMAGE_MIME = new Set([
  "image/jpeg",
  "image/jpg",
  "image/png",
  "image/webp",
  "image/gif",
]);

export interface WahaMediaInfo {
  url?: string;
  mimetype?: string;
  filename?: string | null;
  error?: string | null;
}

export interface WahaMessagePayload {
  id?: string;
  timestamp?: number;
  from?: string;
  to?: string;
  participant?: string;
  fromMe?: boolean;
  body?: string | null;
  hasMedia?: boolean;
  media?: WahaMediaInfo;
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

export interface NormalizedMedia {
  url: string;
  mimetype: string;
  filename?: string;
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
  hasMedia: boolean;
  /** Populated only for allowed image types with a downloadable URL. */
  media?: NormalizedMedia;
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
  const media = extractAllowedImageMedia(p);

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
    hasMedia: Boolean(media),
    media,
    replyToId: p.replyTo?.id,
    replyToBody: p.replyTo?.body,
  };
}

/** Keep only downloadable image attachments we can send to the Cursor SDK. */
export function extractAllowedImageMedia(
  payload: WahaMessagePayload,
): NormalizedMedia | undefined {
  if (!payload.hasMedia || !payload.media) return undefined;
  const url = payload.media.url?.trim();
  if (!url) return undefined;
  if (payload.media.error) return undefined;

  let mimetype = (payload.media.mimetype ?? "").trim().toLowerCase();
  if (mimetype === "image/jpg") mimetype = "image/jpeg";
  if (!mimetype) {
    // Infer from URL extension when WAHA omits mimetype
    const lower = url.toLowerCase();
    if (lower.includes(".png")) mimetype = "image/png";
    else if (lower.includes(".webp")) mimetype = "image/webp";
    else if (lower.includes(".gif")) mimetype = "image/gif";
    else if (lower.includes(".jpg") || lower.includes(".jpeg")) mimetype = "image/jpeg";
  }
  if (!ALLOWED_IMAGE_MIME.has(mimetype)) return undefined;

  const filename = payload.media.filename?.trim() || undefined;
  return { url, mimetype, filename };
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
