import { describe, expect, it } from "vitest";
import { BlockType } from "../src/BlockType.js";
import { Template } from "../src/Template.js";
import { TemplateValidationException } from "../src/TemplateValidationException.js";
import { buildVariableSchema } from "../src/VariableSchema.js";

describe("buildVariableSchema", () => {
  const schema = buildVariableSchema({
    user: {
      type: "object",
      fields: {
        firstName: { type: "string" },
        email: { type: "string" },
        nickname: { type: "string", optional: true },
      },
    },
    accountType: { type: "enum", values: ["personal", "business"] },
    marketingEmails: { type: "boolean" },
    registeredAt: { type: "datetime" },
    estimatedDelivery: { type: "date" },
    tags: { type: "array", items: { type: "string" } },
  });

  const valid = {
    user: { firstName: "Sophie", email: "s@example.com" },
    accountType: "business",
    marketingEmails: true,
    registeredAt: "2024-03-15T09:00:00+01:00",
    estimatedDelivery: "2024-03-18",
    tags: ["a", "b"],
  };

  it("accepts valid data", () => {
    expect(schema.safeParse(valid).success).toBe(true);
  });

  it("allows omitting optional fields", () => {
    expect(schema.safeParse(valid).success).toBe(true); // nickname absent
    const withNull = { ...valid, user: { ...valid.user, nickname: null } };
    expect(schema.safeParse(withNull).success).toBe(true);
  });

  it("ignores unknown extra keys", () => {
    expect(schema.safeParse({ ...valid, surprise: 42 }).success).toBe(true);
  });

  it("flags a wrong type with a path", () => {
    const result = schema.safeParse({ ...valid, marketingEmails: "yes" });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path.join(".") === "marketingEmails")).toBe(true);
    }
  });

  it("flags an enum value not in the list", () => {
    const result = schema.safeParse({ ...valid, accountType: "enterprise" });
    expect(result.success).toBe(false);
  });

  it("flags a missing required nested field", () => {
    const result = schema.safeParse({ ...valid, user: { firstName: "Sophie" } });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues.some((i) => i.path.join(".") === "user.email")).toBe(true);
    }
  });

  it("rejects a malformed datetime", () => {
    expect(schema.safeParse({ ...valid, registeredAt: "not-a-date" }).success).toBe(false);
  });
});

describe("Template render validation", () => {
  const template = Template.create({
    blocks: [{ name: "html", type: BlockType.HTML, source: "Hi {{name}}" }],
    variables: buildVariableSchema({ name: { type: "string" } }),
  });

  it("renders when data is valid", () => {
    expect(template.render({ name: "Sophie" }).get("html")).toBe("Hi Sophie");
  });

  it("throws TemplateValidationException with a readable message on invalid data", () => {
    expect(() => template.render({ name: 123 })).toThrow(TemplateValidationException);
    try {
      template.render({});
    } catch (err) {
      const ex = err as TemplateValidationException;
      expect(ex.errors.some((e) => e.startsWith("name:"))).toBe(true);
      expect(ex.error.issues.length).toBeGreaterThan(0);
    }
  });

  it("validate() returns formatted issue strings", () => {
    expect(template.validate({ name: "ok" })).toEqual([]);
    expect(template.validate({ name: 5 })).toHaveLength(1);
  });
});
