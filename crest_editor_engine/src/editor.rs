use crate::buffer::position::{utf8_col_to_utf16_col, Position, Selection};
use crate::buffer::{Document, TextChangeEvent};
use crate::history::{UndoEntry, UndoHistory};
use crate::lsp::{LspClient, LspCompletionItem, LspHover, LspLocation};
use crate::syntax::{SyntaxHighlighter, TokenSpan};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Arc;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RenderState {
    pub uri: String,
    pub language_id: String,
    pub lines: Vec<String>,
    pub line_count: usize,
    pub cursor: Position,
    pub selection: Selection,
    pub can_undo: bool,
    pub can_redo: bool,
    pub is_modified: bool,
    pub matching_brackets: Option<(Position, Position)>,
}

pub struct EditorEngine {
    documents: HashMap<String, Document>,
    histories: HashMap<String, UndoHistory>,
    active_uri: Option<String>,
    lsp_clients: HashMap<String, Arc<LspClient>>,
}

impl EditorEngine {
    pub fn new() -> Self {
        Self {
            documents: HashMap::new(),
            histories: HashMap::new(),
            active_uri: None,
            lsp_clients: HashMap::new(),
        }
    }

    pub fn open_document(&mut self, uri: &str, language_id: &str, content: &str) {
        let doc = Document::new(uri.to_string(), language_id.to_string(), content);
        self.documents.insert(uri.to_string(), doc);
        if !self.histories.contains_key(uri) {
            self.histories.insert(uri.to_string(), UndoHistory::new());
        }
        self.active_uri = Some(uri.to_string());

        // Notify LSP if connected
        if let Some(client) = self.lsp_clients.get(language_id) {
            let _ = client.did_open(uri, language_id, 1, content);
        }
    }

    pub fn close_document(&mut self, uri: &str) {
        self.documents.remove(uri);
        self.histories.remove(uri);
        if self.active_uri.as_deref() == Some(uri) {
            self.active_uri = self.documents.keys().next().cloned();
        }
        if let Some(client) = self.lsp_clients.get(uri) {
            let _ = client.did_close(uri);
        }
    }

    pub fn set_active_document(&mut self, uri: &str) {
        if self.documents.contains_key(uri) {
            self.active_uri = Some(uri.to_string());
        }
    }

    pub fn get_active_document(&self) -> Option<&Document> {
        self.active_uri
            .as_ref()
            .and_then(|uri| self.documents.get(uri))
    }

    pub fn get_active_document_mut(&mut self) -> Option<&mut Document> {
        if let Some(uri) = self.active_uri.clone() {
            self.documents.get_mut(&uri)
        } else {
            None
        }
    }

    pub fn insert_text(&mut self, text: &str) -> Option<TextChangeEvent> {
        let active_uri = self.active_uri.clone()?;
        let doc = self.documents.get_mut(&active_uri)?;

        let selection_before = doc.selection;
        let start_pos = selection_before.start();
        let end_pos = selection_before.end();
        let text_before = doc.buffer.get_text_range(start_pos, end_pos);

        // 1. Overtype / skip closing bracket if character after cursor is already the matching closing bracket
        if text.len() == 1 && selection_before.is_empty() {
            let ch = text.chars().next().unwrap();
            if matches!(ch, ')' | '}' | ']' | '"' | '\'') {
                if let Some(line_str) = doc.buffer.get_line(start_pos.line) {
                    let chars: Vec<char> = line_str.chars().collect();
                    if start_pos.character < chars.len() && chars[start_pos.character] == ch {
                        let next_pos = Position::new(start_pos.line, start_pos.character + 1);
                        doc.selection = Selection::new(next_pos, next_pos);
                        return None;
                    }
                }
            }
        }

        // 2. Auto-closing pair insertion for (, {, [, ", '
        let insert_str = match text {
            "(" => "()",
            "{" => "{}",
            "[" => "[]",
            "\"" => "\"\"",
            "'" => "''",
            _ => text,
        };
        let is_auto_pair = insert_str.len() == 2 && text.len() == 1 && selection_before.is_empty();

        let change = doc.apply_insert(insert_str);

        if is_auto_pair {
            let mid_pos = Position::new(start_pos.line, start_pos.character + 1);
            doc.selection = Selection::new(mid_pos, mid_pos);
        }

        let selection_after = doc.selection;

        let history = self.histories.get_mut(&active_uri)?;
        history.push(UndoEntry {
            start: start_pos,
            end_before: end_pos,
            end_after: change.end,
            text_before,
            text_after: insert_str.to_string(),
            selection_before,
            selection_after,
        });

        // LSP sync
        let lang = doc.language_id.clone();
        let ver = doc.version;
        let full_text = doc.buffer.get_text();
        if let Some(client) = self.lsp_clients.get(&lang) {
            let _ = client.did_change(&active_uri, ver, &full_text);
        }

        Some(change)
    }

