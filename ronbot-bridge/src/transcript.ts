import { config } from "./env.js";

export interface TranscriptEntry {
  at: number;
  senderId: string;
  body: string;
  fromMe: boolean;
  messageId?: string;
}

export class TranscriptStore {
  private readonly byChat = new Map<string, TranscriptEntry[]>();
  private readonly lastBotReplyAt = new Map<string, number>();
  private readonly lastBotMessageIds = new Map<string, Set<string>>();

  append(chatId: string, entry: TranscriptEntry): void {
    const list = this.byChat.get(chatId) ?? [];
    list.push(entry);
    while (list.length > config.transcriptLimit) list.shift();
    this.byChat.set(chatId, list);
    if (entry.fromMe) {
      this.lastBotReplyAt.set(chatId, entry.at);
      const ids = this.lastBotMessageIds.get(chatId) ?? new Set<string>();
      if (entry.messageId) ids.add(entry.messageId);
      while (ids.size > 20) {
        const first = ids.values().next().value as string | undefined;
        if (first) ids.delete(first);
        else break;
      }
      this.lastBotMessageIds.set(chatId, ids);
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

  lastBotReplyAgeMs(chatId: string): number | null {
    const at = this.lastBotReplyAt.get(chatId);
    if (at == null) return null;
    return Date.now() - at;
  }

  isReplyToBot(chatId: string, replyToId?: string): boolean {
    if (!replyToId) return false;
    return this.lastBotMessageIds.get(chatId)?.has(replyToId) ?? false;
  }

  markBotReply(chatId: string, messageId?: string): void {
    this.lastBotReplyAt.set(chatId, Date.now());
    if (messageId) {
      const ids = this.lastBotMessageIds.get(chatId) ?? new Set<string>();
      ids.add(messageId);
      this.lastBotMessageIds.set(chatId, ids);
    }
  }
}
