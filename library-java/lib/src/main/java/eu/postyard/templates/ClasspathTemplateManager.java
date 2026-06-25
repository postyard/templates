package eu.postyard.templates;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link TemplateManager} that loads templates from the classpath, beneath a fixed prefix. A
 * template named {@code "welcome"} with prefix {@code "emails/"} is read from {@code
 * emails/welcome/} on the classpath.
 *
 * <p>Templates are loaded lazily on first request and then cached, so repeated lookups of the same
 * name return the same instance. Lookups are thread-safe.
 */
public final class ClasspathTemplateManager implements TemplateManager {

  private final String prefix;
  private final TemplateCache cache;
  private final ConcurrentHashMap<String, Template> templates = new ConcurrentHashMap<>();

  private ClasspathTemplateManager(String prefix, TemplateCache cache) {
    this.prefix = prefix;
    this.cache = cache;
  }

  /**
   * Returns the template named {@code name}, loading it from {@code <prefix><name>/} on the
   * classpath the first time it is requested.
   *
   * @param name the template's name
   * @return the template
   * @throws IOException if the template's resources cannot be found or read
   */
  @Override
  public Template get(String name) throws IOException {
    Template existing = templates.get(name);
    if (existing != null) return existing;

    Template loaded = Template.fromClasspath(prefix + name, cache);
    Template prev = templates.putIfAbsent(name, loaded);
    return prev != null ? prev : loaded;
  }

  /**
   * Creates a manager backed by a new cache.
   *
   * @param prefix the classpath prefix prepended to every template name
   * @return the manager
   */
  public static ClasspathTemplateManager of(String prefix) {
    return new ClasspathTemplateManager(prefix, new TemplateCache(TemplateCache.DEFAULT_MAX_BYTES));
  }

  /**
   * Creates a manager backed by the given cache, allowing a cache to be shared across managers.
   *
   * @param prefix the classpath prefix prepended to every template name
   * @param cache the cache that stores compiled templates
   * @return the manager
   */
  public static ClasspathTemplateManager of(String prefix, TemplateCache cache) {
    return new ClasspathTemplateManager(prefix, cache);
  }
}
