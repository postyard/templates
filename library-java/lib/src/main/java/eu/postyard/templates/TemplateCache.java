package eu.postyard.templates;

import com.github.jknack.handlebars.Template;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A bounded, content-addressed cache of compiled Handlebars templates, shared by the {@link
 * eu.postyard.templates.Template}s that use it. Entries are keyed by a hash of the block source, so
 * identical sources compile only once and a given key always maps to the same compiled template.
 *
 * <p>The cache is bounded by the total byte size of the cached sources. When an insertion would
 * exceed the limit, the oldest-inserted entries are evicted until it fits; an evicted source is
 * simply recompiled if needed again. Lookups never block — only insertions are synchronized — which
 * suits the usage pattern where reads (every render) vastly outnumber writes (a source compiled for
 * the first time).
 *
 * <p>The cache also owns the single {@link TemplateEnvironment} shared by every template using it,
 * which ties that environment's lifetime to the cache.
 */
public final class TemplateCache {

  /** The default maximum total source size, in bytes. */
  public static final long DEFAULT_MAX_BYTES = 512L * 1024;

  private final long maxContentBytes;

  private record Entry(Template template, long size) {}

  private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
  private final Deque<String> insertionOrder = new ArrayDeque<>();
  private long currentBytes = 0;

  private TemplateEnvironment environment;

  /**
   * Creates an empty cache bounded by the given total source size.
   *
   * @param maxContentBytes the maximum total size, in bytes, of the cached sources
   * @throws IllegalArgumentException if {@code maxContentBytes} is not positive
   */
  public TemplateCache(long maxContentBytes) {
    if (maxContentBytes <= 0)
      throw new IllegalArgumentException("maxContentBytes must be positive");
    this.maxContentBytes = maxContentBytes;
  }

  /**
   * Returns the Handlebars environment shared by every template using this cache, creating it on
   * first use.
   *
   * @return the shared environment
   */
  synchronized TemplateEnvironment environment() {
    if (environment == null) environment = new TemplateEnvironment(this);
    return environment;
  }

  /**
   * Returns the compiled template stored under {@code hash}.
   *
   * @param hash the entry's key
   * @return the compiled template, or {@code null} if absent
   */
  public Template get(String hash) {
    Entry entry = entries.get(hash);
    return entry != null ? entry.template() : null;
  }

  /**
   * Stores a compiled template, evicting the oldest entries first if needed to stay within the size
   * limit. Does nothing if the key is already present or if the source alone exceeds the limit.
   *
   * @param hash the entry's key
   * @param content the source the template was compiled from, used to size the entry
   * @param template the compiled template
   */
  public synchronized void put(String hash, String content, Template template) {
    if (entries.containsKey(hash)) return;

    long size = content.getBytes(StandardCharsets.UTF_8).length;
    if (size > maxContentBytes) return;

    while (currentBytes + size > maxContentBytes && !insertionOrder.isEmpty()) {
      String oldest = insertionOrder.pollFirst();
      Entry evicted = entries.remove(oldest);
      if (evicted != null) currentBytes -= evicted.size();
    }

    entries.put(hash, new Entry(template, size));
    insertionOrder.addLast(hash);
    currentBytes += size;
  }

  /** {@return the number of cached entries} */
  public int size() {
    return entries.size();
  }

  /** {@return the total size, in bytes, of the cached sources} */
  public synchronized long currentBytes() {
    return currentBytes;
  }
}
