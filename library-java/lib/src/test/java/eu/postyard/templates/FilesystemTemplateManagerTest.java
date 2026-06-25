package eu.postyard.templates;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemTemplateManagerTest {

  @TempDir Path tempDir;

  private void writeTemplate(String name, String html, String text) throws IOException {
    Path dir = tempDir.resolve(name);
    Files.createDirectories(dir);
    Files.writeString(
        dir.resolve("template.json"),
        "{\"blocks\":[{\"name\":\"html\",\"type\":\"html\"},{\"name\":\"text\",\"type\":\"text\"}]}");
    Files.writeString(dir.resolve("html.hbs"), html);
    Files.writeString(dir.resolve("text.hbs"), text);
  }

  @Test
  void getLoadsAndRendersTemplateFromFilesystem() throws IOException {
    writeTemplate("greeting", "<h1>Hello, {{name}}!</h1>", "Hello, {{name}}!");
    FilesystemTemplateManager manager = FilesystemTemplateManager.of(tempDir);

    RenderedTemplate result = manager.get("greeting").render(Map.of("name", "Alice"));

    assertEquals("<h1>Hello, Alice!</h1>", result.get("html"));
    assertEquals("Hello, Alice!", result.get("text"));
  }

  @Test
  void getReturnsSameInstanceOnSecondCall() throws IOException {
    writeTemplate("greeting", "<p>{{msg}}</p>", "{{msg}}");
    FilesystemTemplateManager manager = FilesystemTemplateManager.of(tempDir);

    assertSame(manager.get("greeting"), manager.get("greeting"));
  }

  @Test
  void getLoadsTranslationsFromFilesystem() throws IOException {
    writeTemplate(
        "hello",
        "{{{{translate this id=\"msg\"}}}}Hi!{{{{/translate}}}}",
        "{{{{translate this id=\"msg\"}}}}Hi!{{{{/translate}}}}");
    Files.writeString(
        tempDir.resolve("hello/translations.json"),
        "{\"en_US\":{\"msg\":\"Hello!\"},\"nl_NL\":{\"msg\":\"Hallo!\"}}");

    FilesystemTemplateManager manager = FilesystemTemplateManager.of(tempDir);

    assertEquals("Hello!", manager.get("hello").render(Map.of(), "en_US").get("html"));
    assertEquals("Hallo!", manager.get("hello").render(Map.of(), "nl_NL").get("html"));
  }

  @Test
  void getThrowsOnNonexistentTemplate() throws IOException {
    FilesystemTemplateManager manager = FilesystemTemplateManager.of(tempDir);
    assertThrows(IOException.class, () -> manager.get("nonexistent"));
  }

  @Test
  void twoManagersSharingCacheDoNotGrowCompiledCacheOnSecondRender() throws IOException {
    writeTemplate("msg", "<p>{{text}}</p>", "{{text}}");

    TemplateCache sharedCache = new TemplateCache(TemplateCache.DEFAULT_MAX_BYTES);
    FilesystemTemplateManager manager1 = FilesystemTemplateManager.of(tempDir, sharedCache);
    manager1.get("msg").render(Map.of("text", "hello"));
    int sizeAfterFirstRender = sharedCache.size();
    assertTrue(sizeAfterFirstRender > 0);

    FilesystemTemplateManager manager2 = FilesystemTemplateManager.of(tempDir, sharedCache);
    manager2.get("msg").render(Map.of("text", "world"));

    assertEquals(sizeAfterFirstRender, sharedCache.size());
  }
}
