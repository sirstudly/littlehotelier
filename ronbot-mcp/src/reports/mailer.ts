import nodemailer, { type Transporter } from "nodemailer";
import { isValidEmailAddress } from "../config/emailAliases.js";
import { getSecret } from "../config/secrets.js";

const XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

export interface ReportEmail {
  to: string[];
  subject: string;
  /** Plain-text body. */
  body: string;
  filename: string;
  attachment: Buffer;
}

let transporterPromise: Promise<{ transporter: Transporter; from: string }> | null = null;

async function createTransporter(): Promise<{ transporter: Transporter; from: string }> {
  const [user, pass] = await Promise.all([
    getSecret("ronbot_gmail_user"),
    getSecret("ronbot_gmail_app_password"),
  ]);
  const transporter = nodemailer.createTransport({
    host: "smtp.gmail.com",
    port: 465,
    secure: true,
    auth: { user, pass: pass.replace(/\s+/g, "") },
  });
  const fromName = process.env.RONBOT_REPORT_FROM_NAME?.trim() || "Ronbot";
  // Must be a "Send mail as" address on the signed-in account, or Gmail rewrites it to the account address.
  const fromAddress = process.env.RONBOT_REPORT_FROM_ADDRESS?.trim() || user;
  if (!isValidEmailAddress(fromAddress)) {
    throw new Error(`RONBOT_REPORT_FROM_ADDRESS '${fromAddress}' is not a valid email address`);
  }
  return { transporter, from: `"${fromName.replace(/"/g, "")}" <${fromAddress}>` };
}

function getTransporter(): Promise<{ transporter: Transporter; from: string }> {
  if (!transporterPromise) {
    transporterPromise = createTransporter().catch((err) => {
      transporterPromise = null;
      throw err;
    });
  }
  return transporterPromise;
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

export async function sendReportEmail(email: ReportEmail): Promise<{ messageId: string }> {
  const { transporter, from } = await getTransporter();
  const info = await transporter.sendMail({
    from,
    to: email.to,
    subject: email.subject,
    text: email.body,
    html: `<div style="font-family:Arial,sans-serif;white-space:pre-wrap">${escapeHtml(email.body)}</div>`,
    attachments: [{ filename: email.filename, content: email.attachment, contentType: XLSX_MIME }],
  });
  return { messageId: info.messageId };
}
