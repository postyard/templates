package eu.postyard.templates;

import java.util.Collections;
import java.util.Map;

/**
 * The result of rendering a {@link Template}: the rendered output of each block, keyed by block
 * name.
 */
public final class RenderedTemplate {

  private final Map<String, String> blocks;

  /**
   * Wraps the rendered output of each block.
   *
   * @param blocks the rendered output of each block, keyed by block name
   */
  public RenderedTemplate(Map<String, String> blocks) {
    this.blocks = Collections.unmodifiableMap(blocks);
  }

  /**
   * Returns the rendered output of a single block.
   *
   * @param blockName the block's name
   * @return the rendered output, or {@code null} if the template has no such block
   */
  public String get(String blockName) {
    return blocks.get(blockName);
  }

  /** {@return an unmodifiable view of every block's rendered output, keyed by name} */
  public Map<String, String> blocks() {
    return blocks;
  }
}
