import type { TemplateDelegate } from "handlebars";
import Handlebars from "handlebars";
import { BlockType } from "./BlockType.js";
import { sha256 } from "./hashing.js";
import type { TemplateCache } from "./TemplateCache.js";

/** Render-data key under which the active locale's translations are passed. */
export const CTX_TRANSLATIONS = "_translationsData";

type TranslationMap = Record<string, string>;

const ACTIVE_TRANSLATION_IDS = new Set<string>();

/**
 * A pair of Handlebars instances (one for HTML blocks, one for text blocks) plus
 * the helpers, compiling through a {@link TemplateCache}. Obtained from
 * {@link TemplateCache.environment} and shared by every template using that cache.
 */
export class TemplateEnvironment {
  private readonly cache: TemplateCache;
  private readonly htmlEnv: typeof Handlebars;
  private readonly textEnv: typeof Handlebars;

  constructor(cache: TemplateCache) {
    this.cache = cache;
    this.htmlEnv = this.buildEnv(BlockType.HTML);
    this.textEnv = this.buildEnv(BlockType.TEXT);
  }

  private envFor(type: BlockType): typeof Handlebars {
    return type === BlockType.TEXT ? this.textEnv : this.htmlEnv;
  }

  /**
   * Compiles a block, returning a cached result when the same source was
   * compiled before.
   *
   * @param type - The block's type, selecting the HTML or text instance.
   * @param source - The block's Handlebars source.
   * @returns The compiled template.
   */
  compile(type: BlockType, source: string): TemplateDelegate {
    return this.cachedCompile(this.envFor(type), `${type}:`, source, type === BlockType.TEXT);
  }

  private cachedCompile(
    env: typeof Handlebars,
    prefix: string,
    source: string,
    noEscape: boolean,
  ): TemplateDelegate {
    const key = prefix + sha256(source);
    const cached = this.cache.get(key);
    if (cached) return cached;
    const compiled = env.compile(source, { noEscape, ignoreStandalone: true });
    this.cache.put(key, source, compiled);
    return compiled;
  }

  private buildEnv(type: BlockType): typeof Handlebars {
    const env = Handlebars.create();
    const isText = type === BlockType.TEXT;
    const cachePrefix = `${type}:`;

    env.registerHelper("lookup", () => {
      throw new Error("The 'lookup' helper is disabled");
    });

    const self = this;

    env.registerHelper(
      "translate",
      function (this: Record<string, unknown>, _ctx: unknown, options: Handlebars.HelperOptions) {
        const defaultContent = options.fn(this);
        const translationId: string = options.hash.id ?? sha256(defaultContent);

        if (ACTIVE_TRANSLATION_IDS.has(translationId)) {
          throw new Error(
            `Circular translate reference detected: '${translationId}' is already being rendered`,
          );
        }
        ACTIVE_TRANSLATION_IDS.add(translationId);
        try {
          const localeMap = (options.data?.root as Record<string, unknown>)?.[
            CTX_TRANSLATIONS
          ] as TranslationMap | null;
          const content = resolveTranslation(localeMap, translationId, defaultContent);
          const tpl = self.cachedCompile(env, cachePrefix, content, isText);
          const result = tpl(options.data?.root as object);
          return new Handlebars.SafeString(result);
        } finally {
          ACTIVE_TRANSLATION_IDS.delete(translationId);
        }
      },
    );

    return env;
  }
}

function resolveTranslation(
  localeMap: TranslationMap | null,
  key: string,
  defaultContent: string,
): string {
  if (!localeMap) return defaultContent;
  return key in localeMap ? localeMap[key] : defaultContent;
}
