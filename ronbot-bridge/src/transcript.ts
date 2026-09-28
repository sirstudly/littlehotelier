import { config, normalizeJid } from "./env.js";
import { normalizeMessagePayload, type WahaMessagePayload } from "./waha/types.js";

export interface TranscriptEntry {
  at: number;
  senderId: string;
  body: string;
  fromMe: boolean;
  messageId?: string;
}

const EXCHANGE_TEXT_MAX = 300;

/** WAHA history item → transcript entry (bot's own messages become `ronbot`). */
export function transcriptEntryFromPayload(p: WahaMessagePayload): TranscriptEntry | null {
  const msg = normalizeMessagePayload(p);
  if (!msg) return null;
  const body = msg.body || (p.hasMedia ? "[image]" : "");
  if (!body) return null;
  return {
    at: msg.timestampMs,
    senderId: msg.fromMe ? "ronbot" : normalizeJid(msg.senderId),
    body,
    fromMe: msg.fromMe,
    messageId: p.id ? String(p.id) : undefined,
  };
}

function truncate(text: string, max = EXCHANGE_TEXT_MAX): string {
  const oneLine = text.replace(/\s+/g, " ").trim();
  return oneLine.length <= max ? oneLine : `${oneLine.slice(0, max - 1)}…`;
}

export class TranscriptStore {
  private readonly byChat = new Map<string, TranscriptEntry[]>();
  private readonly lastBotReplyAt = new Map<string, number>();
  private readonly lastBotMessageIds = new Map<string, Set<string>>();
  private readonly lastHumanTriggerByChat = new Map<string, string>();
  /** Chats whose WhatsApp history has been loaded since startup → number of entries it added. */
  private readonly seeded = new Map<string, number>();

  append(chatId: string, entry: TranscriptEntry): void {
    const list = this.byChat.get(chatId) ?? [];
    if (entry.messageId && list.some((e) => e.messageId === entry.messageId)) return;
    list.push(entry);
    while (list.length > config.transcriptLimit) list.shift();
    this.byChat.set(chatId, list);
    if (entry.fromMe) {
      this.lastBotReplyAt.set(chatId, entry.at);
      this.rememberBotMessageId(chatId, entry.messageId);
    }
  }

  isSeeded(chatId: string): boolean {
    return this.seeded.has(chatId);
  }

  /** True when history from before this process started was loaded for the chat. */
  hasBackfilledHistory(chatId: string): boolean {
    return (this.seeded.get(chatId) ?? 0) > 0;
  }

  /**
   * Merge history loaded from WhatsApp (deduped by messageId, time-ordered, trimmed).
   * Restores bot reply ids / follow-up state so replies to pre-restart answers still trigger.
   */
  seed(chatId: string, entries: TranscriptEntry[]): void {
    const existing = this.byChat.get(chatId) ?? [];
    const seen = new Set(existing.map((e) => e.messageId).filter(Boolean));
    const added = entries.filter((e) => !e.messageId || !seen.has(e.messageId));
    const addedIds = new Set<string>();
    const fresh = added.filter((e) => {
      if (!e.messageId) return true;
      if (addedIds.has(e.messageId)) return false;
      addedIds.add(e.messageId);
      return true;
    });
    const merged = [...existing, ...fresh].sort((a, b) => a.at - b.at);
    while (merged.length > config.transcriptLimit) merged.shift();
    this.byChat.set(chatId, merged);
    this.seeded.set(chatId, fresh.length);

    let lastBotIdx = -1;
    for (let i = 0; i < merged.length; i++) {
      if (!merged[i]!.fromMe) continue;
      this.rememberBotMessageId(chatId, merged[i]!.messageId);
      lastBotIdx = i;
    }
    if (lastBotIdx < 0) return;
    const lastBotAt = merged[lastBotIdx]!.at;
    if ((this.lastBotReplyAt.get(chatId) ?? 0) < lastBotAt) {
      this.lastBotReplyAt.set(chatId, lastBotAt);
    }
    if (!this.lastHumanTriggerByChat.has(chatId)) {
      const human = merged.slice(0, lastBotIdx).reverse().find((e) => !e.fromMe);
      if (human) this.lastHumanTriggerByChat.set(chatId, normalizeJid(human.senderId));
    }
  }

  recent(chatId: string): TranscriptEntry[] {
    return [...(this.byChat.get(chatId) ?? [])];
  }

  formatForPrompt(chatId: string): string {
    const lines = this.recent(chatId).map((e) => {
      const who = e.fromMe ? "ronbot" : e.senderId;
      return `[${new Date(e.at).toISOString()}] ${who}: ${e.body}`;
    });
    return lines.join("\n");
  }

  /**
   * Last `maxPairs` ronbot answers, each with the human message that preceded it.
   * Consecutive ronbot messages (chunked replies) count as one answer.
   */
  formatRonbotExchanges(chatId: string, maxPairs = config.exchangePairs): string {
    const entries = this.recent(chatId);
    const pairs: { question?: TranscriptEntry; answer: string; at: number }[] = [];
    let lastHuman: TranscriptEntry | undefined;
    for (let i = 0; i < entries.length; i++) {
      const e = entries[i]!;
      if (!e.fromMe) {
        lastHuman = e;
        continue;
      }
      const prev = entries[i - 1];
      if (prev?.fromMe && pairs.length > 0) {
        pairs[pairs.length - 1]!.answer += ` ${e.body}`;
        continue;
      }
      pairs.push({ question: lastHuman, answer: e.body, at: e.at });
      lastHuman = undefined;
    }
    return pairs
      .slice(-maxPairs)
      .map((p) => {
        const q = p.question
          ? `${p.question.senderId}: ${truncate(p.question.body)}`
          : "(no preceding question in transcript)";
        return `- [${new Date(p.at).toISOString()}] Q ${q}\n  ronbot: ${truncate(p.answer)}`;
      })
      .join("\n");
  }

  lastBotReplyAgeMs(chatId: string): number | null {
    const at = this.lastBotReplyAt.get(chatId);
    if (at == null) return null;
    return Date.now() - at;
  }

  lastHumanTriggerId(chatId: string): string | null {
    return this.lastHumanTriggerByChat.get(chatId) ?? null;
  }

  isReplyToBot(chatId: string, replyToId?: string): boolean {
    if (!replyToId) return false;
    return this.lastBotMessageIds.get(chatId)?.has(replyToId) ?? false;
  }

  markBotReply(chatId: string, messageId?: string, humanSenderId?: string): void {
    this.lastBotReplyAt.set(chatId, Date.now());
    this.rememberBotMessageId(chatId, messageId);
    if (humanSenderId) {
      this.lastHumanTriggerByChat.set(chatId, normalizeJid(humanSenderId));
    }
  }

  private rememberBotMessageId(chatId: string, messageId?: string): void {
    const ids = this.lastBotMessageIds.get(chatId) ?? new Set<string>();
    if (messageId) ids.add(messageId);
    while (ids.size > 20) {
      const first = ids.values().next().value as string | undefined;
      if (first) ids.delete(first);
      else break;
    }
    this.lastBotMessageIds.set(chatId, ids);
  }
}
