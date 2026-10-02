export interface FileStat {
  type: number;
  ctime: number;
  mtime: number;
  size: number;
}

export interface IMainThreadFileSystem {
  $stat(uri: string): Promise<FileStat>;
  $readdir(uri: string): Promise<[string, FileStat][]>;
  $readFile(uri: string): Promise<Uint8Array>;
  $writeFile(uri: string, content: Uint8Array): Promise<void>;
  $mkdir(uri: string): Promise<void>;
  $delete(uri: string, options: { recursive: boolean }): Promise<void>;
  $rename(oldUri: string, newUri: string): Promise<void>;
}
