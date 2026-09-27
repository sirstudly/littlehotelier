/**
 * Offline smoke: guest request prompt building + reply validation (no Cursor call).
 * Run: npx tsx scripts/smoke-guest-requests.ts
 */
import {
  buildGuestRequestPrompt,
  GuestRequestExtractionError,
  guestRequestInputSchema,
  normaliseSpecialRequests,
  parseGuestRequestReply,
} from "../src/agent/guestRequests.js";
import { isAuthorizedInternal } from "../src/server.js";
import { config } from "../src/env.js";

function assert(cond: unknown, msg: string): void {
  if (!cond) throw new Error(msg);
}

function assertThrows(fn: () => unknown, msg: string): void {
  try {
    fn();
  } catch (err) {
    assert(err instanceof GuestRequestExtractionError, `${msg}: wrong error ${String(err)}`);
    return;
  }
  throw new Error(`${msg}: expected throw`);
}

const bdc =
  "\n<br />*** Genius booker *** You have received a virtual credit card for this reservation.You may charge it as of 2026-09-26.\n" +
  "<br />I am gonna come with my friends so please arrange same bed . The virtual card expires on 2027-10-01. You can charge 58.50 GBP until this expiration date.\n" +
  "<br />: Toby McMahon";

const normalised = normaliseSpecialRequests(bdc);
assert(!normalised.includes("<br"), "br tags removed");
assert(normalised.startsWith("*** Genius booker"), "leading whitespace trimmed");

const prompt = buildGuestRequestPrompt([
  { id: 101, text: bdc },
  { id: 102, text: "<br />Can be cancelled until: 2026-11-08 23:59:59." },
]);
assert(prompt.includes("=== booking id 101 ==="), "prompt has booking 101");
assert(prompt.includes("=== booking id 102 ==="), "prompt has booking 102");

const parsed = parseGuestRequestReply(
  'Sure:\n[{"id":101,"request":" Please put me in the same room as my friends. "},{"id":102,"request":null}]',
  [101, 102],
);
assert(parsed.length === 2, "two results");
assert(parsed[0]!.request === "Please put me in the same room as my friends.", "request trimmed");
assert(parsed[1]!.request === null, "null kept");

assert(
  parseGuestRequestReply('[{"id":101,"request":"   "}]', [101])[0]!.request === null,
  "blank request becomes null",
);

assertThrows(() => parseGuestRequestReply("no json here", [101]), "no array");
assertThrows(() => parseGuestRequestReply('[{"id":101,"request":null}]', [101, 102]), "missing id");
assertThrows(
  () => parseGuestRequestReply('[{"id":101,"request":null},{"id":101,"request":null}]', [101]),
  "duplicate id",
);
assertThrows(() => parseGuestRequestReply('[{"id":999,"request":null}]', [101]), "unknown id");
assertThrows(() => parseGuestRequestReply('[{"id":101}]', [101]), "missing request field");

assert(guestRequestInputSchema.safeParse([{ id: 1, text: "x" }]).success, "valid input");
assert(!guestRequestInputSchema.safeParse([]).success, "empty batch rejected");
assert(
  guestRequestInputSchema.safeParse(Array.from({ length: 100 }, (_, i) => ({ id: i + 1, text: "x" }))).success,
  "batch of 100 accepted",
);
assert(
  !guestRequestInputSchema.safeParse(Array.from({ length: 101 }, (_, i) => ({ id: i + 1, text: "x" }))).success,
  "oversize batch rejected",
);

config.ronbotToken = "secret";
assert(isAuthorizedInternal("Bearer secret"), "correct token accepted");
assert(!isAuthorizedInternal("Bearer wrong!"), "wrong token rejected");
assert(!isAuthorizedInternal(undefined), "missing header rejected");
config.ronbotToken = "";
assert(!isAuthorizedInternal("Bearer "), "empty configured token rejects everything");

console.log("smoke-guest-requests: ok");
