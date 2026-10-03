/*---------------------------------------------------------------------------------------------
 *  Copyright (c) Crest Mobile UI Interceptor. All rights reserved.
 *  Interceptor Automated Test Suite for CI / GitHub Actions
 *--------------------------------------------------------------------------------------------*/

import { CrestInterceptorBridge } from '../src/interceptor/CrestBridge.js';
import assert from 'assert';

console.log('🚀 Running Crest Mobile UI Interceptor Test Suite...\n');

// Test 1: Singleton instance initialization
console.log('Test 1: Verify CrestInterceptorBridge singleton initialization');
const bridge = CrestInterceptorBridge.getInstance();
assert.ok(bridge, 'Bridge instance should exist');
const initialStatus = bridge.getStatus();
assert.strictEqual(initialStatus.ready, false, 'Initial status should be not ready');
console.log('  ✅ Passed: Singleton initialized in unready state\n');

// Test 2: Mock VS Code Workbench backend hookup
console.log('Test 2: Connect Mock VS Code Workbench Backend');
let mockCommandExecuted = false;
const mockWorkbenchApi = {
  commands: {
    executeCommand: async (commandId, ...args) => {
      mockCommandExecuted = true;
      return `Executed backend command: ${commandId}`;
    }
  }
};

let eventReceived = false;
bridge.addListener((event, data) => {
  if (event === 'ready') {
    eventReceived = true;
  }
});

bridge.initialize(mockWorkbenchApi);
const readyStatus = bridge.getStatus();
assert.strictEqual(readyStatus.ready, true, 'Status should be ready after initialization');
assert.strictEqual(readyStatus.extensionHostActive, true, 'Extension Host status should be active');
assert.strictEqual(eventReceived, true, 'Listener should have received ready event');
console.log('  ✅ Passed: VS Code backend connected and status updated\n');

// Test 3: Execute Backend Command via Bridge
console.log('Test 3: Execute Command via Bridge');
const cmdResult = await bridge.executeCommand('workbench.action.showCommands');
assert.strictEqual(cmdResult.success, true, 'Command execution should succeed');
assert.strictEqual(mockCommandExecuted, true, 'Mock VS Code command should have executed');
assert.strictEqual(cmdResult.data, 'Executed backend command: workbench.action.showCommands');
console.log('  ✅ Passed: Command executed successfully on VS Code backend\n');

// Test 4: Complete Round-Trip Test (Crest UI -> Backend -> Result -> Crest UI)
console.log('Test 4: Complete Round-Trip Verification');
const roundTripResult = await bridge.executeWorkspaceRoundTrip('triggerExtensionTest', { commandId: 'test.activateExtension' });
assert.strictEqual(roundTripResult.success, true, 'Round-trip execution should succeed');
assert.strictEqual(roundTripResult.data.action, 'triggerExtensionTest', 'Round-trip action should match');
assert.ok(roundTripResult.data.backendResult, 'Backend result should be present in round-trip response');
console.log('  ✅ Passed: Round-trip (Crest UI -> VS Code Backend -> Result) verified!\n');

console.log('🎉 ALL CREST INTERCEPTOR TESTS PASSED SUCCESSFULLY!');
