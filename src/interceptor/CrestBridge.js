/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Crest Mobile UI Interceptor. All rights reserved.
 *  Crest Bridge Communication Layer
 *--------------------------------------------------------------------------------------------*/

export class CrestInterceptorBridge {
  static instance = null;

  constructor() {
    this.workbenchApi = null;
    this.isReady = false;
    this.extensionHostReady = false;
    this.listeners = [];
  }

  static getInstance() {
    if (!CrestInterceptorBridge.instance) {
      CrestInterceptorBridge.instance = new CrestInterceptorBridge();
    }
    return CrestInterceptorBridge.instance;
  }

  initialize(workbenchApi) {
    this.workbenchApi = workbenchApi;
    this.isReady = true;
    this.extensionHostReady = true;
    this.notifyListeners('ready', this.getStatus());
  }

  getStatus() {
    return {
      ready: this.isReady,
      extensionHostActive: this.extensionHostReady,
      workbenchLoaded: !!this.workbenchApi,
      activeWorkspace: 'Crest Mobile Workspace'
    };
  }

  addListener(listener) {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
    };
  }

  notifyListeners(event, data) {
    for (const listener of this.listeners) {
      try {
        listener(event, data);
      } catch (e) {
        console.error('[CrestBridge] Listener error:', e);
      }
    }
  }

  async executeCommand(commandId, ...args) {
    if (!this.workbenchApi || !this.workbenchApi.commands) {
      return {
        success: false,
        error: 'VS Code Workbench backend is not connected',
        timestamp: Date.now()
      };
    }

    try {
      const result = await this.workbenchApi.commands.executeCommand(commandId, ...args);
      this.notifyListeners('commandExecuted', { commandId, args, result });
      return {
        success: true,
        data: result,
        timestamp: Date.now()
      };
    } catch (err) {
      console.error(`[CrestBridge] Command execution failed: ${commandId}`, err);
      return {
        success: false,
        error: err?.message || String(err),
        timestamp: Date.now()
      };
    }
  }

  async executeWorkspaceRoundTrip(action, payload) {
    this.notifyListeners('roundTripStarted', { action, payload });

    switch (action) {
      case 'triggerExtensionTest': {
        const cmdResult = await this.executeCommand('vscode.executeCommand', payload?.commandId || 'workbench.action.showCommands');
        return {
          success: cmdResult.success,
          data: {
            action: 'triggerExtensionTest',
            message: 'Extension engine active and responded to command execution',
            backendResult: cmdResult.data
          },
          error: cmdResult.error,
          timestamp: Date.now()
        };
      }

      case 'getWorkspaceInfo': {
        return {
          success: true,
          data: {
            workspaceName: 'Crest Interceptor Workspace',
            backendReady: this.isReady,
            extensionHostStatus: 'active'
          },
          timestamp: Date.now()
        };
      }

      default:
        return {
          success: false,
          error: `Unknown round-trip action: ${action}`,
          timestamp: Date.now()
        };
    }
  }
}
