export function audit(event: string, details: Record<string, unknown>): void {
  const line = JSON.stringify({
    ts: new Date().toISOString(),
    event,
    ...details,
  });
  console.error(`[ronbot-audit] ${line}`);
}
