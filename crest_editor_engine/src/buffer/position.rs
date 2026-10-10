use serde::{Deserialize, Serialize};

/// Line (0-indexed) and character position.
/// In LSP, `character` represents UTF-16 code units.
/// In internal buffer, we provide methods to convert to UTF-8 byte offset, character offset, and column.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, Hash, Serialize, Deserialize)]
pub struct Position {
    pub line: usize,
    pub character: usize,
}

impl Position {
    pub fn new(line: usize, character: usize) -> Self {
        Self { line, character }
    }

    pub fn zero() -> Self {
        Self { line: 0, character: 0 }
    }
}

/// Selection range defined by anchor (where selection started) and head (cursor location).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct Selection {
    pub anchor: Position,
    pub head: Position,
}

impl Selection {
    pub fn new(anchor: Position, head: Position) -> Self {
        Self { anchor, head }
    }

    pub fn caret(pos: Position) -> Self {
        Self {
            anchor: pos,
            head: pos,
        }
    }

    pub fn is_empty(&self) -> bool {
        self.anchor == self.head
    }

    pub fn start(&self) -> Position {
        if self.anchor <= self.head {
            self.anchor
        } else {
            self.head
        }
    }

    pub fn end(&self) -> Position {
        if self.anchor <= self.head {
            self.head
        } else {
            self.anchor
        }
    }
}

/// Convert a string slice line's character index (UTF-8 code point count) to UTF-16 code unit offset.
pub fn utf8_col_to_utf16_col(line: &str, utf8_char_col: usize) -> usize {
    let mut utf16_offset = 0;
    for (i, c) in line.chars().enumerate() {
        if i >= utf8_char_col {
            break;
        }
        utf16_offset += c.len_utf16();
    }
    utf16_offset
}

/// Convert a line's UTF-16 code unit offset to UTF-8 character index.
pub fn utf16_col_to_utf8_col(line: &str, utf16_col: usize) -> usize {
    let mut current_utf16 = 0;
    let mut char_idx = 0;
    for c in line.chars() {
        if current_utf16 >= utf16_col {
            break;
        }
        current_utf16 += c.len_utf16();
        char_idx += 1;
    }
    char_idx
}

/// Convert character index (0-indexed char count) to byte offset in line.
pub fn char_col_to_byte_col(line: &str, char_col: usize) -> usize {
    line.char_indices()
        .nth(char_col)
        .map(|(b, _)| b)
        .unwrap_or(line.len())
}

/// Convert byte offset in line to character index (0-indexed char count).
pub fn byte_col_to_char_col(line: &str, byte_col: usize) -> usize {
    let target = byte_col.min(line.len());
    line.char_indices()
        .take_while(|(b, _)| *b < target)
        .count()
}
