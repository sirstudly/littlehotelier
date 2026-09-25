import { closePools } from "./db/pools.js";
import { closeReadOnlyPools } from "./db/readOnlyPool.js";
import { startStdio } from "./server.js";

async function main(): Promise<void> {
  const shutdown = async () => {
    try {
      await closePools();
      await closeReadOnlyPools();
    } finally {
      process.exit(0);
    }
  };
  process.on("SIGINT", () => void shutdown());
  process.on("SIGTERM", () => void shutdown());

  await startStdio();
}

main().catch((err) => {
  console.error("ronbot-mcp failed to start", err);
  process.exit(1);
});
