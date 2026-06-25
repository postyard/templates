# postyard-templates (Java)

Handlebars-based, multi-locale email & message templating with typed variables and per-locale translations.

This is the Java library. For the shared concepts (templates, blocks, translations) see the [project README](../README.md).

## Install

Requires Java 21+.

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("eu.postyard:postyard-templates:0.1.0")
}
```

Maven:

```xml
<dependency>
  <groupId>eu.postyard</groupId>
  <artifactId>postyard-templates</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Quick start

Load a template from a directory and render it:

```java
import eu.postyard.templates.Template;
import eu.postyard.templates.RenderedTemplate;
import java.nio.file.Path;
import java.util.Map;

Template template = Template.fromDirectory(Path.of("templates/welcome"));

RenderedTemplate rendered = template.render(Map.of("name", "Ada"), "en_US");

rendered.get("subject"); // "Welcome, Ada!"
rendered.get("html");    // "<h1>Hello, Ada!</h1>\n<p>Welcome to Postyard!</p>"
rendered.get("text");    // "Hello, Ada!\nWelcome to Postyard!"
```

The directory holds a `template.json`, a `.hbs` file per declared block, and an optional `translations.json`:

```json
// template.json
{
  "blocks": [
    { "name": "subject", "type": "text" },
    { "name": "html", "type": "html" },
    { "name": "text", "type": "text" }
  ],
  "variables": {
    "name": { "type": "string" }
  }
}
```

Pass `null` as the locale (or call `render(data)`) to use the text written inline in each block instead of a translation.

## Assembling a template in code

```java
import eu.postyard.templates.BlockType;
import eu.postyard.templates.Template;

Template template = Template.builder()
    .block("subject", BlockType.TEXT, "Welcome, {{name}}!")
    .build();

template.render(Map.of("name", "Ada")).get("subject"); // "Welcome, Ada!"
```

## Loading many templates

Both managers load templates and look them up by name:

```java
import eu.postyard.templates.FilesystemTemplateManager;
import eu.postyard.templates.ClasspathTemplateManager;

var fromDisk = FilesystemTemplateManager.of(Path.of("templates"));
var welcome = fromDisk.get("welcome");

var fromClasspath = ClasspathTemplateManager.of("templates");
var reset = fromClasspath.get("password-reset");
```

## Validation

When a template declares `variables`, render data is validated against the schema first; invalid data throws a `TemplateValidationException`. Check data without rendering with `validate`, which returns one message per problem, each prefixed with the dotted path to the offending field:

```java
template.validate(Map.of("name", 123)); // ["name: expected string, got number"]
```

## API

- `Template` — `fromDirectory()`, `fromClasspath()`, `builder()`, `render()`, `validate()`, `variables()`, `blockNames()`
- `RenderedTemplate` — `get()`, `blockNames()`
- `TemplateManager` — `get()`, implemented by `FilesystemTemplateManager` and `ClasspathTemplateManager` (each via `of()`)
- `TemplateCache` — caches compiled blocks; pass one to share it across templates
- `TemplateValidationException`
- `VariableSchema`, `FieldSchema`, `FieldType`

## License

[MIT](../LICENSE)
