(function() {
  'use strict';

  // Configure Monaco Environment worker fallback for Android WebView & test environments
  window.MonacoEnvironment = {
    getWorkerUrl: function (moduleId, label) {
      return 'data:text/javascript;charset=utf-8,' + encodeURIComponent('self.onmessage = function() {};');
    }
  };

  // Polyfill window.matchMedia for Android WebView compatibility
  if (typeof window.matchMedia !== 'function') {
    window.matchMedia = function(query) {
      return {
        matches: false,
        media: query || '',
        onchange: null,
        addListener: function() {},
        removeListener: function() {},
        addEventListener: function() {},
        removeEventListener: function() {},
        dispatchEvent: function() { return false; }
      };
    };
  }

  // Polyfill window.ResizeObserver for Android WebView compatibility
  if (typeof window.ResizeObserver !== 'function') {
    window.ResizeObserver = class ResizeObserver {
      constructor(callback) {
        this.callback = callback;
      }
      observe(target) {
        if (target) {
          const rect = (target.getBoundingClientRect && typeof target.getBoundingClientRect === 'function') 
            ? target.getBoundingClientRect() 
            : { width: 800, height: 600, top: 0, left: 0, right: 800, bottom: 600 };
          try {
            this.callback([{ target: target, contentRect: rect }], this);
          } catch(e) {}
        }
      }
      unobserve() {}
      disconnect() {}
    };
  }

  // Base path for Monaco AMD loader
  require.config({ paths: { 'vs': 'vs' } });

  let editor = null;
  let isReady = false;

  const statusPill = document.getElementById('status-pill');
  const statLines = document.getElementById('stat-lines');
  const statPos = document.getElementById('stat-pos');
  const footerStatusText = document.getElementById('footer-status-text');
  const btnTheme = document.getElementById('btn-theme');

  // Initial code template to test Monaco functionality
  const sampleCode = `// Crest Code - Monaco Editor on Android WebView
// Milestone 1 Verification Code

function greetCrestUser(name) {
    const greeting = "Hello, " + name + "! Monaco Editor is fully functional on Crest Android.";
    console.log(greeting);
    return greeting;
}

// Perform basic calculation test
const result = greetCrestUser("Developer");
`;

  // Initialize Monaco Editor
  require(['vs/editor/editor.main'], function() {
    try {
      const container = document.getElementById('editor-container');

      editor = monaco.editor.create(container, {
        value: sampleCode,
        language: 'javascript',
        theme: 'vs-dark',
        automaticLayout: true,
        fontSize: 14,
        lineNumbers: 'on',
        wordWrap: 'on',
        minimap: { enabled: false }, // Space optimization for mobile
        scrollBeyondLastLine: false,
        smoothScrolling: true,
        contextmenu: true,
        quickSuggestions: true,
        cursorBlinking: 'smooth'
      });

      isReady = true;

      // Add Ctrl+S / Cmd+S save command shortcut
      editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, function() {
        if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onSaveRequested === 'function') {
          window.CrestAndroidBridge.onSaveRequested(editor.getValue());
        }
      });

      // Update UI Status
      if (statusPill) {
        statusPill.textContent = 'Monaco Active';
        statusPill.style.background = 'rgba(34, 197, 94, 0.2)';
        statusPill.style.color = '#22c55e';
      }

      if (footerStatusText) {
        footerStatusText.textContent = 'Crest Mobile • Monaco v0.57.0 Active';
      }

      updateStats();

      // Listen to model content change (typing / editing)
      editor.onDidChangeModelContent(function(e) {
        updateStats();
        if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onContentChanged === 'function') {
          try {
            window.CrestAndroidBridge.onContentChanged(JSON.stringify({
              lineCount: editor.getModel().getLineCount(),
              length: editor.getValue().length,
              versionId: editor.getModel().getVersionId()
            }));
          } catch(err) {
            console.error('Bridge onContentChanged error:', err);
          }
        }
      });

      // Listen to cursor position changes
      editor.onDidChangeCursorPosition(function(e) {
        if (statPos) {
          statPos.textContent = 'Ln ' + e.position.lineNumber + ', Col ' + e.position.column;
        }
      });

      // Notify native Android bridge
      if (window.CrestAndroidBridge && typeof window.CrestAndroidBridge.onEditorReady === 'function') {
        try {
          window.CrestAndroidBridge.onEditorReady(JSON.stringify({
            status: 'READY',
            engine: 'Monaco Editor',
            lineCount: editor.getModel().getLineCount()
          }));
        } catch(err) {
          console.error('Bridge onEditorReady error:', err);
        }
      }

      console.log('[Crest Monaco] Monaco Editor successfully initialized!');

    } catch (error) {
      console.error('[Crest Monaco] Initialization error:', error);
      if (statusPill) {
        statusPill.textContent = 'Init Error';
        statusPill.style.background = 'rgba(239, 68, 68, 0.2)';
        statusPill.style.color = '#ef4444';
      }
    }
  });

  function updateStats() {
    if (!editor) return;
    const model = editor.getModel();
    if (model && statLines) {
      const count = model.getLineCount();
      statLines.textContent = count + (count === 1 ? ' line' : ' lines');
    }
  }

  // Toggle Theme between Dark and Light
  let isDarkMode = true;
  if (btnTheme) {
    btnTheme.addEventListener('click', function() {
      if (!editor) return;
      isDarkMode = !isDarkMode;
      monaco.editor.setTheme(isDarkMode ? 'vs-dark' : 'vs');
      btnTheme.textContent = isDarkMode ? '🌙' : '☀️';
    });
  }

  // Global API for verification testing
  window.CrestEditorAPI = {
    isReady: function() {
      return isReady && editor !== null;
    },
    getValue: function() {
      return editor ? editor.getValue() : '';
    },
    setValue: function(val) {
      if (editor) {
        editor.setValue(val);
        updateStats();
        return true;
      }
      return false;
    },
    typeText: function(text) {
      if (editor) {
        const position = editor.getPosition();
        editor.executeEdits('test-typing', [{
          range: new monaco.Range(position.lineNumber, position.column, position.lineNumber, position.column),
          text: text,
          forceMoveMarkers: true
        }]);
        updateStats();
        return true;
      }
      return false;
    },
    clearEditor: function() {
      if (!editor) return false;
      let emptyModel = monaco.editor.getModel(monaco.Uri.file('/empty'));
      if (!emptyModel) {
        emptyModel = monaco.editor.createModel('', 'plaintext', monaco.Uri.file('/empty'));
      }
      editor.setModel(emptyModel);
      updateStats();
      return true;
    },
    openFile: function(filePath, content, languageId) {
      if (!editor) return false;
      const uri = monaco.Uri.file(filePath);
      let model = monaco.editor.getModel(uri);
      if (!model) {
        model = monaco.editor.createModel(content, languageId, uri);
      } else {
        if (model.getValue() !== content) {
          model.setValue(content);
        }
        monaco.editor.setModelLanguage(model, languageId);
      }
      editor.setModel(model);
      updateStats();
      return true;
    },
    getEditorStats: function() {
      if (!editor) return null;
      const model = editor.getModel();
      const pos = editor.getPosition();
      return {
        lineCount: model.getLineCount(),
        characterCount: model.getValueLength(),
        cursorPosition: { lineNumber: pos.lineNumber, column: pos.column },
        language: model.getLanguageId()
      };
    },
    undo: function() {
      if (editor) editor.trigger('keyboard', 'undo', null);
    },
    redo: function() {
      if (editor) editor.trigger('keyboard', 'redo', null);
    }
  };

})();
