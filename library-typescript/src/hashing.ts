import { createHash } from "node:crypto";

/**
 * Computes the SHA-256 of a string.
 *
 * @param input - The string to hash.
 * @returns The 64-character lowercase hex digest.
 */
export function sha256(input: string): string {
  return createHash("sha256").update(input, "utf8").digest("hex");
}
