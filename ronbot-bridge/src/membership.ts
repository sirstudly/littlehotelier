import {
  config,
  getGroupProperty,
  isAllowlistedGroup,
  normalizeJid,
  type PropertyId,
  PROPERTY_IDS,
} from "./env.js";
import { extractParticipantIdentityGroups, type WahaClient } from "./waha/client.js";
import type { WahaWebhookEvent } from "./waha/types.js";

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const PROPERTY_ORDER = new Map(
  PROPERTY_IDS.map((id, index) => [id, index] as const),
);

/** Record that all `jids` belong to one participant. */
function linkIdentities(links: Map<string, Set<string>>, jids: string[]): void {
  if (jids.length < 2) return;
  for (const jid of jids) {
    let set = links.get(jid);
    if (!set) {
      set = new Set();
      links.set(jid, set);
    }
    for (const other of jids) {
      if (other !== jid) set.add(other);
    }
  }
}

/**
 * Union of participants across allowlisted groups.
 * Used to authorize 1:1 DMs, and to resolve default/candidate properties
 * from tagged groups the sender belongs to.
 * Stores both `@lid` and phone (`@c.us`) identities when WAHA provides them.
 */
export class MembershipCache {
  private members = new Set<string>();
  /** senderJid → properties from tagged groups they belong to */
  private memberProperties = new Map<string, Set<PropertyId>>();
  /** jid → other identities of the same participant (LID ↔ phone) */
  private linkedIds = new Map<string, Set<string>>();
  private lastRefreshMs = 0;
  private refreshing: Promise<void> | null = null;

  constructor(private readonly waha: WahaClient) {}

  isAuthorizedDmSender(senderId: string): boolean {
    return this.members.has(normalizeJid(senderId));
  }

  /** Deduped property codes for a sender, in stable crh/hsh/rmb/lsh order. */
  getSenderProperties(senderId: string): PropertyId[] {
    const set = this.memberProperties.get(normalizeJid(senderId));
    if (!set || set.size === 0) return [];
    return [...set].sort(
      (a, b) => (PROPERTY_ORDER.get(a) ?? 99) - (PROPERTY_ORDER.get(b) ?? 99),
    );
  }

  /** Other identities (phone / LID) seen for the same participant; excludes `senderId`. */
  getLinkedIds(senderId: string): string[] {
    return [...(this.linkedIds.get(normalizeJid(senderId)) ?? [])];
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
    const nextProps = new Map<string, Set<PropertyId>>();
    const nextLinks = new Map<string, Set<string>>();

    for (const group of config.groups) {
      if (!isAllowlistedGroup(group.id)) continue;
      try {
        const participants = await this.waha.getGroupParticipants(group.id);
        for (const identities of participants) {
          const jids = identities.map(normalizeJid);
          linkIdentities(nextLinks, jids);
          for (const jid of jids) {
            next.add(jid);
            if (group.property) {
              let set = nextProps.get(jid);
              if (!set) {
                set = new Set();
                nextProps.set(jid, set);
              }
              set.add(group.property);
            }
          }
        }
      } catch (err) {
        console.warn(`participants fetch failed for ${group.id}`, err);
      }
    }
    this.members = next;
    this.memberProperties = nextProps;
    this.linkedIds = nextLinks;
    this.lastRefreshMs = Date.now();
    console.log(
      `membership cache: ${this.members.size} unique identities across ${config.groups.length} groups`,
    );
  }

  private addMemberProperty(senderId: string, property: PropertyId): void {
    const jid = normalizeJid(senderId);
    let set = this.memberProperties.get(jid);
    if (!set) {
      set = new Set();
      this.memberProperties.set(jid, set);
    }
    set.add(property);
  }

  /** Apply incremental group.v2.participants webhook. */
  applyParticipantEvent(event: WahaWebhookEvent): void {
    if (event.event !== "group.v2.participants") return;
    const p = event.payload;
    const groupId = normalizeJid(String(p?.group?.id ?? p?.from ?? ""));
    if (!groupId.endsWith("@g.us") || !isAllowlistedGroup(groupId)) return;

    const type = String(p?.type ?? "").toLowerCase();
    const identityGroups = extractParticipantIdentityGroups(p?.participants ?? []).map((ids) =>
      ids.map(normalizeJid),
    );
    const participants = identityGroups.flat();
    const property = getGroupProperty(groupId);

    if (type.includes("leave") || type.includes("remove")) {
      // Safer to full-refresh — member may still be in another allowlisted group
      void this.refresh(true);
      return;
    }
    if (type.includes("join") || type.includes("add") || !type) {
      for (const jids of identityGroups) linkIdentities(this.linkedIds, jids);
      for (const id of participants) {
        this.members.add(id);
        if (property) this.addMemberProperty(id, property);
      }
    }
  }
}
