package eu.postyard.templates;

import java.io.IOException;

/**
 * Provides {@link Template}s by name, hiding where they are loaded from (the classpath, the
 * filesystem, or elsewhere) and how they are cached.
 */
public interface TemplateManager {

  /**
   * Returns the template registered under the given name.
   *
   * @param name the template's name
   * @return the template
   * @throws IOException if the template cannot be found or loaded
   */
  Template get(String name) throws IOException;
}
