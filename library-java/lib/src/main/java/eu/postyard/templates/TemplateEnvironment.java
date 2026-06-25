package eu.postyard.templates;

import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The Handlebars setup shared by every {@link Template} that uses a given {@link TemplateCache}:
 * one {@link Handlebars} instance for HTML blocks and one for text blocks, the custom helpers, and
 * the compilation that reads from and writes to the cache. Holding this here means the Handlebars
 * instances and helpers are built once per cache rather than once per template.
 *
 * <p>The HTML instance escapes interpolated values; the text instance uses {@link
 * EscapingStrategy#NOOP} so values pass through verbatim. Two helpers are registered:
 *
 * <ul>
 *   <li>{@code lookup} is disabled and throws if used.
 *   <li>{@code translate} resolves a localized string by id from the locale supplied in the render
 *       context, falling back to the block's inlined text, then compiles and renders that string so
 *       a translation may itself contain Handlebars expressions. Re-entering the same translation
 *       id while it is already being rendered is rejected as a cycle.
 * </ul>
 */
final class TemplateEnvironment {

  /** Key under which the active locale's translations are passed in the render context. */
  static final String CTX_TRANSLATIONS = "_translationsData";

  private static final ThreadLocal<Set<String>> ACTIVE_TRANSLATION_IDS =
      ThreadLocal.withInitial(HashSet::new);

  private final TemplateCache cache;
  private final Handlebars htmlHandlebars;
  private final Handlebars textHandlebars;

  TemplateEnvironment(TemplateCache cache) {
    this.cache = cache;
    this.htmlHandlebars = buildHandlebars(BlockType.HTML);
    this.textHandlebars = buildHandlebars(BlockType.TEXT);
  }

  /**
   * Compiles a block of the given type, returning a cached result when the same source has been
   * compiled before.
   *
   * @param type the block's type, which selects the HTML or text instance
   * @param source the block's Handlebars source
   * @return the compiled template
   * @throws IOException if the source cannot be compiled
   */
  com.github.jknack.handlebars.Template compile(BlockType type, String source) throws IOException {
    Handlebars hb = type == BlockType.HTML ? htmlHandlebars : textHandlebars;
    return cachedCompile(hb, type.name().toLowerCase() + ":", source);
  }

  private Handlebars buildHandlebars(BlockType type) {
    Handlebars hb = new Handlebars();
    if (type == BlockType.TEXT) {
      hb.with(EscapingStrategy.NOOP);
    }
    String cachePrefix = type.name().toLowerCase() + ":";

    hb.registerHelper(
        "lookup",
        (Object ctx, com.github.jknack.handlebars.Options opts) -> {
          throw new UnsupportedOperationException("The 'lookup' helper is disabled");
        });

    hb.registerHelper(
        "translate",
        (Object value, com.github.jknack.handlebars.Options opts) -> {
          String defaultContent = opts.fn(value).toString();
          String translationId = opts.hash("id");
          if (translationId == null) {
            translationId = Hashing.sha256(defaultContent);
          }

          Set<String> active = ACTIVE_TRANSLATION_IDS.get();
          if (!active.add(translationId)) {
            throw new IllegalStateException(
                "Circular translate reference detected: '"
                    + translationId
                    + "' is already being rendered");
          }
          try {
            @SuppressWarnings("unchecked")
            Map<String, String> localeMap = (Map<String, String>) opts.data(CTX_TRANSLATIONS);
            String content = resolveTranslation(localeMap, translationId, defaultContent);
            return new Handlebars.SafeString(
                cachedCompile(hb, cachePrefix, content).apply(opts.context));
          } finally {
            active.remove(translationId);
          }
        });

    return hb;
  }

  private com.github.jknack.handlebars.Template cachedCompile(
      Handlebars hb, String cachePrefix, String content) throws IOException {
    String hash = cachePrefix + Hashing.sha256(content);
    com.github.jknack.handlebars.Template cached = cache.get(hash);
    if (cached != null) return cached;
    com.github.jknack.handlebars.Template compiled = hb.compileInline(content);
    cache.put(hash, content, compiled);
    return compiled;
  }

  private static String resolveTranslation(
      Map<String, String> localeMap, String key, String defaultContent) {
    if (localeMap == null) return defaultContent;
    return localeMap.getOrDefault(key, defaultContent);
  }
}
