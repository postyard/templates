package eu.postyard.templates;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class TemplateFixtureTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @TestFactory
  List<DynamicContainer> fixtures() throws IOException {
    Path fixturesDir = Path.of(System.getProperty("postyard.fixtures.dir"));
    FilesystemTemplateManager manager = FilesystemTemplateManager.of(fixturesDir);

    List<DynamicContainer> containers = new ArrayList<>();
    try (var stream = Files.list(fixturesDir)) {
      for (Path templateDir : stream.filter(Files::isDirectory).toList()) {
        Path expectedDir = templateDir.resolve("expected");
        if (!Files.exists(expectedDir)) continue;
        String name = templateDir.getFileName().toString();
        containers.add(containerForTemplate(name, manager.get(name), templateDir));
      }
    }
    return containers;
  }

  private DynamicContainer containerForTemplate(String name, Template template, Path templateDir)
      throws IOException {
    Map<String, BlockType> blockTypes = readBlockTypes(templateDir.resolve("template.json"));
    // example.json is a fixture/tooling convention, not part of the template.
    Map<String, Object> exampleData =
        OBJECT_MAPPER.readValue(
            templateDir.resolve("example.json").toFile(), new TypeReference<>() {});
    Path expectedDir = templateDir.resolve("expected");

    List<DynamicContainer> localeCases = new ArrayList<>();
    try (var stream = Files.list(expectedDir)) {
      for (Path localeDir : stream.filter(Files::isDirectory).sorted().toList()) {
        localeCases.add(containerForLocale(template, blockTypes, exampleData, localeDir));
      }
    }
    return DynamicContainer.dynamicContainer(name, localeCases);
  }

  private DynamicContainer containerForLocale(
      Template template,
      Map<String, BlockType> blockTypes,
      Map<String, Object> exampleData,
      Path localeDir)
      throws IOException {
    String localeName = localeDir.getFileName().toString();
    String locale = "default".equals(localeName) ? null : localeName;

    RenderedTemplate rendered = template.render(exampleData, locale, true);

    List<DynamicTest> tests = new ArrayList<>();
    for (Map.Entry<String, BlockType> entry : blockTypes.entrySet()) {
      String blockName = entry.getKey();
      BlockType blockType = entry.getValue();
      String ext = blockType == BlockType.HTML ? ".html" : ".txt";
      Path expectedFile = localeDir.resolve(blockName + ext);

      String expected = Files.readString(expectedFile);
      String actual = rendered.get(blockName);

      tests.add(DynamicTest.dynamicTest(blockName, () -> assertEquals(expected, actual)));
    }
    return DynamicContainer.dynamicContainer(localeName, tests);
  }

  @SuppressWarnings("unchecked")
  private Map<String, BlockType> readBlockTypes(Path templateJson) throws IOException {
    Map<String, Object> descriptor =
        OBJECT_MAPPER.readValue(templateJson.toFile(), new TypeReference<>() {});
    List<Map<String, String>> blocks = (List<Map<String, String>>) descriptor.get("blocks");

    java.util.LinkedHashMap<String, BlockType> result = new java.util.LinkedHashMap<>();
    for (Map<String, String> block : blocks) {
      result.put(block.get("name"), BlockType.valueOf(block.get("type").toUpperCase()));
    }
    return result;
  }
}
