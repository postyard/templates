package eu.postyard.templates;

import static org.junit.jupiter.api.Assertions.*;

import com.github.jknack.handlebars.HandlebarsException;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TemplateTest {

  @Nested
  class BuilderTests {

    @Test
    void requiresAtLeastOneBlock() {
      assertThrows(IllegalStateException.class, () -> Template.builder().build());
    }
  }

  @Nested
  class RenderTests {

    @Test
    void rendersVariablesInAllBlocks() throws IOException {
      Template template =
          Template.builder()
              .block("html", BlockType.HTML, "<h1>Hello {{name}}!</h1>")
              .block("text", BlockType.TEXT, "Hello {{name}}!")
              .build();

      RenderedTemplate result = template.render(Map.of("name", "World"));

      assertEquals("<h1>Hello World!</h1>", result.get("html"));
      assertEquals("Hello World!", result.get("text"));
    }

    @Test
    void blocksMapContainsAllBlocks() throws IOException {
      Template template =
          Template.builder()
              .block("title", BlockType.TEXT, "Hi {{name}}")
              .block("body", BlockType.TEXT, "Welcome {{name}}")
              .build();

      RenderedTemplate result = template.render(Map.of("name", "Alice"));

      assertEquals(Map.of("title", "Hi Alice", "body", "Welcome Alice"), result.blocks());
    }

    @Test
    void translateBlockRendersDefaultContentWhenNoTranslations() throws IOException {
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"greeting\"}}}}Hello, {{name}}!{{{{/translate}}}}")
              .block(
                  "text",
                  BlockType.TEXT,
                  "{{{{translate this id=\"greeting\"}}}}Hello, {{name}}!{{{{/translate}}}}")
              .build();

      RenderedTemplate result = template.render(Map.of("name", "World"));

      assertEquals("Hello, World!", result.get("html"));
      assertEquals("Hello, World!", result.get("text"));
    }

    @Test
    void translateBlockUsesTranslationForRequestedLocale() throws IOException {
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"greeting\"}}}}Hello!{{{{/translate}}}}")
              .block(
                  "text",
                  BlockType.TEXT,
                  "{{{{translate this id=\"greeting\"}}}}Hello!{{{{/translate}}}}")
              .translations(
                  Map.of(
                      "en_US", Map.of("greeting", "Hello!"),
                      "nl_NL", Map.of("greeting", "Hallo!")))
              .build();

      assertEquals("Hello!", template.render(Map.of(), "en_US").get("html"));
      assertEquals("Hallo!", template.render(Map.of(), "nl_NL").get("html"));
    }

    @Test
    void translateBlockResolvesVariablesInsideTranslation() throws IOException {
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"greeting\"}}}}Hello, {{name}}!{{{{/translate}}}}")
              .block(
                  "text",
                  BlockType.TEXT,
                  "{{{{translate this id=\"greeting\"}}}}Hello, {{name}}!{{{{/translate}}}}")
              .translations(Map.of("nl_NL", Map.of("greeting", "Hallo, {{name}}!")))
              .build();

      assertEquals("Hallo, Alice!", template.render(Map.of("name", "Alice"), "nl_NL").get("html"));
    }

    @Test
    void translateBlockFallsBackToDefaultContentWhenIdMissing() throws IOException {
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"unknown\"}}}}Fallback {{name}}{{{{/translate}}}}")
              .block(
                  "text",
                  BlockType.TEXT,
                  "{{{{translate this id=\"unknown\"}}}}Fallback {{name}}{{{{/translate}}}}")
              .translations(Map.of("en_US", Map.of("other", "value")))
              .build();

      assertEquals("Fallback World", template.render(Map.of("name", "World"), "en_US").get("html"));
    }

    @Test
    void translateBlockFallsBackToDefaultContentWhenNoLocale() throws IOException {
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"greeting\"}}}}Hello, {{name}}!{{{{/translate}}}}")
              .block(
                  "text",
                  BlockType.TEXT,
                  "{{{{translate this id=\"greeting\"}}}}Hello, {{name}}!{{{{/translate}}}}")
              .translations(Map.of("en_US", Map.of("greeting", "Hi!")))
              .build();

      assertEquals("Hello, World!", template.render(Map.of("name", "World")).get("html"));
    }

    @Test
    void translateBlockUsesImplicitSha256IdWhenNoIdProvided() throws IOException {
      Template explicitId =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"hello\"}}}}Hello{{{{/translate}}}}")
              .block("text", BlockType.TEXT, "")
              .translations(Map.of("nl_NL", Map.of("hello", "Hallo")))
              .build();

      Template implicitId =
          Template.builder()
              .block("html", BlockType.HTML, "{{{{translate this}}}}Hello{{{{/translate}}}}")
              .block("text", BlockType.TEXT, "")
              // key is SHA-256("Hello")
              .translations(
                  Map.of(
                      "nl_NL",
                      Map.of(
                          "185f8db32271fe25f561a6fc938b2e264306ec304eda518007d1764826381969",
                          "Hallo")))
              .build();

      assertEquals(
          explicitId.render(Map.of(), "nl_NL").get("html"),
          implicitId.render(Map.of(), "nl_NL").get("html"));
    }

    @Test
    void nestedTranslateBlockInTranslationIsResolved() throws IOException {
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"outer\"}}}}default{{{{/translate}}}}")
              .block("text", BlockType.TEXT, "")
              .translations(
                  Map.of(
                      "en_US",
                      Map.of(
                          "outer",
                              "{{{{translate this id=\"inner\"}}}}inner default{{{{/translate}}}}",
                          "inner", "resolved")))
              .build();

      assertEquals("resolved", template.render(Map.of(), "en_US").get("html"));
    }

    @Test
    void cyclicTranslateBlockThrowsIllegalStateException() {
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"cycle\"}}}}default{{{{/translate}}}}")
              .block("text", BlockType.TEXT, "")
              .translations(
                  Map.of(
                      "en_US",
                      Map.of("cycle", "{{{{translate this id=\"cycle\"}}}}oops{{{{/translate}}}}")))
              .build();

      HandlebarsException ex =
          assertThrows(HandlebarsException.class, () -> template.render(Map.of(), "en_US"));
      assertInstanceOf(IllegalStateException.class, ex.getCause());
      assertTrue(ex.getCause().getMessage().contains("cycle"));
    }

    @Test
    void lookupHelperIsDisabled() {
      Template template =
          Template.builder()
              .block("html", BlockType.HTML, "{{lookup items 0}}")
              .block("text", BlockType.TEXT, "{{lookup items 0}}")
              .build();

      HandlebarsException ex =
          assertThrows(
              HandlebarsException.class,
              () -> template.render(Map.of("items", new String[] {"a", "b"})));
      assertInstanceOf(UnsupportedOperationException.class, ex.getCause());
    }
  }

  @Nested
  class CachingTests {

    @Test
    void templatesAreCachedAfterFirstRender() throws IOException {
      TemplateCache cache = new TemplateCache(TemplateCache.DEFAULT_MAX_BYTES);
      Template template =
          Template.builder()
              .block("html", BlockType.HTML, "<h1>{{name}}</h1>")
              .block("text", BlockType.TEXT, "{{name}}")
              .cache(cache)
              .build();

      assertEquals(0, cache.size());
      template.render(Map.of("name", "Alice"));
      assertEquals(2, cache.size()); // html block + text block
    }

    @Test
    void translationContentIsCachedOnFirstUse() throws IOException {
      TemplateCache cache = new TemplateCache(TemplateCache.DEFAULT_MAX_BYTES);
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"msg\"}}}}Hi {{name}}!{{{{/translate}}}}")
              .block("text", BlockType.TEXT, "")
              .translations(Map.of("en_US", Map.of("msg", "Hello {{name}}!")))
              .cache(cache)
              .build();

      template.render(Map.of("name", "Alice"), "en_US");
      int sizeAfterFirst = cache.size(); // html block + text block + translation value

      template.render(Map.of("name", "Bob"), "en_US");
      assertEquals(sizeAfterFirst, cache.size()); // no new compilations
    }

    @Test
    void cacheIsSharedAcrossMultipleTemplates() throws IOException {
      TemplateCache sharedCache = new TemplateCache(TemplateCache.DEFAULT_MAX_BYTES);
      String sharedHtml = "<h1>{{name}}</h1>";

      Template t1 =
          Template.builder()
              .block("html", BlockType.HTML, sharedHtml)
              .block("text", BlockType.TEXT, "{{name}}")
              .cache(sharedCache)
              .build();

      Template t2 =
          Template.builder()
              .block("html", BlockType.HTML, sharedHtml) // identical content — same cache entry
              .block("text", BlockType.TEXT, "Different text")
              .cache(sharedCache)
              .build();

      t1.render(Map.of("name", "Alice"));
      int sizeAfterFirst = sharedCache.size();

      t2.render(Map.of("name", "Bob"));
      // html is already cached; only "Different text" is new
      assertEquals(sizeAfterFirst + 1, sharedCache.size());
    }

    @Test
    void renderStillWorksWhenContentExceedsCacheMaxSize() throws IOException {
      TemplateCache tinyCache = new TemplateCache(4); // too small for any template
      Template template =
          Template.builder()
              .block("html", BlockType.HTML, "<h1>{{name}}</h1>")
              .block("text", BlockType.TEXT, "{{name}}")
              .cache(tinyCache)
              .build();

      RenderedTemplate result = template.render(Map.of("name", "Alice"));

      assertEquals("<h1>Alice</h1>", result.get("html"));
      assertEquals(0, tinyCache.size()); // nothing cached, but no crash
    }

    @Test
    void subsequentRendersWithDifferentLocalesShareCachedTemplates() throws IOException {
      TemplateCache cache = new TemplateCache(TemplateCache.DEFAULT_MAX_BYTES);
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"greeting\"}}}}Hi!{{{{/translate}}}}")
              .block("text", BlockType.TEXT, "")
              .translations(
                  Map.of(
                      "en_US", Map.of("greeting", "Hello!"),
                      "nl_NL", Map.of("greeting", "Hallo!")))
              .cache(cache)
              .build();

      template.render(Map.of(), "en_US");
      int sizeAfterEnglish = cache.size();

      // Dutch translation "Hallo!" is a new content string — adds one entry
      template.render(Map.of(), "nl_NL");
      assertEquals(sizeAfterEnglish + 1, cache.size());

      // Re-rendering either locale adds nothing new
      template.render(Map.of(), "en_US");
      template.render(Map.of(), "nl_NL");
      assertEquals(sizeAfterEnglish + 1, cache.size());
    }
  }

  @Nested
  class EscapingTests {

    @Test
    void htmlBlockEscapesVariablesByDefault() throws IOException {
      Template template =
          Template.builder()
              .block("html", BlockType.HTML, "<p>{{content}}</p>")
              .block("text", BlockType.TEXT, "{{content}}")
              .build();

      RenderedTemplate result = template.render(Map.of("content", "<b>bold</b> & \"quoted\""));

      assertEquals("<p>&lt;b&gt;bold&lt;/b&gt; &amp; &quot;quoted&quot;</p>", result.get("html"));
      // text blocks do not HTML-escape
      assertEquals("<b>bold</b> & \"quoted\"", result.get("text"));
    }

    @Test
    void tripleStacheAllowsRawHtmlInHtmlBlock() throws IOException {
      Template template =
          Template.builder().block("html", BlockType.HTML, "<p>{{{content}}}</p>").build();

      RenderedTemplate result = template.render(Map.of("content", "<b>bold</b>"));

      assertEquals("<p><b>bold</b></p>", result.get("html"));
    }

    @Test
    void variablesInsideTranslationValueAreHtmlEscapedInHtmlBlock() throws IOException {
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"msg\"}}}}Hi {{name}}{{{{/translate}}}}")
              .block("text", BlockType.TEXT, "")
              .translations(Map.of("en_US", Map.of("msg", "Hello {{name}}")))
              .build();

      RenderedTemplate result = template.render(Map.of("name", "<script>xss</script>"), "en_US");

      assertEquals("Hello &lt;script&gt;xss&lt;/script&gt;", result.get("html"));
    }

    @Test
    void tripleStacheInsideTranslationValueAllowsRawHtml() throws IOException {
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"msg\"}}}}Hi {{{name}}}{{{{/translate}}}}")
              .block("text", BlockType.TEXT, "")
              .translations(Map.of("en_US", Map.of("msg", "Hello {{{name}}}")))
              .build();

      RenderedTemplate result = template.render(Map.of("name", "<b>World</b>"), "en_US");

      assertEquals("Hello <b>World</b>", result.get("html"));
    }

    @Test
    void variablesInsideTranslationDefaultContentAreHtmlEscapedInHtmlBlock() throws IOException {
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"msg\"}}}}Hi {{name}}{{{{/translate}}}}")
              .block("text", BlockType.TEXT, "")
              .build();

      RenderedTemplate result = template.render(Map.of("name", "<b>World</b>"));

      assertEquals("Hi &lt;b&gt;World&lt;/b&gt;", result.get("html"));
    }

    @Test
    void tripleStacheInsideTranslationDefaultContentAllowsRawHtml() throws IOException {
      Template template =
          Template.builder()
              .block(
                  "html",
                  BlockType.HTML,
                  "{{{{translate this id=\"msg\"}}}}<em>{{{name}}}</em>{{{{/translate}}}}")
              .block("text", BlockType.TEXT, "")
              .build();

      RenderedTemplate result = template.render(Map.of("name", "<b>World</b>"));

      assertEquals("<em><b>World</b></em>", result.get("html"));
    }
  }

  @Nested
  class ClasspathLoadingTests {

    @Test
    void loadsTemplateFromClasspath() throws IOException {
      assertNotNull(Template.fromClasspath("greeting"));
    }

    @Test
    void throwsWhenTemplateNotFound() {
      assertThrows(IOException.class, () -> Template.fromClasspath("nonexistent-template"));
    }
  }
}