    pub fn delete_backspace(&mut self) -> Option<TextChangeEvent> {
        let active_uri = self.active_uri.clone()?;
        let doc = self.documents.get_mut(&active_uri)?;

        let selection_before = doc.selection;
        let start_pos = selection_before.start();

        // Check if cursor is directly between an auto-closed pair like (), {}, [], "", ''
        if selection_before.is_empty() && start_pos.character > 0 {
            if let Some(line_str) = doc.buffer.get_line(start_pos.line) {
                let chars: Vec<char> = line_str.chars().collect();
                if start_pos.character < chars.len() {
                    let prev_ch = chars[start_pos.character - 1];
                    let next_ch = chars[start_pos.character];
                    let is_pair = matches!(
                        (prev_ch, next_ch),
                        ('(', ')') | ('{', '}') | ('[', ']') | ('"', '"') | ('\'', '\'')
                    );
                    if is_pair {
                        // Expand selection to cover both characters so apply_delete_backspace erases both
                        doc.selection = Selection::new(
                            Position::new(start_pos.line, start_pos.character - 1),
                            Position::new(start_pos.line, start_pos.character + 1),
                        );
                    }
                }
            }
        }

        let current_sel = doc.selection;
        let text_before = if current_sel.is_empty() {
            if start_pos.line == 0 && start_pos.character == 0 {
                return None;
            }
            let del_start = if start_pos.character > 0 {
                Position::new(start_pos.line, start_pos.character - 1)
            } else {
                let p_line = start_pos.line - 1;
                Position::new(p_line, doc.buffer.line_length_chars(p_line))
            };
            doc.buffer.get_text_range(del_start, start_pos)
        } else {
            doc.buffer.get_text_range(current_sel.start(), current_sel.end())
        };

        let change = doc.apply_delete_backspace()?;
        let selection_after = doc.selection;

        let history = self.histories.get_mut(&active_uri)?;
        history.push(UndoEntry {
            start: change.start,
            end_before: change.end,
            end_after: change.start,
            text_before,
            text_after: String::new(),
            selection_before,
            selection_after,
        });

        // LSP sync
        let lang = doc.language_id.clone();
        let ver = doc.version;
        let full_text = doc.buffer.get_text();
        if let Some(client) = self.lsp_clients.get(&lang) {
            let _ = client.did_change(&active_uri, ver, &full_text);
        }

        Some(change)
    }

    pub fn undo(&mut self) -> bool {
        let active_uri = match self.active_uri.clone() {
            Some(u) => u,
            None => return false,
        };
        let history = match self.histories.get_mut(&active_uri) {
            Some(h) => h,
            None => return false,
        };
        let entry = match history.pop_undo() {
            Some(e) => e,
            None => return false,
        };

        if let Some(doc) = self.documents.get_mut(&active_uri) {
            doc.buffer.delete_range(entry.start, entry.end_after);
            if !entry.text_before.is_empty() {
                doc.buffer.insert(entry.start, &entry.text_before);
            }
            doc.selection = entry.selection_before;
            doc.version += 1;
            doc.is_modified = true;
            true
        } else {
            false
        }
    }

    pub fn redo(&mut self) -> bool {
        let active_uri = match self.active_uri.clone() {
            Some(u) => u,
            None => return false,
        };
        let history = match self.histories.get_mut(&active_uri) {
            Some(h) => h,
            None => return false,
        };
        let entry = match history.pop_redo() {
            Some(e) => e,
            None => return false,
        };

        if let Some(doc) = self.documents.get_mut(&active_uri) {
            doc.buffer.delete_range(entry.start, entry.end_before);
            if !entry.text_after.is_empty() {
                doc.buffer.insert(entry.start, &entry.text_after);
            }
            doc.selection = entry.selection_after;
            doc.version += 1;
            doc.is_modified = true;
            true
        } else {
            false
        }
    }

