import { CrestStorageAdapter } from '../storage/crest-storage.ts';

export class MainThreadFileSystemImpl {
  private storage = new CrestStorageAdapter();

  async $stat(uri: string) {
    console.log(`\n📂 [CREST FS]: $stat -> ${uri}`);
    return await this.storage.stat(uri);
  }

  async $readdir(uri: string) {
    console.log(`\n📂 [CREST FS]: $readdir -> ${uri}`);
    return await this.storage.readDirectory(uri);
  }

  async $readFile(uri: string) {
    console.log(`\n📂 [CREST FS]: $readFile -> ${uri}`);
    return await this.storage.readFile(uri);
  }

  async $writeFile(uri: string, content: Uint8Array) {
    console.log(`\n📂 [CREST FS]: $writeFile -> ${uri} (${content.length} bytes)`);
    await this.storage.writeFile(uri, content);
  }

  async $mkdir(uri: string) {
    console.log(`\n📂 [CREST FS]: $mkdir -> ${uri}`);
  }

  async $delete(uri: string, options: { recursive: boolean }) {
    console.log(`\n📂 [CREST FS]: $delete -> ${uri}`);
  }

  async $rename(oldUri: string, newUri: string) {
    console.log(`\n📂 [CREST FS]: $rename -> ${oldUri} => ${newUri}`);
  }
}
