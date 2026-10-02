const net = require('net');
const path = require('path');
const fs = require('fs');
const { spawn } = require('child_process');

const socketPath = path.join(__dirname, 'crest-ipc-local.sock');
try { fs.unlinkSync(socketPath); } catch (e) {}

console.log('==================================================');
console.log('🚀 CREST CODE - LOCAL TERMUX EXTENSION HOST TEST');
console.log('==================================================');

const server = net.createServer((socket) => {
  console.log('\n✅ [SUCCESS] VS CODE EXTENSION HOST CONNECTED TO LOCAL CREST IPC SERVER!');
  
  socket.on('data', (data) => {
    console.log('\n📥 [RPC PACKET RECEIVED FROM EXT_HOST]:', data);
  });

  socket.on('end', () => {
    console.log('Extension Host disconnected.');
    process.exit(0);
  });
});

server.listen(socketPath, () => {
  console.log(`📡 Crest Editor IPC Server listening at ${socketPath}...`);
  console.log('⚡ Launching Real VS Code Extension Host Process in Termux...');

  const extHostProc = spawn('node', [
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