    pub fn move_cursor(&mut self, direction: &str, select: bool) {
        if let Some(doc) = self.get_active_document_mut() {
            let head = doc.selection.head;
            let line_count = doc.buffer.line_count();
            let line_len = doc.buffer.line_length_chars(head.line);

            let new_head = match direction {
                "left" => {
                    if head.character > 0 {
                        Position::new(head.line, head.character - 1)
                    } else if head.line > 0 {
                        let prev_line = head.line - 1;
                        Position::new(prev_line, doc.buffer.line_length_chars(prev_line))
                    } else {
                        head
                    }
                }
                "right" => {
                    if head.character < line_len {
                        Position::new(head.line, head.character + 1)
                    } else if head.line + 1 < line_count {
                        Position::new(head.line + 1, 0)
                    } else {
                        head
                    }
                }
                "up" => {
                    if head.line > 0 {
                        let target_line = head.line - 1;
                        let target_len = doc.buffer.line_length_chars(target_line);
                        Position::new(target_line, head.character.min(target_len))
                    } else {
                        Position::new(0, 0)
                    }
                }
                "down" => {
                    if head.line + 1 < line_count {
                        let target_line = head.line + 1;
                        let target_len = doc.buffer.line_length_chars(target_line);
                        Position::new(target_line, head.character.min(target_len))
                    } else {
                        Position::new(head.line, line_len)
                    }
                }
                "line_start" => Position::new(head.line, 0),
                "line_end" => Position::new(head.line, line_len),
                _ => head,
            };

            if select {
                doc.selection.head = new_head;
            } else {
                doc.selection = Selection::caret(new_head);
            }
        }
    }

    pub fn set_selection(&mut self, anchor: Position, head: Position) {
        if let Some(doc) = self.get_active_document_mut() {
            let a = doc.buffer.clamp_position(anchor);
            let h = doc.buffer.clamp_position(head);
            doc.selection = Selection::new(a, h);
        }
    }

    pub fn select_word_at(&mut self, line: usize, col: usize) {
        if let Some(doc) = self.get_active_document_mut() {
            let line_count = doc.buffer.line_count();
            if line >= line_count { return; }
            let line_str = match doc.buffer.get_line(line) {
                Some(s) => s,
                None => return,
            };
            let chars: Vec<char> = line_str.chars().collect();
            if chars.is_empty() {
                doc.selection = Selection::caret(Position::new(line, 0));
                return;
            }
            let target_col = col.min(chars.len() - 1);
            let is_word_char = |c: char| c.is_alphanumeric() || c == '_';
            if !is_word_char(chars[target_col]) {
                doc.selection = Selection::new(
                    Position::new(line, target_col),
                    Position::new(line, target_col + 1),
                );
                return;
            }
            let mut start_col = target_col;
            while start_col > 0 && is_word_char(chars[start_col - 1]) {
                start_col -= 1;
            }
            let mut end_col = target_col + 1;
            while end_col < chars.len() && is_word_char(chars[end_col]) {
                end_col += 1;
            }
            doc.selection = Selection::new(
                Position::new(line, start_col),
                Position::new(line, end_col),
            );
        }
    }

    pub fn get_selected_text(&self) -> String {
        if let Some(doc) = self.get_active_document() {
            let start = doc.selection.start();
            let end = doc.selection.end();
            if start != end {
                doc.buffer.get_text_range(start, end)
            } else {
                doc.buffer.get_line(start.line).unwrap_or_default().to_string()
            }
        } else {
            String::new()
        }
    }

    pub fn delete_selection(&mut self) -> Option<TextChangeEvent> {
        let active_uri = self.active_uri.clone()?;
        let doc = self.documents.get_mut(&active_uri)?;
        let sel = doc.selection;
        if sel.is_empty() { return None; }

        let start = sel.start();
        let end = sel.end();
        let text_before = doc.buffer.get_text_range(start, end);

        let change = doc.apply_delete(start, end);

        let history = self.histories.get_mut(&active_uri)?;
        history.push(UndoEntry {
            start,
            end_before: end,
            end_after: start,
            text_before,
            text_after: String::new(),
            selection_before: sel,
            selection_after: Selection::caret(start),
        });

        Some(change)
    }

