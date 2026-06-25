package eu.postyard.templates;

import static org.junit.jupiter.api.Assertions.*;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class TemplateCacheTest {

  private static Template compile(String content) throws IOException {
    return new Handlebars().compileInline(content);
  }

  @Test
  void getMissReturnsNull() {
    TemplateCache cache = new TemplateCache(1024);
    assertNull(cache.get("nonexistent"));
  }

  @Test
  void getAfterPutReturnsTemplate() throws IOException {
    TemplateCache cache = new TemplateCache(1024);
    Template template = compile("Hello {{name}}!");
    cache.put("hash1", "Hello {{name}}!", template);
    assertSame(template, cache.get("hash1"));
  }

  @Test
  void sizeAndBytesReflectStoredEntries() throws IOException {
    TemplateCache cache = new TemplateCache(1024);
    String content = "Hello"; // 5 bytes
    cache.put("h1", content, compile(content));

    assertEquals(1, cache.size());
    assertEquals(5, cache.currentBytes());
  }

  @Test
  void duplicatePutIsIgnored() throws IOException {
    TemplateCache cache = new TemplateCache(1024);
    Template first = compile("Hi");
    Template second = compile("Hi");

    cache.put("h1", "Hi", first);
    cache.put("h1", "Hi", second); // duplicate — should be ignored

    assertEquals(1, cache.size());
    assertSame(first, cache.get("h1")); // original is retained
  }

  @Test
  void entryLargerThanMaxSizeIsNotCached() throws IOException {
    TemplateCache cache = new TemplateCache(4); // 4 bytes max
    String big = "Hello"; // 5 bytes
    cache.put("h1", big, compile(big));

    assertNull(cache.get("h1"));
    assertEquals(0, cache.size());
    assertEquals(0, cache.currentBytes());
  }

  @Test
  void evictsOldestInsertedWhenFull() throws IOException {
    // max = 10 bytes; entries: "Hello" (5), "World" (5) fill it exactly
    TemplateCache cache = new TemplateCache(10);
    cache.put("h1", "Hello", compile("Hello")); // 5 bytes
    cache.put("h2", "World", compile("World")); // 5 bytes — full

    // Reading does not affect eviction order (FIFO, not LRU).
    cache.get("h1");

    // Adding a 5-byte entry evicts the oldest-inserted (h1).
    cache.put("h3", "Alice", compile("Alice")); // 5 bytes

    assertNull(cache.get("h1")); // evicted
    assertNotNull(cache.get("h2"));
    assertNotNull(cache.get("h3"));
    assertEquals(10, cache.currentBytes());
  }

  @Test
  void maxContentBytesMustBePositive() {
    assertThrows(IllegalArgumentException.class, () -> new TemplateCache(0));
    assertThrows(IllegalArgumentException.class, () -> new TemplateCache(-1));
  }
}
