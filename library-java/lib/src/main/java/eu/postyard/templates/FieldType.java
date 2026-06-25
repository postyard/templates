package eu.postyard.templates;

/**
 * The type of a template variable, as declared in a template's variable schema and enforced by
 * {@link VariableSchema} when validating render data.
 */
public enum FieldType {

  /** A text value. */
  STRING,

  /** A numeric value. */
  NUMBER,

  /** A boolean value. */
  BOOLEAN,

  /** A string restricted to a fixed set of allowed values. */
  ENUM,

  /** A nested object with its own named fields. */
  OBJECT,

  /** A list whose elements share a single element type. */
  ARRAY,

  /** An ISO-8601 local date, e.g. {@code 2024-01-31}. */
  DATE,

  /** An ISO-8601 local time, e.g. {@code 14:30:00}. */
  TIME,

  /** An ISO-8601 date-time, with or without a UTC offset. */
  DATETIME
}
