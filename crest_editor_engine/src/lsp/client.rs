use crate::lsp::transport::{read_message, write_message};
use crate::lsp::types::*;
use crossbeam_channel::{unbounded, Receiver, Sender};
use serde_json::json;
use std::collections::HashMap;
use std::io::BufReader;
use std::process::{Child, Command, Stdio};
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc, Mutex,
};
use std::thread;

pub type DiagnosticsCallback = Arc<dyn Fn(PublishDiagnosticsParams) + Send + Sync>;

pub struct LspClient {
    child: Arc<Mutex<Option<Child>>>,
    writer_tx: Sender<String>,
    next_request_id: AtomicU64,
    pending_requests: Arc<Mutex<HashMap<u64, Sender<Result<serde_json::Value, String>>>>>,
    diagnostics_callback: Arc<Mutex<Option<DiagnosticsCallback>>>,
}

impl LspClient {
    pub fn spawn(server_command: &str, args: &[&str]) -> Result<Self, String> {
        let mut child = Command::new(server_command)
            .args(args)
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped())
            .spawn()
            .map_err(|e| format!("Failed to spawn LSP server '{}': {}", server_command, e))?;

        let stdin = child
            .stdin
            .take()
            .ok_or_else(|| "Failed to open stdin for LSP server".to_string())?;
        let stdout = child
            .stdout
            .take()
            .ok_or_else(|| "Failed to open stdout for LSP server".to_string())?;

        let (writer_tx, writer_rx): (Sender<String>, Receiver<String>) = unbounded();
        let pending_requests: Arc<Mutex<HashMap<u64, Sender<Result<serde_json::Value, String>>>>> =
            Arc::new(Mutex::new(HashMap::new()));
        let diagnostics_callback: Arc<Mutex<Option<DiagnosticsCallback>>> =
            Arc::new(Mutex::new(None));

        // Writer thread
        thread::spawn(move || {
            let mut writer = stdin;
            while let Ok(msg) = writer_rx.recv() {
                if write_message(&mut writer, &msg).is_err() {
                    break;
                }
            }
        });

