import { z } from "zod";

/** A compiled variable schema: a Zod object that validates a template's render data. */
export type VariableSchema = z.ZodObject;

/**
 * Compiles a template's `variables` declaration into a {@link VariableSchema}.
 *
 * Each field is `{ "type": <type>, "optional"?: true, ... }`:
 *
 * - `string`, `number`, `boolean` — primitives
 * - `date`, `time`, `datetime` — ISO 8601 strings
 * - `enum` with `"values": [...]` — one of the listed strings
 * - `object` with `"fields": { ... }` — nested fields; undeclared keys are ignored
 * - `array` with optional `"items": <field>` — homogeneous array
 *
 * An optional field accepts `null`, `undefined`, or omission.
 *
 * @param raw - The parsed `variables` object from a template descriptor.
 * @returns The compiled schema.
 * @throws If `raw` is not an object or a field declaration is invalid.
 */
export function buildVariableSchema(raw: unknown): VariableSchema {
  if (!raw || typeof raw !== "object") {
    throw new Error("variables must be an object");
  }
  const shape: Record<string, z.ZodType> = {};
  for (const [name, field] of Object.entries(raw as Record<string, unknown>)) {
    shape[name] = buildField(field);
  }
  return z.object(shape);
}

function buildField(raw: unknown): z.ZodType {
  if (!raw || typeof raw !== "object") {
    throw new Error("field schema must be an object");
  }
  const map = raw as Record<string, unknown>;

  const typeStr = map.type;
  if (typeof typeStr !== "string") throw new Error("field schema missing 'type'");

  const base = baseSchema(typeStr.toLowerCase(), map, typeStr);
  return map.optional === true ? base.nullish() : base;
}

function baseSchema(type: string, map: Record<string, unknown>, original: string): z.ZodType {
  switch (type) {
    case "string":
      return z.string();
    case "number":
      return z.number();
    case "boolean":
      return z.boolean();
    case "date":
      return z.iso.date();
    case "time":
      return z.iso.time();
    case "datetime":
      return z.iso.datetime({ offset: true });
    case "enum": {
      const values = map.values;
      if (!Array.isArray(values) || values.length === 0) {
        throw new Error("enum field requires a non-empty 'values' array");
      }
      return z.enum(values.map(String) as [string, ...string[]]);
    }
    case "object": {
      const rawFields = map.fields;
      const shape: Record<string, z.ZodType> = {};
      if (rawFields && typeof rawFields === "object") {
        for (const [k, v] of Object.entries(rawFields as Record<string, unknown>)) {
          shape[k] = buildField(v);
        }
      }
      return z.object(shape);
    }
    case "array": {
      const rawItems = map.items;
      return z.array(rawItems ? buildField(rawItems) : z.unknown());
    }
    default:
      throw new Error(`unknown field type: ${original}`);
  }
}
