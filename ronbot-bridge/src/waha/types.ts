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
  me?: { id?: string; pushName?: string };
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
    replyToId: p.replyTo?.id,
    replyToBody: p.replyTo?.body,
  };
}