        // Reader thread
        let pending_requests_clone = pending_requests.clone();
        let diagnostics_callback_clone = diagnostics_callback.clone();
        thread::spawn(move || {
            let mut reader = BufReader::new(stdout);
            while let Ok(Some(json_str)) = read_message(&mut reader) {
                if let Ok(value) = serde_json::from_str::<serde_json::Value>(&json_str) {
                    if let Some(id) = value.get("id").and_then(|i| i.as_u64()) {
                        let mut map = pending_requests_clone.lock().unwrap();
                        if let Some(tx) = map.remove(&id) {
                            if let Some(err) = value.get("error") {
                                let _ = tx.send(Err(err.to_string()));
                            } else {
                                let result = value.get("result").cloned().unwrap_or(serde_json::Value::Null);
                                let _ = tx.send(Ok(result));
                            }
                        }
                    } else if let Some(method) = value.get("method").and_then(|m| m.as_str()) {
                        if method == "textDocument/publishDiagnostics" {
                            if let Some(params_val) = value.get("params") {
                                if let Ok(params) = serde_json::from_value::<PublishDiagnosticsParams>(params_val.clone()) {
                                    let cb_opt = diagnostics_callback_clone.lock().unwrap();
                                    if let Some(cb) = cb_opt.as_ref() {
                                        cb(params);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });

        Ok(Self {
            child: Arc::new(Mutex::new(Some(child))),
            writer_tx,
            next_request_id: AtomicU64::new(1),
            pending_requests,
            diagnostics_callback,
        })
    }

    pub fn set_diagnostics_callback<F>(&self, cb: F)
    where
        F: Fn(PublishDiagnosticsParams) + Send + Sync + 'static,
    {
        *self.diagnostics_callback.lock().unwrap() = Some(Arc::new(cb));
    }

    pub fn send_notification(&self, method: &str, params: serde_json::Value) -> Result<(), String> {
        let req = JsonRpcRequest {
            jsonrpc: "2.0".to_string(),
            id: None,
            method: method.to_string(),
            params: Some(params),
        };
        let payload = serde_json::to_string(&req).map_err(|e| e.to_string())?;
        self.writer_tx.send(payload).map_err(|e| e.to_string())
    }

    pub fn send_request(&self, method: &str, params: serde_json::Value) -> Result<serde_json::Value, String> {
        let id = self.next_request_id.fetch_add(1, Ordering::SeqCst);
        let (tx, rx) = unbounded();
        {
            self.pending_requests.lock().unwrap().insert(id, tx);
        }

        let req = JsonRpcRequest {
            jsonrpc: "2.0".to_string(),
            id: Some(id),
            method: method.to_string(),
            params: Some(params),
        };
        let payload = serde_json::to_string(&req).map_err(|e| e.to_string())?;
        self.writer_tx.send(payload).map_err(|e| e.to_string())?;

        // Wait for response with timeout
        rx.recv_timeout(std::time::Duration::from_secs(5))
            .map_err(|_| "LSP request timed out".to_string())?
    }

    pub fn initialize(&self, root_uri: &str) -> Result<serde_json::Value, String> {
        let params = json!({
            "processId": std::process::id(),
            "rootUri": root_uri,
            "capabilities": {
                "textDocument": {
                    "completion": { "completionItem": { "snippetSupport": true } },
                    "hover": {},
                    "definition": {},
                    "references": {},
                    "rename": {},
                    "codeAction": {}
                }
            }
        });
        let res = self.send_request("initialize", params)?;
        self.send_notification("initialized", json!({}))?;
        Ok(res)
    }

    pub fn did_open(&self, uri: &str, language_id: &str, version: i64, text: &str) -> Result<(), String> {
        self.send_notification(
            "textDocument/didOpen",
            json!({
                "textDocument": {
                    "uri": uri,
                    "languageId": language_id,
                    "version": version,
                    "text": text
                }
            }),
        )
    }

    pub fn did_change(&self, uri: &str, version: i64, text: &str) -> Result<(), String> {
        self.send_notification(
            "textDocument/didChange",
            json!({
                "textDocument": {
                    "uri": uri,
                    "version": version
                },
                "contentChanges": [{ "text": text }]
            }),
        )
    }

    pub fn did_close(&self, uri: &str) -> Result<(), String> {
        self.send_notification(
            "textDocument/didClose",
            json!({
                "textDocument": { "uri": uri }
            }),
        )
    }

    pub fn completion(&self, uri: &str, line: u32, character: u32) -> Result<Vec<LspCompletionItem>, String> {
        let params = json!({
            "textDocument": { "uri": uri },
            "position": { "line": line, "character": character }
        });
        let res = self.send_request("textDocument/completion", params)?;
        let items = if let Ok(arr) = serde_json::from_value::<Vec<LspCompletionItem>>(res.clone()) {
            arr
        } else if let Some(items_val) = res.get("items") {
            serde_json::from_value(items_val.clone()).unwrap_or_default()
        } else {
            Vec::new()
        };
        Ok(items)
    }

    pub fn hover(&self, uri: &str, line: u32, character: u32) -> Result<Option<LspHover>, String> {
        let params = json!({
            "textDocument": { "uri": uri },
            "position": { "line": line, "character": character }
        });
        let res = self.send_request("textDocument/hover", params)?;
        if res.is_null() {
            Ok(None)
        } else {
            let hover = serde_json::from_value(res).map_err(|e| e.to_string())?;
            Ok(Some(hover))
        }
    }

    pub fn goto_definition(&self, uri: &str, line: u32, character: u32) -> Result<Vec<LspLocation>, String> {
        let params = json!({
            "textDocument": { "uri": uri },
            "position": { "line": line, "character": character }
        });
        let res = self.send_request("textDocument/definition", params)?;
        if res.is_null() {
            Ok(Vec::new())
        } else if let Ok(loc) = serde_json::from_value::<LspLocation>(res.clone()) {
            Ok(vec![loc])
        } else if let Ok(locs) = serde_json::from_value::<Vec<LspLocation>>(res) {
            Ok(locs)
        } else {
            Ok(Vec::new())
        }
    }

    pub fn shutdown(&self) {
        let _ = self.send_request("shutdown", serde_json::Value::Null);
        let _ = self.send_notification("exit", serde_json::Value::Null);
        if let Some(mut child) = self.child.lock().unwrap().take() {
            let _ = child.kill();
        }
    }
}

impl Drop for LspClient {
    fn drop(&mut self) {
        self.shutdown();
    }
}
