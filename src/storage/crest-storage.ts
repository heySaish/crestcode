import * as fs from 'fs/promises';
import * as path from 'path';

export class CrestStorageAdapter {
  async stat(filePath: string) {
    const s = await fs.stat(filePath);
    return {
      type: s.isDirectory() ? 2 : 1,
      ctime: s.ctimeMs,
      mtime: s.mtimeMs,
      size: s.size
    };
  }

  async readDirectory(dirPath: string): Promise<[string, { type: number; ctime: number; mtime: number; size: number }][]> {
    const files = await fs.readdir(dirPath);
    const result: [string, { type: number; ctime: number; mtime: number; size: number }][] = [];
    for (const f of files) {
      const fullPath = path.join(dirPath, f);
      try {
        const st = await this.stat(fullPath);
        result.push([f, st]);
      } catch (e) {
        // Skip unreadable files
      }
    }
    return result;
  }

  async readFile(filePath: string): Promise<Uint8Array> {
    const buf = await fs.readFile(filePath);
    return new Uint8Array(buf);
  }

  async writeFile(filePath: string, content: Uint8Array): Promise<void> {
    await fs.writeFile(filePath, content);
  }
}
