package eu.postyard.templates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Describes the variables a {@link Template} expects: a set of named top-level fields, each a
 * {@link FieldSchema}. Validating a data map checks every declared field — recursing into objects
 * and array elements and parsing temporal values — and collects every problem rather than stopping
 * at the first. Data keys that are not declared in the schema are ignored.
 */
public final class VariableSchema {

  private final Map<String, FieldSchema> fields;

  private VariableSchema(Map<String, FieldSchema> fields) {
    this.fields = Collections.unmodifiableMap(fields);
  }

  /** {@return the top-level fields, keyed by name, in declaration order} */
  public Map<String, FieldSchema> fields() {
    return fields;
  }

  /**
   * Validates {@code data} against this schema.
   *
   * @param data the data to check; {@code null} is treated as empty
   * @return one message per problem found, each prefixed with the dotted path to the offending
   *     field; empty if the data is valid
   */
  public List<String> validate(Map<String, Object> data) {
    List<String> errors = new ArrayList<>();
    validateObject(fields, data != null ? data : Collections.emptyMap(), "", errors);
    return errors;
  }

  /**
   * Creates a schema from field definitions.
   *
   * @param fields the top-level fields, keyed by name
   * @return the schema
   */
  public static VariableSchema of(Map<String, FieldSchema> fields) {
    return new VariableSchema(new LinkedHashMap<>(fields));
  }

  @SuppressWarnings("unchecked")
  static VariableSchema fromMap(Map<String, Object> map) {
    Map<String, FieldSchema> fields =
        map.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> FieldSchema.fromMap((Map<String, Object>) e.getValue()),
                    (a, b) -> a,
                    LinkedHashMap::new));
    return new VariableSchema(fields);
  }

  private void validateObject(
      Map<String, FieldSchema> schema, Map<String, Object> data, String path, List<String> errors) {
    for (Map.Entry<String, FieldSchema> entry : schema.entrySet()) {
      String name = entry.getKey();
      FieldSchema fieldSchema = entry.getValue();
      String fieldPath = path.isEmpty() ? name : path + "." + name;
      Object value = data.get(name);

      if (value == null) {
        if (!fieldSchema.optional()) {
          errors.add(fieldPath + ": required field is missing");
        }
        continue;
      }
      validateField(fieldSchema, value, fieldPath, errors);
    }
  }

  @SuppressWarnings("unchecked")
  private void validateField(FieldSchema schema, Object value, String path, List<String> errors) {
    switch (schema.type()) {
      case STRING -> {
        if (!(value instanceof String)) {
          errors.add(path + ": expected string, got " + typeName(value));
        }
      }
      case NUMBER -> {
        if (!(value instanceof Number)) {
          errors.add(path + ": expected number, got " + typeName(value));
        }
      }
      case BOOLEAN -> {
        if (!(value instanceof Boolean)) {
          errors.add(path + ": expected boolean, got " + typeName(value));
        }
      }
      case ENUM -> {
        if (!(value instanceof String s)) {
          errors.add(path + ": expected string for enum, got " + typeName(value));
        } else if (!schema.values().contains(s)) {
          errors.add(path + ": '" + s + "' is not one of " + schema.values());
        }
      }
      case OBJECT -> {
        if (!(value instanceof Map<?, ?> map)) {
          errors.add(path + ": expected object, got " + typeName(value));
        } else if (!schema.fields().isEmpty()) {
          validateObject(schema.fields(), (Map<String, Object>) map, path, errors);
        }
      }
      case ARRAY -> {
        if (!(value instanceof List<?> list)) {
          errors.add(path + ": expected array, got " + typeName(value));
        } else if (schema.items() != null) {
          for (int i = 0; i < list.size(); i++) {
            validateField(schema.items(), list.get(i), path + "[" + i + "]", errors);
          }
        }
      }
      case DATE ->
          validateTemporalString(value, path, errors, s -> LocalDate.parse(s), "date (YYYY-MM-DD)");
      case TIME ->
          validateTemporalString(value, path, errors, s -> LocalTime.parse(s), "time (HH:MM:SS)");
      case DATETIME ->
          validateTemporalString(
              value,
              path,
              errors,
              s -> {
                try {
                  OffsetDateTime.parse(s);
                } catch (DateTimeParseException e) {
                  LocalDateTime.parse(s);
                }
              },
              "datetime (ISO 8601)");
    }
  }

  private void validateTemporalString(
      Object value,
      String path,
      List<String> errors,
      TemporalParser parser,
      String expectedFormat) {
    if (!(value instanceof String s)) {
      errors.add(path + ": expected " + expectedFormat + ", got " + typeName(value));
      return;
    }
    try {
      parser.parse(s);
    } catch (DateTimeParseException e) {
      errors.add(path + ": invalid " + expectedFormat + ": '" + s + "'");
    }
  }

  @FunctionalInterface
  private interface TemporalParser {
    void parse(String value) throws DateTimeParseException;
  }

  private static String typeName(Object value) {
    if (value == null) return "null";
    if (value instanceof String) return "string";
    if (value instanceof Number) return "number";
    if (value instanceof Boolean) return "boolean";
    if (value instanceof List) return "array";
    if (value instanceof Map) return "object";
    return value.getClass().getSimpleName().toLowerCase();
  }
}
