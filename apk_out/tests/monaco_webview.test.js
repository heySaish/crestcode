const http = require('http');
const fs = require('fs');
const path = require('path');
const jsdom = require('jsdom');
const { JSDOM, VirtualConsole } = jsdom;
const assert = require('assert');

const assetsDir = path.join(__dirname, '../android/app/src/main/assets/editor');

console.log('====================================================');
console.log(' Crest Mobile Editor - Milestone 1 Automated Test ');
console.log('====================================================\n');

// 1. Asset File Integrity Checks
console.log('▶ [Test 1/6] Verifying asset files and Monaco bundle presence...');
assert.strictEqual(fs.existsSync(path.join(assetsDir, 'index.html')), true, 'index.html must exist');
assert.strictEqual(fs.existsSync(path.join(assetsDir, 'styles.css')), true, 'styles.css must exist');
assert.strictEqual(fs.existsSync(path.join(assetsDir, 'editor.js')), true, 'editor.js must exist');
assert.strictEqual(fs.existsSync(path.join(assetsDir, 'vs/loader.js')), true, 'vs/loader.js must exist');
assert.strictEqual(fs.existsSync(path.join(assetsDir, 'vs/editor/editor.main.css')), true, 'editor.main.css must exist');
console.log('   ✔ All required HTML/CSS/JS and Monaco AMD assets exist in Android assets!\n');

// Mocks and state tracking for Android native bridge callbacks
let bridgeReadyCalled = false;
let bridgeContentChangeCount = 0;
let lastBridgeContent = null;

// MIME types dictionary for local test server
const mimeTypes = {
  '.html': 'text/html',
  '.css': 'text/css',
  '.js': 'application/javascript',
  '.json': 'application/json',
  '.png': 'image/png',
  '.ttf': 'font/ttf'
};

