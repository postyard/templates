import { existsSync } from "node:fs";
import { readdir, readFile } from "node:fs/promises";
import { join, resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { FilesystemTemplateManager } from "../src/FilesystemTemplateManager.js";

const FIXTURES_DIR = resolve(import.meta.dirname, "../../test-fixtures");

const manager = await FilesystemTemplateManager.of(FIXTURES_DIR);

const templateDirs = (await readdir(FIXTURES_DIR, { withFileTypes: true }))
  .filter((e) => e.isDirectory() && existsSync(join(FIXTURES_DIR, e.name, "expected")))
  .map((e) => e.name);

describe("fixtures", () => {
  for (const templateName of templateDirs) {
    const expectedDir = join(FIXTURES_DIR, templateName, "expected");

    describe(templateName, async () => {
      const localeDirs = (await readdir(expectedDir, { withFileTypes: true }))
        .filter((e) => e.isDirectory())
        .map((e) => e.name);

      for (const localeName of localeDirs) {
        const locale = localeName === "default" ? undefined : localeName;
        const label = localeName === "default" ? "default locale" : localeName;

        it(label, async () => {
          const template = manager.get(templateName);
          const exampleData = JSON.parse(
            await readFile(join(FIXTURES_DIR, templateName, "example.json"), "utf8"),
          ) as Record<string, unknown>;
          const rendered = template.render(exampleData, locale, true);

          const localeExpectedDir = join(expectedDir, localeName);
          const expectedFiles = await readdir(localeExpectedDir);

          for (const file of expectedFiles) {
            const blockName = file.replace(/\.(html|txt)$/, "");
            const expected = await readFile(join(localeExpectedDir, file), "utf8");
            expect(rendered.get(blockName)).toBe(expected);
          }
        });
      }
    });
  }
});
