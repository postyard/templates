/**
 * The kind of content a block produces, which controls how interpolated values
 * are escaped during rendering.
 */
export enum BlockType {
  /** Output is HTML; interpolated values are HTML-escaped. */
  HTML = "html",

  /** Output is plain text; interpolated values are emitted verbatim. */
  TEXT = "text",
}
