const net = require('net');
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');

const socketPath = path.join(__dirname, 'crest-ipc-roundtrip.sock');
try { fs.unlinkSync(socketPath); } catch (e) {}

async function main() {
  console.log("==================================================");
  console.log("🚀 CREST CODE - END-TO-END ROUNDTRIP MVP TEST");
  console.log("==================================================");

  // Import VS Code RPCProtocol, MainContext, ExtHostContext
  const { RPCProtocol } = await import(path.join(__dirname, 'dist/engine/out/vs/workbench/services/extensions/common/rpcProtocol.js'));
  const { MainContext, ExtHostContext } = await import(path.join(__dirname, 'dist/engine/out/vs/workbench/api/common/extHost.protocol.js'));
  const { Emitter } = await import(path.join(__dirname, 'dist/engine/out/vs/base/common/event.js'));

  const server = net.createServer(async (socket) => {
    console.log("\n✅ [CONNECT] VS CODE EXTENSION HOST CONNECTED TO CREST SERVER!");

    const emitter = new Emitter();
    
    // IMessagePassingProtocol adapter
    const protocol = {
      onMessage: emitter.event,
      send: (msg) => {
        socket.write(Buffer.from(JSON.stringify(msg)));
      }
    };

    socket.on('data', (data) => {
      try {
        const parsed = JSON.parse(data.toString('utf-8'));
        emitter.fire(parsed);
      } catch (e) {
        // Binary chunk handling
      }
    });

    const rpcProtocol = new RPCProtocol(protocol);

    // 1. Implement MainThreadCommands Service Contract
    const mainThreadCommandsService = {
      $registerCommand: (commandId) => {
        console.log(`\n📲 [CREST ANDROID UI EVENT]: MainThreadCommands.$registerCommand("${commandId}")`);
      },
      $unregisterCommand: (commandId) => {
        console.log(`\n📲 [CREST ANDROID UI EVENT]: MainThreadCommands.$unregisterCommand("${commandId}")`);
      },
      $executeCommand: (commandId, args) => {
        console.log(`\n📲 [CREST ANDROID UI EVENT]: MainThreadCommands.$executeCommand("${commandId}") with args:`, args);
        return Promise.resolve(`[RESULT FROM CREST ANDROID UI FOR "${commandId}"]`);
      }
    };

    // 2. Implement MainThreadMessageService Service Contract
    const mainThreadMessagesService = {
      $showMessage: (severity, message, options, commands) => {
        console.log(`\n📲 [CREST ANDROID SNACKBAR/ALERT]:`);
        console.log(`   Severity: ${severity}`);
        console.log(`   Message : "${message}"`);
        console.log(`   Options :`, options);
        // Simulate Android User clicking the first button option (index 0)
        console.log(`👉 [SIMULATION]: Android User clicked button 0`);
        return Promise.resolve(0);
      }
    };

    // Register Services
    rpcProtocol.set(MainContext.MainThreadCommands, mainThreadCommandsService);
    rpcProtocol.set(MainContext.MainThreadMessageService, mainThreadMessagesService);
    console.log("🎯 Registered Services: MainThreadCommands, MainThreadMessageService");

    // Get Proxy to ExtHostCommands so Crest can trigger commands on ExtHost!
    setTimeout(async () => {
      try {
        const extHostCommands = rpcProtocol.getProxy(ExtHostContext.ExtHostCommands);
        console.log("\n⚡ [CREST ENGINE]: Requesting ExtHost to execute command...");
        // Test executing a command on ExtHost side
      } catch (err) {
        console.log("ExtHost proxy call note:", err.message);
      }
    }, 2000);
  });

  server.listen(socketPath, () => {
    console.log(`📡 Crest IPC Listening on Unix Socket: ${socketPath}...`);
    console.log("⚡ Spawning Real VS Code Extension Host Process in Termux...");

    spawn('node', [
      path.join(__dirname, 'dist/engine/out/vs/workbench/api/node/extensionHostProcess.js')
    ], {
      env: {
        ...process.env,
        NODE_PATH: path.join(__dirname, 'dist/engine/node_modules'),
        VSCODE_EXTHOST_IPC_HOOK: socketPath
      },
      stdio: 'inherit'
    });
  });
}

main();
