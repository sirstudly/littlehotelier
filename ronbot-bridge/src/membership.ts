import { config, isAllowlistedGroup, normalizeJid } from "./env.js";
import { extractParticipantIds, type WahaClient } from "./waha/client.js";
import type { WahaWebhookEvent } from "./waha/types.js";

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Union of participants across allowlisted groups.
 * Used to authorize 1:1 DMs.
 * Stores both `@lid` and phone (`@c.us`) identities when WAHA provides them.
 */
export class MembershipCache {
  private members = new Set<string>();
  private lastRefreshMs = 0;
  private refreshing: Promise<void> | null = null;

  constructor(private readonly waha: WahaClient) {}

  isAuthorizedDmSender(senderId: string): boolean {
    return this.members.has(normalizeJid(senderId));
  }

  size(): number {
    return this.members.size;
  }

  async refresh(force = false): Promise<void> {
    if (this.refreshing) return this.refreshing;
    const due =
      force || Date.now() - this.lastRefreshMs > config.membershipRefreshMs;
    if (!due && this.members.size > 0) return;

    this.refreshing = this.doRefreshWithRetries(force)
      .catch((err) => {
        console.error("membership refresh failed", err);
      })
      .finally(() => {
        this.refreshing = null;
      });
    return this.refreshing;
  }

  /** Startup / force: retry while WAHA session may still be warming up (422 / ECONNREFUSED). */
  private async doRefreshWithRetries(force: boolean): Promise<void> {
    const attempts = force ? 5 : 1;
    for (let i = 0; i < attempts; i++) {
      await this.doRefresh();
      if (this.members.size > 0 || config.groups.length === 0) return;
      if (i < attempts - 1) {
        const waitMs = 2000 * (i + 1);
        console.warn(
          `membership empty after attempt ${i + 1}/${attempts}; retrying in ${waitMs}ms`,
        );
        await sleep(waitMs);
      }
    }
  }

  private async doRefresh(): Promise<void> {
    const next = new Set<string>();
    for (const groupId of config.groups) {
      if (!isAllowlistedGroup(groupId)) continue;
      try {
        const ids = await this.waha.getGroupParticipants(groupId);
        for (const id of ids) next.add(normalizeJid(id));
      } catch (err) {
        console.warn(`participants fetch failed for ${groupId}`, err);
      }
    }
    this.members = next;
    this.lastRefreshMs = Date.now();
    console.log(
      `membership cache: ${this.members.size} unique identities across ${config.groups.length} groups`,
    );
  }

  /** Apply incremental group.v2.participants webhook. */
  applyParticipantEvent(event: WahaWebhookEvent): void {
    if (event.event !== "group.v2.participants") return;
    const p = event.payload;
    const groupId = normalizeJid(String(p?.group?.id ?? p?.from ?? ""));
    if (!groupId.endsWith("@g.us") || !isAllowlistedGroup(groupId)) return;

    const type = String(p?.type ?? "").toLowerCase();
    const participants = extractParticipantIds(p?.participants ?? []).map(normalizeJid);

    if (type.includes("leave") || type.includes("remove")) {
      // Safer to full-refresh — member may still be in another allowlisted group
      void this.refresh(true);
      return;
    }
    if (type.includes("join") || type.includes("add") || !type) {
      for (const id of participants) this.members.add(id);
    }
  }
}
