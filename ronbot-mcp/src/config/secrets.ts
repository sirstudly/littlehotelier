import { SecretManagerServiceClient } from "@google-cloud/secret-manager";

const projectId =
  process.env.GCP_PROJECT_ID ?? process.env.GOOGLE_CLOUD_PROJECT ?? "macbackpackers-backoffice";

let client: SecretManagerServiceClient | null = null;
const cache = new Map<string, string>();

function getClient(): SecretManagerServiceClient {
  if (!client) {
    client = new SecretManagerServiceClient();
  }
  return client;
}

/**
 * Fetches a secret version from GCP Secret Manager.
 * Secret names mirror Spring: db_username_crh, db_password_hsh, etc.
 */
export async function getSecret(secretId: string): Promise<string> {
  const cached = cache.get(secretId);
  if (cached !== undefined) {
    return cached;
  }

  // Local override for development without GCP
  const envKey = secretId.toUpperCase().replace(/[^A-Z0-9]/g, "_");
  if (process.env[envKey]) {
    const value = process.env[envKey]!;
    cache.set(secretId, value);
    return value;
  }

  const name = `projects/${projectId}/secrets/${secretId}/versions/latest`;
  const [version] = await getClient().accessSecretVersion({ name });
  const payload = version.payload?.data;
  if (!payload) {
    throw new Error(`Secret ${secretId} has empty payload`);
  }
  const value =
    typeof payload === "string" ? payload : Buffer.from(payload as Uint8Array).toString("utf8");
  cache.set(secretId, value.trim());
  return cache.get(secretId)!;
}

export async function getDbCredentials(secretSuffix: string): Promise<{
  username: string;
  password: string;
}> {
  const [username, password] = await Promise.all([
    getSecret(`db_username_${secretSuffix}`),
    getSecret(`db_password_${secretSuffix}`),
  ]);
  return { username, password };
}
