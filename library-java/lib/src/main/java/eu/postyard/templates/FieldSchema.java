package eu.postyard.templates;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Describes a single template variable: its {@link FieldType}, whether it is optional, and any
 * type-specific detail — the nested fields of an object, the element schema of an array, or the
 * allowed values of an enum. Used by {@link VariableSchema} to validate render data.
 *
 * <p>Instances are created with the static factory methods. Every type has a no-argument factory (a
 * required field) and a variant taking an {@code optional} flag.
 */
public final class FieldSchema {

  private final FieldType type;
  private final boolean optional;
  private final Map<String, FieldSchema> fields;
  private final FieldSchema items;
  private final List<String> values;

  private FieldSchema(
      FieldType type,
      boolean optional,
      Map<String, FieldSchema> fields,
      FieldSchema items,
      List<String> values) {
    this.type = type;
    this.optional = optional;
    this.fields = fields;
    this.items = items;
    this.values = values;
  }

  /** {@return the field's type} */
  public FieldType type() {
    return type;
  }

  /** {@return whether the field may be absent or null} */
  public boolean optional() {
    return optional;
  }

  /** {@return the nested fields of an object field, or null otherwise} */
  public Map<String, FieldSchema> fields() {
    return fields;
  }

  /** {@return the element schema of an array field, or null if untyped or not an array} */
  public FieldSchema items() {
    return items;
  }

  /** {@return the allowed values of an enum field, or null otherwise} */
  public List<String> values() {
    return values;
  }

  /** {@return a required string field} */
  public static FieldSchema string() {
    return primitive(FieldType.STRING, false);
  }

  /**
   * {@return a string field}
   *
   * @param optional whether the field may be absent or null
   */
  public static FieldSchema string(boolean optional) {
    return primitive(FieldType.STRING, optional);
  }

  /** {@return a required number field} */
  public static FieldSchema number() {
    return primitive(FieldType.NUMBER, false);
  }

  /**
   * {@return a number field}
   *
   * @param optional whether the field may be absent or null
   */
  public static FieldSchema number(boolean optional) {
    return primitive(FieldType.NUMBER, optional);
  }

  /** {@return a required boolean field} */
  public static FieldSchema bool() {
    return primitive(FieldType.BOOLEAN, false);
  }

  /**
   * {@return a boolean field}
   *
   * @param optional whether the field may be absent or null
   */
  public static FieldSchema bool(boolean optional) {
    return primitive(FieldType.BOOLEAN, optional);
  }

  /** {@return a required ISO-8601 date field} */
  public static FieldSchema date() {
    return primitive(FieldType.DATE, false);
  }

  /**
   * {@return an ISO-8601 date field}
   *
   * @param optional whether the field may be absent or null
   */
  public static FieldSchema date(boolean optional) {
    return primitive(FieldType.DATE, optional);
  }

  /** {@return a required ISO-8601 time field} */
  public static FieldSchema time() {
    return primitive(FieldType.TIME, false);
  }

  /**
   * {@return an ISO-8601 time field}
   *
   * @param optional whether the field may be absent or null
   */
  public static FieldSchema time(boolean optional) {
    return primitive(FieldType.TIME, optional);
  }

  /** {@return a required ISO-8601 date-time field} */
  public static FieldSchema datetime() {
    return primitive(FieldType.DATETIME, false);
  }

  /**
   * {@return an ISO-8601 date-time field}
   *
   * @param optional whether the field may be absent or null
   */
  public static FieldSchema datetime(boolean optional) {
    return primitive(FieldType.DATETIME, optional);
  }

  /**
   * {@return a required enum field restricted to {@code values}}
   *
   * @param values the allowed values
   */
  public static FieldSchema enumOf(List<String> values) {
    return enumOf(false, values);
  }

  /**
   * {@return an enum field restricted to {@code vs}}
   *
   * @param optional whether the field may be absent or null
   * @param vs the allowed values; must be non-empty
   * @throws IllegalArgumentException if {@code vs} is null or empty
   */
  public static FieldSchema enumOf(boolean optional, List<String> vs) {
    if (vs == null || vs.isEmpty())
      throw new IllegalArgumentException("enum requires at least one value");
    return new FieldSchema(FieldType.ENUM, optional, null, null, Collections.unmodifiableList(vs));
  }

  /**
   * {@return a required object field with the given fields}
   *
   * @param fields the nested fields, keyed by name
   */
  public static FieldSchema object(Map<String, FieldSchema> fields) {
    return object(false, fields);
  }

  /**
   * {@return an object field with the given fields}
   *
   * @param optional whether the field may be absent or null
   * @param fs the nested fields, keyed by name; may be null for an unconstrained object
   */
  public static FieldSchema object(boolean optional, Map<String, FieldSchema> fs) {
    Map<String, FieldSchema> safe =
        fs != null ? Collections.unmodifiableMap(new LinkedHashMap<>(fs)) : Collections.emptyMap();
    return new FieldSchema(FieldType.OBJECT, optional, safe, null, null);
  }

  /** {@return a required array field whose elements are not type-checked} */
  public static FieldSchema array() {
    return array(false, null);
  }

  /**
   * {@return a required array field of {@code items}}
   *
   * @param items the schema applied to each element
   */
  public static FieldSchema array(FieldSchema items) {
    return array(false, items);
  }

  /**
   * {@return an array field}
   *
   * @param optional whether the field may be absent or null
   * @param items the schema applied to each element, or null to leave elements unchecked
   */
  public static FieldSchema array(boolean optional, FieldSchema items) {
    return new FieldSchema(FieldType.ARRAY, optional, null, items, null);
  }

  private static FieldSchema primitive(FieldType type, boolean optional) {
    return new FieldSchema(type, optional, null, null, null);
  }

  @SuppressWarnings("unchecked")
  static FieldSchema fromMap(Map<String, Object> map) {
    Object typeRaw = map.get("type");
    if (!(typeRaw instanceof String)) {
      throw new IllegalArgumentException("Field schema missing or invalid 'type': " + typeRaw);
    }
    FieldType type;
    try {
      type = FieldType.valueOf(((String) typeRaw).toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown field type: " + typeRaw);
    }

    boolean optional = Boolean.TRUE.equals(map.get("optional"));

    return switch (type) {
      case ENUM -> {
        Object rawValues = map.get("values");
        if (!(rawValues instanceof List<?>)) {
          throw new IllegalArgumentException("enum field requires a 'values' array");
        }
        yield enumOf(
            optional,
            ((List<?>) rawValues).stream().map(Object::toString).collect(Collectors.toList()));
      }
      case OBJECT -> {
        Object rawFields = map.get("fields");
        Map<String, FieldSchema> fields = Collections.emptyMap();
        if (rawFields instanceof Map<?, ?> rawMap) {
          fields =
              ((Map<String, Object>) rawMap)
                  .entrySet().stream()
                      .collect(
                          Collectors.toMap(
                              Map.Entry::getKey,
                              e -> fromMap((Map<String, Object>) e.getValue()),
                              (a, b) -> a,
                              LinkedHashMap::new));
        }
        yield object(optional, fields);
      }
      case ARRAY -> {
        Object rawItems = map.get("items");
        FieldSchema itemSchema =
            (rawItems instanceof Map<?, ?> m) ? fromMap((Map<String, Object>) m) : null;
        yield array(optional, itemSchema);
      }
      default -> primitive(type, optional);
    };
  }
}
