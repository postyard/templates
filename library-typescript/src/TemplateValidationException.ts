import { z } from "zod";

/**
 * Thrown by {@link Template.render} when render data fails the template's
 * variable schema. Use {@link TemplateValidationException.errors} for flattened
 * messages, or {@link TemplateValidationException.error} for the structured Zod
 * issues.
 */
export class TemplateValidationException extends Error {
  /** The underlying validation error. */
  readonly error: z.ZodError;

  /**
   * @param error - The validation error to wrap.
   */
  constructor(error: z.ZodError) {
    super(`Template validation failed:\n${z.prettifyError(error)}`);
    this.name = "TemplateValidationException";
    this.error = error;
  }

  /**
   * @returns One `"path: message"` string per validation issue.
   */
  get errors(): string[] {
    return formatIssues(this.error);
  }
}

/**
 * Flattens a Zod error into `"path: message"` strings, one per issue.
 *
 * @param error - The validation error.
 * @returns One message per issue; root-level issues use `(root)` as the path.
 */
export function formatIssues(error: z.ZodError): string[] {
  return error.issues.map((issue) => {
    const path = issue.path.length > 0 ? issue.path.join(".") : "(root)";
    return `${path}: ${issue.message}`;
  });
}
