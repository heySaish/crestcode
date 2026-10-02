const net = require('net');
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');

const socketPath = path.join(__dirname, 'crest-ipc-commands.sock');
try { fs.unlinkSync(socketPath); } catch (e) {}

async function main() {
  console.log("==================================================");
  console.log("🚀 CREST CODE - MAINTHREAD COMMANDS MVP TEST");
  console.log("==================================================");

  // Import VS Code RPCProtocol & MainContext identifiers
  const { RPCProtocol } = await import(path.join(__dirname, 'dist/engine/out/vs/workbench/services/extensions/common/rpcProtocol.js'));
  const { MainContext } = await import(path.join(__dirname, 'dist/engine/out/vs/workbench/api/common/extHost.protocol.js'));
  const { Emitter } = await import(path.join(__dirname, 'dist/engine/out/vs/base/common/event.js'));

  const server = net.createServer(async (socket) => {
    console.log("\n✅ [SUCCESS] EXTENSION HOST CONNECTED TO CREST SERVER!");

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
        // Binary buffer fallback
      }
    });

    const rpcProtocol = new RPCProtocol(protocol);

    // Implement MainThreadCommands service contract
    const mainThreadCommandsService = {
      $registerCommand: (commandId) => {
        console.log(`\n📲 [CREST ANDROID UI]: MainThreadCommands.$registerCommand("${commandId}")`);
      },
      $unregisterCommand: (commandId) => {
        console.log(`\n📲 [CREST ANDROID UI]: MainThreadCommands.$unregisterCommand("${commandId}")`);
      },
      $executeCommand: (commandId, args) => {
        console.log(`\n📲 [CREST ANDROID UI]: MainThreadCommands.$executeCommand("${commandId}", args:`, args, `)`);
        return Promise.resolve(`Result from Crest UI for command: ${commandId}`);
      }
    };

    // Register service with RPCProtocol
    rpcProtocol.set(MainContext.MainThreadCommands, mainThreadCommandsService);
    console.log("🎯 MainThreadCommands Service registered with RPCProtocol!");
  });

  server.listen(socketPath, () => {
    console.log(`📡 Listening on Unix Socket ${socketPath}...`);
    console.log("⚡ Spawning VS Code Extension Host...");

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
