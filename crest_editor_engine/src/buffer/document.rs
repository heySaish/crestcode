use crate::buffer::piece_table::TextBuffer;
use crate::buffer::position::{Position, Selection};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TextChangeEvent {
    pub start: Position,
    pub end: Position,
    pub text: String,
}

#[derive(Debug, Clone)]
pub struct Document {
    pub uri: String,
    pub language_id: String,
    pub version: i64,
    pub buffer: TextBuffer,
    pub selection: Selection,
    pub is_modified: bool,
}

impl Document {
    pub fn new(uri: String, language_id: String, content: &str) -> Self {
        Self {
            uri,
            language_id,
            version: 1,
            buffer: TextBuffer::from_str(content),
            selection: Selection::caret(Position::zero()),
            is_modified: false,
        }
    }

    pub fn apply_insert(&mut self, text: &str) -> TextChangeEvent {
        let start = self.selection.start();
        let end = self.selection.end();

        if start != end {
            self.buffer.delete_range(start, end);
        }

        let new_pos = self.buffer.insert(start, text);
        self.selection = Selection::caret(new_pos);
        self.version += 1;
        self.is_modified = true;

        TextChangeEvent {
            start,
            end: new_pos,
            text: text.to_string(),
        }
    }

    pub fn apply_delete_backspace(&mut self) -> Option<TextChangeEvent> {
        if !self.selection.is_empty() {
            let start = self.selection.start();
            let end = self.selection.end();
            self.buffer.delete_range(start, end);
            self.selection = Selection::caret(start);
            self.version += 1;
            self.is_modified = true;
            return Some(TextChangeEvent {
                start,
                end,
                text: String::new(),
            });
        }

        let caret = self.selection.head;
        if caret.line == 0 && caret.character == 0 {
            return None;
        }

        let start = if caret.character > 0 {
            Position::new(caret.line, caret.character - 1)
        } else {
            let prev_line = caret.line - 1;
            let prev_line_len = self.buffer.line_length_chars(prev_line);
            Position::new(prev_line, prev_line_len)
        };

        self.buffer.delete_range(start, caret);
        self.selection = Selection::caret(start);
        self.version += 1;
        self.is_modified = true;

        Some(TextChangeEvent {
            start,
            end: caret,
            text: String::new(),
        })
    }
}
