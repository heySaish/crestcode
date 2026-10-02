export interface IMainThreadMessages {
  $showMessage(severity: number, message: string, options: unknown, commands: unknown[]): Promise<number | undefined>;
}
