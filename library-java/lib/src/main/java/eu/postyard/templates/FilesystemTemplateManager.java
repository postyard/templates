package eu.postyard.templates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link TemplateManager} that loads every template found directly beneath a base directory. Each
 * immediate subdirectory that contains a {@code template.json} is loaded as a template named after
 * the subdirectory.
 *
 * <p>All templates are read eagerly when the manager is created and share a single {@link
 * TemplateCache}. The manager itself is immutable afterwards, so lookups are thread-safe.
 */
public final class FilesystemTemplateManager implements TemplateManager {

  private final Map<String, Template> templates;

  private FilesystemTemplateManager(Map<String, Template> templates) {
    this.templates = templates;
  }

  /**
   * Returns the template named {@code name}.
   *
   * @param name the template's name, matching the subdirectory it was loaded from
   * @return the template
   * @throws IOException if no template with that name was loaded
   */
  @Override
  public Template get(String name) throws IOException {
    Template t = templates.get(name);
    if (t == null) throw new IOException("Template not found: " + name);
    return t;
  }

  /**
   * Loads every template under {@code baseDir} into a manager backed by a new cache.
   *
   * @param baseDir the directory whose subdirectories each describe one template
   * @return the manager
   * @throws IOException if the directory cannot be listed or a template cannot be read
   */
  public static FilesystemTemplateManager of(Path baseDir) throws IOException {
    return of(baseDir, new TemplateCache(TemplateCache.DEFAULT_MAX_BYTES));
  }

  /**
   * Loads every template under {@code baseDir} into a manager backed by the given cache, allowing a
   * cache to be shared across managers.
   *
   * @param baseDir the directory whose subdirectories each describe one template
   * @param cache the cache that stores compiled templates
   * @return the manager
   * @throws IOException if the directory cannot be listed or a template cannot be read
   */
  public static FilesystemTemplateManager of(Path baseDir, TemplateCache cache) throws IOException {
    Map<String, Template> templates = new HashMap<>();

    try (var stream = Files.list(baseDir)) {
      for (Path dir : stream.filter(Files::isDirectory).toList()) {
        Path descriptor = dir.resolve("template.json");
        if (!Files.exists(descriptor)) continue;
        String name = dir.getFileName().toString();
        templates.put(name, Template.fromDirectory(dir, cache));
      }
    }

    return new FilesystemTemplateManager(templates);
  }
}
