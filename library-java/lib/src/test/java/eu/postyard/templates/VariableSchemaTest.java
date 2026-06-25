package eu.postyard.templates;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class VariableSchemaTest {

  @Nested
  class PrimitiveTypes {

    @Test
    void stringAcceptsString() {
      var schema = VariableSchema.of(Map.of("name", FieldSchema.string()));
      assertTrue(schema.validate(Map.of("name", "Alice")).isEmpty());
    }

    @Test
    void stringRejectsNonString() {
      var schema = VariableSchema.of(Map.of("name", FieldSchema.string()));
      assertContainsError(schema.validate(Map.of("name", 42)), "name", "expected string");
    }

    @Test
    void numberAcceptsInteger() {
      var schema = VariableSchema.of(Map.of("count", FieldSchema.number()));
      assertTrue(schema.validate(Map.of("count", 5)).isEmpty());
    }

    @Test
    void numberAcceptsDouble() {
      var schema = VariableSchema.of(Map.of("ratio", FieldSchema.number()));
      assertTrue(schema.validate(Map.of("ratio", 3.14)).isEmpty());
    }

    @Test
    void numberRejectsString() {
      var schema = VariableSchema.of(Map.of("count", FieldSchema.number()));
      assertContainsError(schema.validate(Map.of("count", "5")), "count", "expected number");
    }

    @Test
    void booleanAcceptsTrue() {
      var schema = VariableSchema.of(Map.of("active", FieldSchema.bool()));
      assertTrue(schema.validate(Map.of("active", true)).isEmpty());
    }

    @Test
    void booleanRejectsString() {
      var schema = VariableSchema.of(Map.of("active", FieldSchema.bool()));
      assertContainsError(schema.validate(Map.of("active", "true")), "active", "expected boolean");
    }
  }

  @Nested
  class EnumType {

    @Test
    void acceptsValueInList() {
      var schema =
          VariableSchema.of(
              Map.of("status", FieldSchema.enumOf(List.of("pending", "active", "closed"))));
      assertTrue(schema.validate(Map.of("status", "active")).isEmpty());
    }

    @Test
    void rejectsValueNotInList() {
      var schema =
          VariableSchema.of(
              Map.of("status", FieldSchema.enumOf(List.of("pending", "active", "closed"))));
      var errors = schema.validate(Map.of("status", "unknown"));
      assertContainsError(errors, "status", "not one of");
    }

    @Test
    void rejectsNonStringValue() {
      var schema = VariableSchema.of(Map.of("status", FieldSchema.enumOf(List.of("a", "b"))));
      assertContainsError(
          schema.validate(Map.of("status", 1)), "status", "expected string for enum");
    }

    @Test
    void requiresNonEmptyValuesList() {
      assertThrows(IllegalArgumentException.class, () -> FieldSchema.enumOf(List.of()));
    }
  }

  @Nested
  class TemporalTypes {

    @Test
    void dateAcceptsValidDate() {
      var schema = VariableSchema.of(Map.of("dob", FieldSchema.date()));
      assertTrue(schema.validate(Map.of("dob", "1990-06-15")).isEmpty());
    }

    @Test
    void dateRejectsInvalidDate() {
      var schema = VariableSchema.of(Map.of("dob", FieldSchema.date()));
      assertContainsError(schema.validate(Map.of("dob", "not-a-date")), "dob", "invalid date");
    }

    @Test
    void dateRejectsOutOfRangeDate() {
      var schema = VariableSchema.of(Map.of("dob", FieldSchema.date()));
      assertContainsError(schema.validate(Map.of("dob", "2024-13-01")), "dob", "invalid date");
    }

    @Test
    void timeAcceptsValidTime() {
      var schema = VariableSchema.of(Map.of("alarm", FieldSchema.time()));
      assertTrue(schema.validate(Map.of("alarm", "08:30:00")).isEmpty());
    }

    @Test
    void timeRejectsInvalidTime() {
      var schema = VariableSchema.of(Map.of("alarm", FieldSchema.time()));
      assertContainsError(schema.validate(Map.of("alarm", "25:00:00")), "alarm", "invalid time");
    }

    @Test
    void datetimeAcceptsOffsetDateTime() {
      var schema = VariableSchema.of(Map.of("ts", FieldSchema.datetime()));
      assertTrue(schema.validate(Map.of("ts", "2024-01-15T10:30:00+01:00")).isEmpty());
    }

    @Test
    void datetimeAcceptsUtcInstant() {
      var schema = VariableSchema.of(Map.of("ts", FieldSchema.datetime()));
      assertTrue(schema.validate(Map.of("ts", "2024-01-15T10:30:00Z")).isEmpty());
    }

    @Test
    void datetimeAcceptsLocalDateTime() {
      var schema = VariableSchema.of(Map.of("ts", FieldSchema.datetime()));
      assertTrue(schema.validate(Map.of("ts", "2024-01-15T10:30:00")).isEmpty());
    }

    @Test
    void datetimeRejectsPlainString() {
      var schema = VariableSchema.of(Map.of("ts", FieldSchema.datetime()));
      assertContainsError(schema.validate(Map.of("ts", "tomorrow")), "ts", "invalid datetime");
    }
  }

  @Nested
  class ObjectType {

    @Test
    void acceptsValidNestedObject() {
      var schema =
          VariableSchema.of(
              Map.of(
                  "address",
                  FieldSchema.object(
                      Map.of(
                          "street", FieldSchema.string(),
                          "city", FieldSchema.string()))));

      assertTrue(
          schema
              .validate(Map.of("address", Map.of("street", "Main St", "city", "Amsterdam")))
              .isEmpty());
    }

    @Test
    void rejectsNonMapValue() {
      var schema = VariableSchema.of(Map.of("address", FieldSchema.object(Map.of())));
      assertContainsError(
          schema.validate(Map.of("address", "not-an-object")), "address", "expected object");
    }

    @Test
    void reportsNestedMissingField() {
      var schema =
          VariableSchema.of(
              Map.of("address", FieldSchema.object(Map.of("city", FieldSchema.string()))));

      var errors = schema.validate(Map.of("address", Map.of()));
      assertContainsError(errors, "address.city", "required field is missing");
    }

    @Test
    void reportsNestedTypeError() {
      var schema =
          VariableSchema.of(
              Map.of("address", FieldSchema.object(Map.of("zip", FieldSchema.string()))));

      var errors = schema.validate(Map.of("address", Map.of("zip", 1234)));
      assertContainsError(errors, "address.zip", "expected string");
    }
  }

  @Nested
  class ArrayType {

    @Test
    void acceptsValidArray() {
      var schema = VariableSchema.of(Map.of("tags", FieldSchema.array(FieldSchema.string())));
      assertTrue(schema.validate(Map.of("tags", List.of("a", "b", "c"))).isEmpty());
    }

    @Test
    void rejectsNonListValue() {
      var schema = VariableSchema.of(Map.of("tags", FieldSchema.array()));
      assertContainsError(
          schema.validate(Map.of("tags", "not-an-array")), "tags", "expected array");
    }

    @Test
    void reportsIndexedItemError() {
      var schema = VariableSchema.of(Map.of("ids", FieldSchema.array(FieldSchema.number())));
      var errors = schema.validate(Map.of("ids", List.of(1, "two", 3)));
      assertContainsError(errors, "ids[1]", "expected number");
    }

    @Test
    void arrayWithoutItemsSchemaSkipsItemValidation() {
      var schema = VariableSchema.of(Map.of("items", FieldSchema.array()));
      assertTrue(schema.validate(Map.of("items", List.of(1, "mixed", true))).isEmpty());
    }
  }

  @Nested
  class RequiredAndOptional {

    @Test
    void missingRequiredFieldIsAnError() {
      var schema = VariableSchema.of(Map.of("name", FieldSchema.string()));
      assertContainsError(schema.validate(Map.of()), "name", "required field is missing");
    }

    @Test
    void missingOptionalFieldIsNotAnError() {
      var schema = VariableSchema.of(Map.of("nickname", FieldSchema.string(true)));
      assertTrue(schema.validate(Map.of()).isEmpty());
    }

    @Test
    void multipleErrorsAreAllReported() {
      var schema =
          VariableSchema.of(
              Map.of(
                  "a", FieldSchema.string(),
                  "b", FieldSchema.number()));
      var errors = schema.validate(Map.of());
      assertEquals(2, errors.size());
    }
  }

  @Nested
  class JsonParsing {

    @Test
    void parsesSchemaFromTemplateJson() throws IOException {
      var template =
          Template.builder()
              .block("html", BlockType.HTML, "{{name}}")
              .variables(VariableSchema.of(Map.of("name", FieldSchema.string())))
              .build();

      assertTrue(template.variables().isPresent());
      assertEquals(FieldType.STRING, template.variables().get().fields().get("name").type());
    }

    @Test
    void noVariablesSectionMeansNoSchema() throws IOException {
      var template = Template.builder().block("html", BlockType.HTML, "hi").build();

      assertTrue(template.variables().isEmpty());
    }
  }

  @Nested
  class IntegrationWithRender {

    @Test
    void renderThrowsWhenRequiredFieldMissing() {
      Template template =
          Template.builder()
              .block("html", BlockType.HTML, "Hello {{name}}")
              .variables(VariableSchema.of(Map.of("name", FieldSchema.string())))
              .build();

      var ex = assertThrows(TemplateValidationException.class, () -> template.render(Map.of()));
      assertContainsError(ex.errors(), "name", "required field is missing");
    }

    @Test
    void renderThrowsWhenFieldHasWrongType() {
      Template template =
          Template.builder()
              .block("html", BlockType.HTML, "{{count}}")
              .variables(VariableSchema.of(Map.of("count", FieldSchema.number())))
              .build();

      var ex =
          assertThrows(
              TemplateValidationException.class, () -> template.render(Map.of("count", "five")));
      assertContainsError(ex.errors(), "count", "expected number");
    }

    @Test
    void renderSucceedsWithValidData() throws IOException {
      Template template =
          Template.builder()
              .block("html", BlockType.HTML, "Hello {{name}}")
              .variables(VariableSchema.of(Map.of("name", FieldSchema.string())))
              .build();

      assertEquals("Hello Alice", template.render(Map.of("name", "Alice")).get("html"));
    }

    @Test
    void renderWithSkipValidationIgnoresErrors() throws IOException {
      Template template =
          Template.builder()
              .block("html", BlockType.HTML, "Hello")
              .variables(VariableSchema.of(Map.of("name", FieldSchema.string())))
              .build();

      // Should not throw even though "name" is missing
      assertNotNull(template.render(Map.of(), null, true));
    }

    @Test
    void validateMethodReturnsErrorsWithoutThrowing() {
      Template template =
          Template.builder()
              .block("html", BlockType.HTML, "{{name}}")
              .variables(VariableSchema.of(Map.of("name", FieldSchema.string())))
              .build();

      var errors = template.validate(Map.of());
      assertEquals(1, errors.size());
      assertTrue(errors.get(0).contains("name"));
    }

    @Test
    void validateReturnsEmptyListWhenNoSchemaIsDefined() {
      Template template = Template.builder().block("html", BlockType.HTML, "hi").build();

      assertTrue(template.validate(Map.of()).isEmpty());
    }

    @Test
    void classpathTemplateWithVariablesSectionValidatesOnRender() throws IOException {
      Template template = Template.fromClasspath("greeting");
      assertTrue(template.variables().isPresent());

      var ex = assertThrows(TemplateValidationException.class, () -> template.render(Map.of()));
      assertContainsError(ex.errors(), "name", "required field is missing");
    }

    @Test
    void classpathTemplateRendersSuccessfullyWithValidData() throws IOException {
      Template template = Template.fromClasspath("greeting");
      assertDoesNotThrow(() -> template.render(Map.of("name", "Alice")));
    }
  }

  // ---- helpers ----

  private static void assertContainsError(List<String> errors, String fieldPath, String fragment) {
    assertTrue(
        errors.stream().anyMatch(e -> e.contains(fieldPath) && e.contains(fragment)),
        "Expected an error containing '"
            + fieldPath
            + "' and '"
            + fragment
            + "' but got: "
            + errors);
  }
}
