export interface IMainThreadCommands {
  $registerCommand(commandId: string): void;
  $unregisterCommand(commandId: string): void;
  $executeCommand<T = unknown>(commandId: string, args: unknown[]): Promise<T>;
}
