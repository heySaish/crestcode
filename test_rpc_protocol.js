const path = require('path');

async function main() {
  console.log('==================================================');
  console.log('🔥 TESTING VS CODE COMPILED RPCPROTOCOL IN TERMUX');
  console.log('==================================================');

  const rpcPath = path.join(__dirname, 'dist/engine/out/vs/workbench/services/extensions/common/rpcProtocol.js');
  
  try {
    const { RPCProtocol } = await import(rpcPath);
    console.log('✅ SUCCESSFULLY IMPORTED VS CODE RPCPROTOCOL!');
    console.log('RPCProtocol constructor:', typeof RPCProtocol);
  } catch (err) {
    console.log('Error loading RPCProtocol:', err.stack);
  }
}

main();