    pub fn select_all(&mut self) {
        if let Some(doc) = self.get_active_document_mut() {
            let last_line = doc.buffer.line_count().saturating_sub(1);
            let last_col = doc.buffer.line_length_chars(last_line);
            doc.selection = Selection::new(
                Position::new(0, 0),
                Position::new(last_line, last_col),
            );
        }
    }

    pub fn highlight_visible_lines(&self, start_line: usize, end_line: usize) -> Vec<(usize, Vec<TokenSpan>)> {
        let mut result = Vec::new();
        if let Some(doc) = self.get_active_document() {
            let highlighter = SyntaxHighlighter::for_language(&doc.language_id);
            let max_line = doc.buffer.line_count().min(end_line + 1);
            for l_idx in start_line..max_line {
                if let Some(line_str) = doc.buffer.get_line(l_idx) {
                    let spans = highlighter.highlight_line(line_str);
                    result.push((l_idx, spans));
                }
            }
        }
        result
    }

    pub fn get_render_state(&self) -> Option<RenderState> {
        let doc = self.get_active_document()?;
        let uri = doc.uri.clone();
        let history = self.histories.get(&uri);
        let matching_brackets = doc.buffer.find_matching_bracket(doc.selection.head);

        Some(RenderState {
            uri: doc.uri.clone(),
            language_id: doc.language_id.clone(),
            lines: (0..doc.buffer.line_count())
                .map(|i| doc.buffer.get_line(i).unwrap_or("").to_string())
                .collect(),
            line_count: doc.buffer.line_count(),
            cursor: doc.selection.head,
            selection: doc.selection,
            can_undo: history.map(|h| h.can_undo()).unwrap_or(false),
            can_redo: history.map(|h| h.can_redo()).unwrap_or(false),
            is_modified: doc.is_modified,
            matching_brackets,
        })
    }

    pub fn connect_lsp(&mut self, language_id: &str, server_command: &str, args: &[&str]) -> Result<(), String> {
        let client = LspClient::spawn(server_command, args)?;
        let arc_client = Arc::new(client);
        self.lsp_clients.insert(language_id.to_string(), arc_client.clone());

        // Initialize LSP with workspace
        let _ = arc_client.initialize("file:///workspace");

        // Sync existing open document for this language if any
        if let Some(doc) = self.get_active_document() {
            if doc.language_id == language_id {
                let _ = arc_client.did_open(&doc.uri, &doc.language_id, doc.version, &doc.buffer.get_text());
            }
        }
        Ok(())
    }

    pub fn lsp_completion(&self) -> Result<Vec<LspCompletionItem>, String> {
        let doc = self.get_active_document().ok_or_else(|| "No active document".to_string())?;
        let client = self.lsp_clients.get(&doc.language_id).ok_or_else(|| "No LSP client".to_string())?;
        let line_str = doc.buffer.get_line(doc.selection.head.line).unwrap_or("");
        let utf16_col = utf8_col_to_utf16_col(line_str, doc.selection.head.character) as u32;
        client.completion(&doc.uri, doc.selection.head.line as u32, utf16_col)
    }

    pub fn lsp_hover(&self) -> Result<Option<LspHover>, String> {
        let doc = self.get_active_document().ok_or_else(|| "No active document".to_string())?;
        let client = self.lsp_clients.get(&doc.language_id).ok_or_else(|| "No LSP client".to_string())?;
        let line_str = doc.buffer.get_line(doc.selection.head.line).unwrap_or("");
        let utf16_col = utf8_col_to_utf16_col(line_str, doc.selection.head.character) as u32;
        client.hover(&doc.uri, doc.selection.head.line as u32, utf16_col)
    }

    pub fn lsp_goto_definition(&self) -> Result<Vec<LspLocation>, String> {
        let doc = self.get_active_document().ok_or_else(|| "No active document".to_string())?;
        let client = self.lsp_clients.get(&doc.language_id).ok_or_else(|| "No LSP client".to_string())?;
        let line_str = doc.buffer.get_line(doc.selection.head.line).unwrap_or("");
        let utf16_col = utf8_col_to_utf16_col(line_str, doc.selection.head.character) as u32;
        client.goto_definition(&doc.uri, doc.selection.head.line as u32, utf16_col)
    }
}
