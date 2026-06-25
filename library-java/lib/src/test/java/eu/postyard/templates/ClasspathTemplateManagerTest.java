package eu.postyard.templates;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClasspathTemplateManagerTest {

  @Test
  void getLoadsAndRendersTemplateFromClasspath() throws IOException {
    ClasspathTemplateManager manager = ClasspathTemplateManager.of("");

    RenderedTemplate result = manager.get("greeting").render(Map.of("name", "Alice"), null, true);

    assertEquals("Hello, Alice!", result.get("text"));
  }

  @Test
  void getReturnsSameInstanceOnSecondCall() throws IOException {
    ClasspathTemplateManager manager = ClasspathTemplateManager.of("");

    Template first = manager.get("greeting");
    Template second = manager.get("greeting");

    assertSame(first, second);
  }

  @Test
  void twoManagersSharingCacheDoNotGrowCompiledCacheOnSecondRender() throws IOException {
    TemplateCache sharedCache = new TemplateCache(TemplateCache.DEFAULT_MAX_BYTES);

    ClasspathTemplateManager manager1 = ClasspathTemplateManager.of("", sharedCache);
    manager1.get("greeting").render(Map.of("name", "Alice"), null, true);
    int sizeAfterFirstRender = sharedCache.size();
    assertTrue(sizeAfterFirstRender > 0);

    ClasspathTemplateManager manager2 = ClasspathTemplateManager.of("", sharedCache);
    manager2.get("greeting").render(Map.of("name", "Bob"), null, true);

    assertEquals(sizeAfterFirstRender, sharedCache.size());
  }

  @Test
  void getThrowsOnNonexistentTemplate() {
    ClasspathTemplateManager manager = ClasspathTemplateManager.of("");
    assertThrows(IOException.class, () -> manager.get("nonexistent-template"));
  }
}
