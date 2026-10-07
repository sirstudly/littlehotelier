import type { PropertyId } from "../config/properties.js";

export interface DbTarget {
  host: string;
  port: number;
  database: string;
}

/**
 * Same host resolution as the Java processors (`db.host`, Compose `DB_HOST`):
 * `DB_HOST` / `DB_PORT`, defaulting to the mysql-tailscale sidecar on 3306.
 */
export function dbTarget(id: PropertyId, hostOverride?: string, portOverride?: string): DbTarget {
  const host = hostOverride?.trim() || process.env.DB_HOST?.trim() || "mysql-tailscale";
  const port = portOverride?.trim() || process.env.DB_PORT?.trim();
  return {
    host,
    port: port ? Number(port) : 3306,
    database: `wp_${id}_backoffice`,
  };
}
