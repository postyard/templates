package eu.postyard.templates;

import java.util.Collections;
import java.util.List;

/**
 * Thrown when data passed to {@link Template#render} does not satisfy the template's {@link
 * VariableSchema}. It carries the complete list of validation messages rather than reporting only
 * the first problem.
 */
public final class TemplateValidationException extends RuntimeException {

  /** The validation messages, in the order they were detected. */
  private final List<String> errors;

  /**
   * Creates an exception carrying the given validation messages.
   *
   * @param errors the validation messages; also joined into the exception's detail message
   */
  public TemplateValidationException(List<String> errors) {
    super("Template validation failed:\n" + String.join("\n", errors));
    this.errors = Collections.unmodifiableList(errors);
  }

  /** {@return the validation messages, in the order they were detected} */
  public List<String> errors() {
    return errors;
  }
}