async function runMonacoWebViewTests() {
  // Start local HTTP server to serve assets for JSDOM WebView simulation
  const server = http.createServer((req, res) => {
    let reqPath = req.url.split('?')[0];
    if (reqPath === '/') reqPath = '/index.html';
    const filePath = path.join(assetsDir, reqPath);

    if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
      const ext = path.extname(filePath);
      const contentType = mimeTypes[ext] || 'application/octet-stream';
      res.writeHead(200, { 'Content-Type': contentType });
      fs.createReadStream(filePath).pipe(res);
    } else {
      res.writeHead(404, { 'Content-Type': 'text/plain' });
      res.end('404 Not Found');
    }
  });

  await new Promise(resolve => server.listen(8888, '127.0.0.1', resolve));
  console.log('▶ [Test 2/6] Started local HTTP server for Android WebView asset rendering at http://127.0.0.1:8888/');

  const virtualConsole = new VirtualConsole();
  virtualConsole.on('error', (msg) => {
    if (msg && typeof msg === 'string' && (msg.includes('Could not load') || msg.includes('Worker'))) return;
  });
  virtualConsole.on('log', (msg) => console.log(msg));

  try {
    // Create JSDOM instance representing Android WebView
    const dom = await JSDOM.fromURL('http://127.0.0.1:8888/index.html', {
      runScripts: 'dangerously',
      resources: 'usable',
      virtualConsole: virtualConsole,
      beforeParse(window) {
        // Mock Android Native Javascript Interface
        window.CrestAndroidBridge = {
          getDeviceInfo: function() {
            return "Android 14 (API 34) WebView Simulator";
          },
          onEditorReady: function(dataStr) {
            bridgeReadyCalled = true;
            console.log('   [Bridge Event] onEditorReady:', dataStr);
          },
          onContentChanged: function(dataStr) {
            bridgeContentChangeCount++;
            lastBridgeContent = JSON.parse(dataStr);
            console.log('   [Bridge Event] onContentChanged:', dataStr);
          },
          logNative: function(msg) {
            console.log('   [Bridge Native Log]:', msg);
          }
        };

        // Mock canvas for Monaco layout engine in JSDOM
        window.HTMLCanvasElement.prototype.getContext = function() {
          return {
            fillRect: () => {},
            clearRect: () => {},
            getImageData: (x, y, w, h) => ({ data: new Array(w * h * 4) }),
            putImageData: () => {},
            createImageData: () => ([]),
            setTransform: () => {},
            drawImage: () => {},
            save: () => {},
            fillText: () => {},
            restore: () => {},
            beginPath: () => {},
            moveTo: () => {},
            lineTo: () => {},
            closePath: () => {},
            stroke: () => {},
            translate: () => {},
            scale: () => {},
            rotate: () => {},
            arc: () => {},
            fill: () => {},
            measureText: (text) => ({ width: text.length * 8, actualBoundingBoxAscent: 10, actualBoundingBoxDescent: 2 })
          };
        };
      }
    });

    const { window } = dom;

    // Poll for Monaco Editor readiness
    console.log('▶ [Test 3/6] Loading Monaco Editor & AMD loader in WebView...');
    let checkAttempts = 0;
    const maxAttempts = 50;

    await new Promise((resolve, reject) => {
      const interval = setInterval(() => {
        checkAttempts++;
        if (window.CrestEditorAPI && window.CrestEditorAPI.isReady()) {
          clearInterval(interval);
          console.log('   ✔ Monaco Editor loaded successfully and window.CrestEditorAPI is READY!\n');
          resolve();
        } else if (checkAttempts >= maxAttempts) {
          clearInterval(interval);
          reject(new Error('Monaco Editor timed out loading in WebView JSDOM environment after 10 seconds.'));
        }
      }, 200);
    });

    // 4. Test initial content and status
    console.log('▶ [Test 4/6] Verifying editor document model & stats...');
    const initialText = window.CrestEditorAPI.getValue();
    assert.ok(initialText.includes('greetCrestUser'), 'Initial text should contain greetCrestUser sample code');
    
    const stats = window.CrestEditorAPI.getEditorStats();
    console.log('   ✔ Editor Stats:', stats);
    assert.strictEqual(stats.language, 'javascript', 'Language should be javascript');
    assert.ok(stats.lineCount >= 10, 'Initial document line count should be >= 10');
    console.log('   ✔ Document model verification passed!\n');

    // 5. Test basic typing & editing
    console.log('▶ [Test 5/6] Testing basic typing & document editing...');
    const newCodeSnippet = '\n// Automated Typing Test\nconst crestStatus = "PASS";\n';
    const typeResult = window.CrestEditorAPI.typeText(newCodeSnippet);
    assert.strictEqual(typeResult, true, 'typeText API should return true');

    const updatedText = window.CrestEditorAPI.getValue();
    assert.ok(updatedText.includes('const crestStatus = "PASS"'), 'Document must reflect newly typed text');
    
    const updatedStats = window.CrestEditorAPI.getEditorStats();
    console.log('   ✔ Updated Stats after typing:', updatedStats);
    assert.ok(updatedStats.lineCount > stats.lineCount, 'Line count should increase after typing');
    console.log('   ✔ Typing and document editing verification passed!\n');

    // 6. Verify Android Native Bridge events
    console.log('▶ [Test 6/6] Verifying Android Native Bridge integration...');
    assert.strictEqual(bridgeReadyCalled, true, 'Android bridge onEditorReady callback must have executed');
    assert.ok(bridgeContentChangeCount > 0, 'Android bridge onContentChanged should be invoked upon typing');
    console.log('   ✔ Android Native Bridge callback verification passed!\n');

    console.log('====================================================');
    console.log(' RESULT: PASS - Milestone 1 Monaco on Crest Success ');
    console.log('====================================================\n');

    server.close(() => {
      process.exit(0);
    });

  } catch (err) {
    server.close();
    console.error('\n❌ TEST FAILED:', err.message);
    process.exit(1);
  }
}

runMonacoWebViewTests();
