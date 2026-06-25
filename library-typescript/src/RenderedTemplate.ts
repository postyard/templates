/**
 * The output of rendering a {@link Template}: each block's rendered string,
 * keyed by block name.
 */
export class RenderedTemplate {
  private readonly blocks: ReadonlyMap<string, string>;

  /**
   * @param blocks - The rendered output of each block, keyed by block name.
   */
  constructor(blocks: Map<string, string>) {
    this.blocks = new Map(blocks);
  }

  /**
   * Returns the rendered output of a single block.
   *
   * @param blockName - The block's name.
   * @returns The block's rendered output.
   * @throws If the template has no block with that name.
   */
  get(blockName: string): string {
    const value = this.blocks.get(blockName);
    if (value === undefined) {
      throw new Error(`Block '${blockName}' not found`);
    }
    return value;
  }

  /**
   * @returns The names of the rendered blocks, in render order.
   */
  blockNames(): string[] {
    return [...this.blocks.keys()];
  }
}
