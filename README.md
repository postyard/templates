# Postyard Templates

Handlebars-based, multi-locale email & message templating with typed variables and per-locale translations.

Postyard Templates is a small, dependency-light engine for transactional email and message templating, shipped as two native libraries from one source of truth:

- **[TypeScript](library-typescript/)** — [`@postyard/templates`](https://www.npmjs.com/package/@postyard/templates)
- **[Java](library-java/)** — `eu.postyard:postyard-templates`

Both libraries share the same concepts and produce identical output for the same template and data.

## Concept

A **template** is a folder of [Handlebars](https://handlebarsjs.com/) blocks plus two optional files:

```
welcome/
  template.json        # declares the blocks and an optional variable schema
  subject.hbs          # a block
  html.hbs             # a block
  text.hbs             # a block
  translations.json    # optional per-locale strings
```

- **Blocks** are the named pieces of a message — typically `subject`, `html` and `text`. Each block is a Handlebars source file. A block is either `html` (interpolated values are HTML-escaped) or `text` (values are emitted verbatim).
- **`template.json`** declares the blocks, in render order, and may declare a **variable schema** describing the data the template expects.
- **`translations.json`** holds per-locale strings, keyed first by locale and then by translation id. A `translate` helper resolves a string for the requested locale, falling back to the text written inline in the block.

Rendering applies a data object to every block and returns one rendered string per block. If the template declares a schema, the data is validated first.

```json
// welcome/template.json
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

```handlebars
{{! welcome/html.hbs }}
<h1>Hello, {{name}}!</h1>
<p>{{{{translate this id="body.message"}}}}Welcome to Postyard!{{{{/translate}}}}</p>
```

```json
// welcome/translations.json
{
  "en_US": { "body.message": "Welcome to Postyard!" },
  "nl_NL": { "body.message": "Welkom bij Postyard!" }
}
```

## Libraries

| | TypeScript | Java |
| --- | --- | --- |
| Package | [`@postyard/templates`](library-typescript/) | [`eu.postyard:postyard-templates`](library-java/) |
| Runtime | Node (ESM) | Java 21 |
| Loading | filesystem | filesystem, classpath |
| I/O | async | synchronous |

See each library's README for installation and a quick-start render example.

## License

[MIT](LICENSE)
