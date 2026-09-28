import { config } from "../env.js";
import type { WahaClient } from "./client.js";

export interface SessionStatus {
  status: string;
  detail?: string;
}

export interface AlertSender {
  (subject: string, htmlBody: string): Promise<void>;
}

/**
 * Emails once when the WAHA session has been unhealthy for `threshold` consecutive checks,
 * and once more when it recovers. Transient WAHA reconnects (STARTING) usually clear
 * within one poll, so a single bad reading never alerts.
 */
export class SessionMonitor {
  private consecutiveBad = 0;
  private alerted = false;
  private downSince: Date | undefined;

  constructor(
    private readonly getStatus: () => Promise<SessionStatus>,
    private readonly sendAlert: AlertSender,
    private readonly threshold = config.sessionAlertThreshold,
    private readonly session = config.wahaSession,
  ) {}

  async check(): Promise<void> {
    let current: SessionStatus;
    try {
      current = await this.getStatus();
    } catch (err) {
      current = { status: "UNREACHABLE", detail: (err as Error).message };
    }

    if (current.status === "WORKING") {
      if (this.alerted) {
        await this.trySend(
          `WhatsApp (ronbot) session reconnected`,
          `<p>WAHA session <b>${escapeHtml(this.session)}</b> is <b>WORKING</b> again` +
            (this.downSince ? ` (down since ${formatLondon(this.downSince)})` : "") +
            `.</p>`,
        );
      }
      this.consecutiveBad = 0;
      this.alerted = false;
      this.downSince = undefined;
      return;
    }

    this.consecutiveBad++;
    this.downSince ??= new Date();
    console.warn(
      `WAHA session ${this.session} status=${current.status} (${this.consecutiveBad}/${this.threshold})` +
        (current.detail ? `: ${current.detail}` : ""),
    );
    if (this.alerted || this.consecutiveBad < this.threshold) return;

    const sent = await this.trySend(
      `WhatsApp (ronbot) session is ${current.status}`,
      [
        `<p>WAHA session <b>${escapeHtml(this.session)}</b> has been <b>${escapeHtml(current.status)}</b> since ${formatLondon(this.downSince)}. Ronbot cannot read or reply to WhatsApp until it is fixed.</p>`,
        current.detail ? `<p>Detail: ${escapeHtml(current.detail)}</p>` : "",
        `<p>To fix: open the WAHA dashboard (botpressvm port 3001), stop/logout and start the session, then scan the QR code from the burner phone (WhatsApp &rarr; Settings &rarr; Linked devices &rarr; Link a device).</p>`,
        `<p>If the logs show <code>conflict / device_removed</code>, WhatsApp unlinked the device &mdash; check the burner phone is powered on and online.</p>`,
      ].join("\n"),
    );
    this.alerted = sent;
  }

  private async trySend(subject: string, htmlBody: string): Promise<boolean> {
    try {
      await this.sendAlert(subject, htmlBody);
      console.log(`session alert sent: ${subject}`);
      return true;
    } catch (err) {
      console.error(`session alert failed: ${subject}`, err);
      return false;
    }
  }
}

/** Send an alert email via ronbot-read-api using the configured property's Gmail account. */
export async function sendAlertEmail(subject: string, htmlBody: string): Promise<void> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (config.ronbotToken) headers["X-Ronbot-Token"] = config.ronbotToken;
  const res = await fetch(
    `${config.ronbotReadApiUrl}/ronbot/${encodeURIComponent(config.alertProperty)}/alerts/email`,
    {
      method: "POST",
      headers,
      body: JSON.stringify({ to: config.alertEmailTo || undefined, subject, body: htmlBody }),
    },
  );
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`read-api alert email ${res.status}: ${body.slice(0, 300)}`);
  }
}

export function startSessionMonitor(waha: WahaClient): void {
  if (config.sessionCheckMs <= 0) {
    console.log("WAHA session monitor disabled (RONBOT_SESSION_CHECK_MS<=0)");
    return;
  }
  const monitor = new SessionMonitor(() => waha.getSessionStatus(), sendAlertEmail);
  console.log(
    `WAHA session monitor: every ${Math.round(config.sessionCheckMs / 1000)}s, alert after ${config.sessionAlertThreshold} bad checks via ${config.alertProperty} Gmail to ${config.alertEmailTo || "self"}`,
  );
  setInterval(() => void monitor.check(), config.sessionCheckMs);
}

function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function formatLondon(d: Date): string {
  return d.toLocaleString("en-GB", { timeZone: "Europe/London" });
}
