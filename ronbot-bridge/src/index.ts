import { config } from "./env.js";
import { startServer } from "./server.js";

console.log("ronbot-bridge starting...");
if (config.groups.length === 0) {
  console.warn(
    "WARNING: no allowlisted groups — set RONBOT_WHATSAPP_GROUPS or config/groups.json",
  );
}
if (!config.googleCredentials) {
  console.warn("WARNING: GOOGLE_APPLICATION_CREDENTIALS unset — MCP DB tools will fail");
}
startServer();
