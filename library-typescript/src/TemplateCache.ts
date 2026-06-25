import type { TemplateDelegate } from "handlebars";
import { TemplateEnvironment } from "./TemplateEnvironment.js";

interface CacheEntry {
  template: TemplateDelegate;
  sizeBytes: number;
}

/**
 * A bounded cache of compiled Handlebars templates, keyed by a hash of the block
 * source. Bounded by the total byte size of the cached sources; when an
 * insertion would exceed the limit, the oldest-inserted entries are evicted
 * until it fits.
 *
 * Pass a shared instance to {@link Template.create}, {@link Template.fromDirectory}
 * or {@link FilesystemTemplateManager.of} to share compiled templates — and the
 * Handlebars environment — across templates.
 */
export class TemplateCache {
  /** Default maximum total source size, in bytes (50 MiB). */
  static readonly DEFAULT_MAX_BYTES = 50 * 1024 * 1024;

  private readonly maxBytes: number;
  private readonly entries = new Map<string, CacheEntry>();
  private usedBytes = 0;
  private env?: TemplateEnvironment;

  /**
   * @param maxBytes - Maximum total size, in bytes, of the cached sources.
   * Defaults to {@link TemplateCache.DEFAULT_MAX_BYTES}.
   */
  constructor(maxBytes = TemplateCache.DEFAULT_MAX_BYTES) {
    this.maxBytes = maxBytes;
  }

  /**
   * Returns the Handlebars environment shared by every template using this
   * cache, creating it on first call.
   *
   * @internal
   */
  environment(): TemplateEnvironment {
    this.env ??= new TemplateEnvironment(this);
    return this.env;
  }

  /**
   * @param key - The entry's key.
   * @returns The compiled template, or `undefined` if absent.
   */
  get(key: string): TemplateDelegate | undefined {
    return this.entries.get(key)?.template;
  }

  /**
   * Stores a compiled template, evicting the oldest entries first if needed to
   * stay within the size limit. Does nothing if the source alone exceeds the
   * limit.
   *
   * @param key - The entry's key.
   * @param source - The source the template was compiled from, used to size the entry.
   * @param template - The compiled template.
   */
  put(key: string, source: string, template: TemplateDelegate): void {
    const sizeBytes = Buffer.byteLength(source, "utf8");
    if (sizeBytes > this.maxBytes) return;

    while (this.usedBytes + sizeBytes > this.maxBytes && this.entries.size > 0) {
      const oldest = this.entries.entries().next().value;
      if (!oldest) break;
      const [oldestKey, entry] = oldest;
      this.entries.delete(oldestKey);
      this.usedBytes -= entry.sizeBytes;
    }

    this.entries.set(key, { template, sizeBytes });
    this.usedBytes += sizeBytes;
  }
}
