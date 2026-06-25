# @postyard/templates

Handlebars-based, multi-locale email & message templating with typed variables and per-locale translations.

This is the TypeScript library. For the shared concepts (templates, blocks, translations) see the [project README](../README.md).

## Install

```sh
pnpm add @postyard/templates
```

Node ESM, requires Node 20+.

## Quick start

Load a template from a directory and render it:

```ts
import { Template } from "@postyard/templates";

const template = await Template.fromDirectory("./templates/welcome");

const rendered = template.render({ name: "Ada" }, "en_US");

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

Omit the locale to use the text written inline in each block instead of a translation.

## Assembling a template in code

```ts
import { BlockType, Template } from "@postyard/templates";

const template = Template.create({
  blocks: [{ name: "subject", type: BlockType.TEXT, source: "Welcome, {{name}}!" }],
});

template.render({ name: "Ada" }).get("subject"); // "Welcome, Ada!"
```

## Loading many templates

`FilesystemTemplateManager` loads every template directly beneath a directory and looks them up by name:

```ts
import { FilesystemTemplateManager } from "@postyard/templates";

const manager = await FilesystemTemplateManager.of("./templates");
const welcome = manager.get("welcome");
```

## Validation

When a template declares `variables`, render data is validated against the schema first; invalid data throws a `TemplateValidationException`. Check data without rendering with `validate`, which returns one `"path: message"` string per problem:

```ts
template.validate({ name: 123 }); // ["name: Invalid input: expected string, received number"]
```

## API

- `Template` — `.fromDirectory()`, `.create()`, `.render()`, `.validate()`, `.variables()`, `.locales()`, `.blockNames()`
- `RenderedTemplate` — `.get()`, `.blockNames()`
- `FilesystemTemplateManager` — `.of()`, `.get()`, `.names()`
- `TemplateCache` — caches compiled blocks; pass one to share it across templates
- `TemplateValidationException`, `formatIssues`
- `buildVariableSchema` and types `VariableSchema`, `TemplateBlock`, `TemplateOptions`, `LocaleTranslations`

## License

[MIT](../LICENSE)
