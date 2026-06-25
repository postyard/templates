import { existsSync } from "node:fs";
import { readdir } from "node:fs/promises";
import { join } from "node:path";
import { Template } from "./Template.js";
import { TemplateCache } from "./TemplateCache.js";

/**
 * Loads every template directly beneath a directory and looks them up by name.
 * Each immediate subdirectory containing a `template.json` becomes a template
 * named after the subdirectory.
 */
export class FilesystemTemplateManager {
  private readonly templates: ReadonlyMap<string, Template>;

  private constructor(templates: Map<string, Template>) {
    this.templates = templates;
  }

  /**
   * Loads every template under `baseDir`.
   *
   * @param baseDir - Directory whose subdirectories each describe one template.
   * @param cache - Cache for compiled blocks, shared by all loaded templates; a
   * new one is created if omitted.
   * @returns A manager holding the loaded templates.
   */
  static async of(baseDir: string, cache?: TemplateCache): Promise<FilesystemTemplateManager> {
    const resolvedCache = cache ?? new TemplateCache();
    const templates = new Map<string, Template>();

    const entries = await readdir(baseDir, { withFileTypes: true });
    for (const entry of entries) {
      if (!entry.isDirectory()) continue;
      const templateDir = join(baseDir, entry.name);
      if (!existsSync(join(templateDir, "template.json"))) continue;
      templates.set(entry.name, await Template.fromDirectory(templateDir, resolvedCache));
    }

    return new FilesystemTemplateManager(templates);
  }

  /**
   * Returns the template with the given name.
   *
   * @param name - The template's name (its subdirectory name).
   * @returns The template.
   * @throws If no template with that name was loaded.
   */
  get(name: string): Template {
    const template = this.templates.get(name);
    if (!template) throw new Error(`Template '${name}' not found`);
    return template;
  }

  /**
   * @returns The names of all loaded templates.
   */
  names(): string[] {
    return [...this.templates.keys()];
  }
}
