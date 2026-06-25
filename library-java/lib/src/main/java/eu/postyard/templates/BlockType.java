package eu.postyard.templates;

/**
 * The kind of content a template block produces. It determines how values interpolated into the
 * block are escaped during rendering.
 */
public enum BlockType {

  /** Output is HTML; interpolated values are HTML-escaped. */
  HTML,

  /** Output is plain text; interpolated values are emitted verbatim. */
  TEXT
}
