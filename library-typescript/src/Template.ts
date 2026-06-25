import { access, readFile } from "node:fs/promises";
import { join } from "node:path";
import type { BlockType } from "./BlockType.js";
import { RenderedTemplate } from "./RenderedTemplate.js";
import { TemplateCache } from "./TemplateCache.js";
import { CTX_TRANSLATIONS, type TemplateEnvironment } from "./TemplateEnvironment.js";
import { formatIssues, TemplateValidationException } from "./TemplateValidationException.js";
import { buildVariableSchema, type VariableSchema } from "./VariableSchema.js";

/** Per-locale translations, keyed first by locale and then by translation id. */
export type LocaleTranslations = Record<string, Record<string, string>>;

/** A single named block of a template. */
export interface TemplateBlock {
  /** The block's name; used to look up its rendered output. */
  name: string;
  /** Whether the block is HTML or text. */
  type: BlockType;
  /** The block's Handlebars source. */
  source: string;
}

/** Options for {@link Template.create}. */
export interface TemplateOptions {
  /** Blocks in render order. */
  blocks: TemplateBlock[];
  /** Per-locale translations. */
  translations?: LocaleTranslations;
  /** Schema used to validate render data. */
  variables?: VariableSchema;
  /** Cache for compiled blocks; a new one is created if omitted. */
  cache?: TemplateCache;
}

/**
 * A message template made up of one or more named blocks, with optional
 * per-locale translations and an optional schema describing the variables it
 * expects.
 *
 * Create one from a directory with {@link Template.fromDirectory}, or from blocks
 * in code with {@link Template.create}, then call {@link Template.render}.
 */
export class Template {
  private readonly blocks: TemplateBlock[];
  private readonly translations: LocaleTranslations | null;
  private readonly variableSchema: VariableSchema | null;
  private readonly env: TemplateEnvironment;

  private constructor(
    blocks: TemplateBlock[],
    translations: LocaleTranslations | null,
    variableSchema: VariableSchema | null,
    cache: TemplateCache,
  ) {
    this.blocks = blocks;
    this.translations = translations;
    this.variableSchema = variableSchema;
    this.env = cache.environment();
  }

  /**
   * Checks data against the template's variable schema without rendering.
   *
   * @param data - The data to validate.
   * @returns One `"path: message"` string per problem; empty if the data is
   * valid or the template has no schema.
   */
  validate(data: Record<string, unknown>): string[] {
    if (!this.variableSchema) return [];
    const result = this.variableSchema.safeParse(data);
    return result.success ? [] : formatIssues(result.error);
  }

  /**
   * @returns The template's variable schema, or `null` if it has none.
   */
  variables(): VariableSchema | null {
    return this.variableSchema;
  }

  /**
   * @returns The locales for which the template has translations, sorted.
   */
  locales(): string[] {
    return this.translations ? Object.keys(this.translations).sort() : [];
  }

  /**
   * @returns The template's block names, in render order.
   */
  blockNames(): string[] {
    return this.blocks.map((block) => block.name);
  }

  /**
   * Renders every block with the given data.
   *
   * @param data - The variables to render with.
   * @param locale - The locale whose translations to use; omit to use the text
   * written inline in each block.
   * @param skipValidation - When `true`, skips checking `data` against the schema.
   * @returns The rendered output of each block.
   * @throws {@link TemplateValidationException} If validation runs and `data`
   * does not satisfy the schema.
   */
  render(data: Record<string, unknown>, locale?: string, skipValidation = false): RenderedTemplate {
    if (!skipValidation && this.variableSchema) {
      const result = this.variableSchema.safeParse(data);
      if (!result.success) throw new TemplateValidationException(result.error);
    }

    const localeMap = locale && this.translations ? (this.translations[locale] ?? null) : null;

    const rendered = new Map<string, string>();
    for (const block of this.blocks) {
      const tpl = this.env.compile(block.type, block.source);
      rendered.set(block.name, tpl({ ...data, [CTX_TRANSLATIONS]: localeMap }));
    }
    return new RenderedTemplate(rendered);
  }

  /**
   * Assembles a template from blocks supplied in code.
   *
   * @param options - The blocks and optional translations, schema and cache.
   * @returns The template.
   * @throws If `options.blocks` is empty.
   */
  static create(options: TemplateOptions): Template {
    if (options.blocks.length === 0) throw new Error("at least one block is required");
    return new Template(
      options.blocks,
      options.translations ?? null,
      options.variables ?? null,
      options.cache ?? new TemplateCache(),
    );
  }

  /**
   * Loads a template from a directory, reading `template.json`, the `.hbs` source
   * of each declared block, and an optional `translations.json`.
   *
   * @param directory - The template's directory.
   * @param cache - Cache for compiled blocks; a new one is created if omitted.
   * @returns The loaded template.
   * @throws If a required file is missing or cannot be read.
   */
  static async fromDirectory(directory: string, cache?: TemplateCache): Promise<Template> {
    const resolvedCache = cache ?? new TemplateCache();
    const descriptor = JSON.parse(
      await readFile(join(directory, "template.json"), "utf8"),
    ) as Record<string, unknown>;
    const blocks = await loadBlocks(descriptor, (name) =>
      readFile(join(directory, `${name}.hbs`), "utf8"),
    );
    const translations = await readOptionalJson<LocaleTranslations>(
      join(directory, "translations.json"),
    );
    const variableSchema = descriptor.variables ? buildVariableSchema(descriptor.variables) : null;
    return new Template(blocks, translations, variableSchema, resolvedCache);
  }
}

async function readOptionalJson<T>(path: string): Promise<T | null> {
  try {
    await access(path);
    return JSON.parse(await readFile(path, "utf8")) as T;
  } catch {
    return null;
  }
}

async function loadBlocks(
  descriptor: Record<string, unknown>,
  readSource: (name: string) => Promise<string>,
): Promise<TemplateBlock[]> {
  const specs = descriptor.blocks as Array<Record<string, string>>;
  if (!Array.isArray(specs)) throw new Error("template.json must contain a 'blocks' array");
  return Promise.all(
    specs.map(async (spec) => ({
      name: spec.name,
      type: spec.type.toLowerCase() as BlockType,
      source: await readSource(spec.name),
    })),
  );
}
