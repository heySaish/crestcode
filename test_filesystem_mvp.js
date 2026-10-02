const net = require('net');
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');

const socketPath = path.join(__dirname, 'crest-ipc-fs.sock');
try { fs.unlinkSync(socketPath); } catch (e) {}

async function main() {
  console.log("==================================================");
  console.log("🚀 CREST CODE - MAINTHREAD FILESYSTEM & DOCUMENTS TEST");
  console.log("==================================================");

  const { RPCProtocol } = await import(path.join(__dirname, 'dist/engine/out/vs/workbench/services/extensions/common/rpcProtocol.js'));
  const { MainContext } = await import(path.join(__dirname, 'dist/engine/out/vs/workbench/api/common/extHost.protocol.js'));
  const { Emitter } = await import(path.join(__dirname, 'dist/engine/out/vs/base/common/event.js'));
  
  const { MainThreadFileSystemImpl } = await import(path.join(__dirname, 'src/services/mainThreadFileSystem.ts'));
  const { MainThreadDocumentsImpl } = await import(path.join(__dirname, 'src/services/mainThreadDocuments.ts'));

  const server = net.createServer(async (socket) => {
    console.log("\n✅ [CONNECT] EXTENSION HOST CONNECTED TO CREST SERVER!");

    const emitter = new Emitter();
    const protocol = {
      onMessage: emitter.event,
      send: (msg) => socket.write(Buffer.from(JSON.stringify(msg)))
    };

    socket.on('data', (data) => {
      try {
        const parsed = JSON.parse(data.toString('utf-8'));
        emitter.fire(parsed);
      } catch (e) {}
    });

    const rpcProtocol = new RPCProtocol(protocol);

    const fsService = new MainThreadFileSystemImpl();
    const docsService = new MainThreadDocumentsImpl();

    // Register Services
    rpcProtocol.set(MainContext.MainThreadFileSystem, fsService);
    rpcProtocol.set(MainContext.MainThreadDocuments, docsService);

    console.log("🎯 Registered Services: MainThreadFileSystem, MainThreadDocuments");
  });

  server.listen(socketPath, () => {
    console.log(`📡 Listening on Unix Socket: ${socketPath}...`);
    console.log("⚡ Spawning Real VS Code Extension Host Process...");

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
