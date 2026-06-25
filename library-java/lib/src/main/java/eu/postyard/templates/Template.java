package eu.postyard.templates;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jknack.handlebars.Context;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A message template made up of one or more named blocks (for example an HTML body, a plain-text
 * body and a subject), with optional per-locale translations and an optional schema describing the
 * variables it expects.
 *
 * <p>Each block is written in Handlebars. An {@link BlockType#HTML} block escapes interpolated
 * values; a {@link BlockType#TEXT} block emits them verbatim. A {@code translate} helper resolves
 * localized strings for the requested locale, falling back to the text written inline in the block.
 * Rendering applies a data map to every block and returns a {@link RenderedTemplate} holding each
 * block's output.
 *
 * <p>A template is loaded from the classpath ({@link #fromClasspath}), from the filesystem ({@link
 * #fromDirectory}), or assembled in code ({@link #builder}). In each case it is described by a
 * {@code template.json} declaring its blocks and, optionally, its variables, accompanied by a
 * {@code .hbs} source per block and an optional {@code translations.json}.
 *
 * <p>Instances are immutable and may be rendered concurrently.
 */
public final class Template {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final LinkedHashMap<String, BlockType> blockTypes;
  private final Map<String, String> blockSources;
  private final Map<String, Map<String, String>> translations;
  private final VariableSchema variableSchema;
  private final TemplateEnvironment env;

  private Template(
      LinkedHashMap<String, BlockType> blockTypes,
      Map<String, String> blockSources,
      Map<String, Map<String, String>> translations,
      VariableSchema variableSchema,
      TemplateCache cache) {
    this.blockTypes = blockTypes;
    this.blockSources = blockSources;
    this.translations = translations;
    this.variableSchema = variableSchema;
    this.env = cache.environment();
  }

  /**
   * Checks {@code data} against this template's variable schema without rendering.
   *
   * @param data the data to validate
   * @return one message per problem, each prefixed with the dotted path to the offending field;
   *     empty if the data is valid or the template declares no schema
   */
  public List<String> validate(Map<String, Object> data) {
    if (variableSchema == null) return List.of();
    return variableSchema.validate(data);
  }

  /** {@return this template's variable schema, or empty if it declares none} */
  public Optional<VariableSchema> variables() {
    return Optional.ofNullable(variableSchema);
  }

  /** {@return the names of this template's blocks, in render order} */
  public List<String> blockNames() {
    return List.copyOf(blockTypes.keySet());
  }

  /**
   * Renders every block with the given data, without a locale, after validating the data.
   *
   * @param data the variables to render with
   * @return the rendered output of each block
   * @throws TemplateValidationException if the data does not satisfy the schema
   * @throws IOException if a block fails to compile or render
   */
  public RenderedTemplate render(Map<String, Object> data) throws IOException {
    return render(data, null, false);
  }

  /**
   * Renders every block with the given data, resolving translations for the given locale, after
   * validating the data.
   *
   * @param data the variables to render with
   * @param locale the locale whose translations to use, or {@code null} to use the text written
   *     inline in each block
   * @return the rendered output of each block
   * @throws TemplateValidationException if the data does not satisfy the schema
   * @throws IOException if a block fails to compile or render
   */
  public RenderedTemplate render(Map<String, Object> data, String locale) throws IOException {
    return render(data, locale, false);
  }

  /**
   * Renders every block with the given data and locale.
   *
   * @param data the variables to render with
   * @param locale the locale whose translations to use, or {@code null} to use the text written
   *     inline in each block
   * @param skipValidation if {@code true}, the data is not checked against the schema
   * @return the rendered output of each block
   * @throws TemplateValidationException if validation runs and the data does not satisfy the schema
   * @throws IOException if a block fails to compile or render
   */
  public RenderedTemplate render(Map<String, Object> data, String locale, boolean skipValidation)
      throws IOException {
    if (!skipValidation && variableSchema != null) {
      List<String> errors = variableSchema.validate(data);
      if (!errors.isEmpty()) {
        throw new TemplateValidationException(errors);
      }
    }

    Map<String, String> localeMap =
        (translations != null && locale != null) ? translations.get(locale) : null;

    Context ctx = Context.newContext(data);
    ctx.data(TemplateEnvironment.CTX_TRANSLATIONS, localeMap);

    Map<String, String> rendered = new LinkedHashMap<>();
    for (Map.Entry<String, BlockType> entry : blockTypes.entrySet()) {
      String name = entry.getKey();
      com.github.jknack.handlebars.Template tpl =
          env.compile(entry.getValue(), blockSources.get(name));
      rendered.put(name, tpl.apply(ctx));
    }
    return new RenderedTemplate(rendered);
  }

  /**
   * Loads a template from the classpath, using a new cache.
   *
   * @param name the resource path of the template's directory, without a trailing slash
   * @return the loaded template
   * @throws IOException if a required resource is missing or cannot be read
   */
  public static Template fromClasspath(String name) throws IOException {
    return fromClasspath(name, new TemplateCache(TemplateCache.DEFAULT_MAX_BYTES));
  }

  /**
   * Loads a template from the classpath, reading {@code <name>/template.json}, the {@code .hbs}
   * source of each declared block, and an optional {@code <name>/translations.json}.
   *
   * @param name the resource path of the template's directory, without a trailing slash
   * @param cache the cache that stores the template's compiled blocks
   * @return the loaded template
   * @throws IOException if a required resource is missing or cannot be read
   */
  public static Template fromClasspath(String name, TemplateCache cache) throws IOException {
    ClassLoader cl = Template.class.getClassLoader();
    String base = name + "/";

    Map<String, Object> descriptor = parseDescriptor(loadResource(cl, base + "template.json"));

    LinkedHashMap<String, BlockType> blockTypes = new LinkedHashMap<>();
    Map<String, String> blockSources = new LinkedHashMap<>();
    for (Map<String, String> spec : extractBlockSpecs(descriptor)) {
      String blockName = spec.get("name");
      blockTypes.put(blockName, BlockType.valueOf(spec.get("type").toUpperCase()));
      blockSources.put(blockName, loadResource(cl, base + blockName + ".hbs"));
    }

    Map<String, Map<String, String>> translations =
        loadOptionalResource(cl, base + "translations.json")
            .map(json -> parseJson(json, new TypeReference<Map<String, Map<String, String>>>() {}))
            .orElse(null);

    VariableSchema variableSchema = extractVariableSchema(descriptor);

    return new Template(blockTypes, blockSources, translations, variableSchema, cache);
  }

  /**
   * Loads a template from a directory, using a new cache.
   *
   * @param directory the template's directory
   * @return the loaded template
   * @throws IOException if a required file is missing or cannot be read
   */
  public static Template fromDirectory(Path directory) throws IOException {
    return fromDirectory(directory, new TemplateCache(TemplateCache.DEFAULT_MAX_BYTES));
  }

  /**
   * Loads a template from a directory, reading {@code template.json}, the {@code .hbs} source of
   * each declared block, and an optional {@code translations.json}.
   *
   * @param directory the template's directory
   * @param cache the cache that stores the template's compiled blocks
   * @return the loaded template
   * @throws IOException if a required file is missing or cannot be read
   */
  public static Template fromDirectory(Path directory, TemplateCache cache) throws IOException {
    Map<String, Object> descriptor =
        parseDescriptor(Files.readString(directory.resolve("template.json")));

    LinkedHashMap<String, BlockType> blockTypes = new LinkedHashMap<>();
    Map<String, String> blockSources = new LinkedHashMap<>();
    for (Map<String, String> spec : extractBlockSpecs(descriptor)) {
      String blockName = spec.get("name");
      blockTypes.put(blockName, BlockType.valueOf(spec.get("type").toUpperCase()));
      blockSources.put(blockName, Files.readString(directory.resolve(blockName + ".hbs")));
    }

    Path translationsPath = directory.resolve("translations.json");
    Map<String, Map<String, String>> translations =
        Files.exists(translationsPath)
            ? parseJson(
                Files.readString(translationsPath),
                new TypeReference<Map<String, Map<String, String>>>() {})
            : null;

    VariableSchema variableSchema = extractVariableSchema(descriptor);

    return new Template(blockTypes, blockSources, translations, variableSchema, cache);
  }

  /** {@return a builder for assembling a template in code} */
  public static Builder builder() {
    return new Builder();
  }

  private static Map<String, Object> parseDescriptor(String json) {
    return parseJson(json, new TypeReference<Map<String, Object>>() {});
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, String>> extractBlockSpecs(Map<String, Object> descriptor) {
    return (List<Map<String, String>>) descriptor.get("blocks");
  }

  @SuppressWarnings("unchecked")
  private static VariableSchema extractVariableSchema(Map<String, Object> descriptor) {
    Object raw = descriptor.get("variables");
    if (!(raw instanceof Map<?, ?> map)) return null;
    return VariableSchema.fromMap((Map<String, Object>) map);
  }

  private static String loadResource(ClassLoader cl, String path) throws IOException {
    try (InputStream is = cl.getResourceAsStream(path)) {
      if (is == null) {
        throw new IOException("Template resource not found: " + path);
      }
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static Optional<String> loadOptionalResource(ClassLoader cl, String path) {
    try (InputStream is = cl.getResourceAsStream(path)) {
      if (is == null) return Optional.empty();
      return Optional.of(new String(is.readAllBytes(), StandardCharsets.UTF_8));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static <T> T parseJson(String json, TypeReference<T> type) {
    try {
      return OBJECT_MAPPER.readValue(json, type);
    } catch (IOException e) {
      throw new IllegalArgumentException("Failed to parse JSON: " + e.getMessage(), e);
    }
  }

  /**
   * Assembles a {@link Template} from blocks, translations and a schema supplied in code, as an
   * alternative to loading one from the classpath or filesystem.
   */
  public static final class Builder {

    private final LinkedHashMap<String, BlockType> blockTypes = new LinkedHashMap<>();
    private final Map<String, String> blockSources = new LinkedHashMap<>();
    private Map<String, Map<String, String>> translations;
    private VariableSchema variableSchema;
    private TemplateCache cache;

    private Builder() {}

    /**
     * Adds a block. Blocks are rendered in the order they are added.
     *
     * @param name the block's name
     * @param type whether the block is HTML or text
     * @param source the block's Handlebars source
     * @return this builder
     */
    public Builder block(String name, BlockType type, String source) {
      blockTypes.put(name, type);
      blockSources.put(name, source);
      return this;
    }

    /**
     * Sets the per-locale translations, keyed first by locale and then by translation id.
     *
     * @param translations the translations
     * @return this builder
     */
    public Builder translations(Map<String, Map<String, String>> translations) {
      this.translations = translations;
      return this;
    }

    /**
     * Sets the schema used to validate render data.
     *
     * @param variableSchema the schema
     * @return this builder
     */
    public Builder variables(VariableSchema variableSchema) {
      this.variableSchema = variableSchema;
      return this;
    }

    /**
     * Sets the cache that stores compiled blocks. If not set, the built template gets its own new
     * cache.
     *
     * @param cache the cache to use
     * @return this builder
     */
    public Builder cache(TemplateCache cache) {
      this.cache = cache;
      return this;
    }

    /**
     * Builds the template.
     *
     * @return the assembled template
     * @throws IllegalStateException if no block was added
     */
    public Template build() {
      if (blockTypes.isEmpty()) throw new IllegalStateException("at least one block is required");
      TemplateCache resolvedCache =
          cache != null ? cache : new TemplateCache(TemplateCache.DEFAULT_MAX_BYTES);
      return new Template(
          new LinkedHashMap<>(blockTypes),
          new LinkedHashMap<>(blockSources),
          translations,
          variableSchema,
          resolvedCache);
    }
  }
}
