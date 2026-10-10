use crate::buffer::position::{char_col_to_byte_col, Position};

/// High-performance line-indexed text buffer.
/// Maintains text lines internally with fast insertion, deletion, replacement, and Unicode position queries.
#[derive(Debug, Clone)]
pub struct TextBuffer {
    lines: Vec<String>,
}

impl TextBuffer {
    pub fn new() -> Self {
        Self {
            lines: vec![String::new()],
        }
    }

    pub fn from_str(content: &str) -> Self {
        let lines: Vec<String> = if content.is_empty() {
            vec![String::new()]
        } else {
            // Normalize CRLF to LF internally
            let normalized = content.replace("\r\n", "\n").replace('\r', "\n");
            let mut l: Vec<String> = normalized.split('\n').map(|s| s.to_string()).collect();
            if l.is_empty() {
                l.push(String::new());
            }
            l
        };
        Self { lines }
    }

    pub fn get_text(&self) -> String {
        self.lines.join("\n")
    }

    pub fn line_count(&self) -> usize {
        self.lines.len()
    }

    pub fn get_line(&self, line_idx: usize) -> Option<&str> {
        self.lines.get(line_idx).map(|s| s.as_str())
    }

    pub fn line_length_chars(&self, line_idx: usize) -> usize {
        self.lines.get(line_idx).map(|s| s.chars().count()).unwrap_or(0)
    }

    pub fn line_length_utf16(&self, line_idx: usize) -> usize {
        self.lines
            .get(line_idx)
            .map(|s| s.chars().map(|c| c.len_utf16()).sum())
            .unwrap_or(0)
    }

    /// Clamp a position to valid buffer bounds.
    pub fn clamp_position(&self, pos: Position) -> Position {
        if self.lines.is_empty() {
            return Position::zero();
        }
        let line = pos.line.min(self.lines.len() - 1);
        let max_char = self.line_length_chars(line);
        let char_idx = pos.character.min(max_char);
        Position::new(line, char_idx)
    }

    /// Insert text at a specified position (line, character index).
    /// Returns the ending position after insertion.
    pub fn insert(&mut self, pos: Position, text: &str) -> Position {
        let clamped = self.clamp_position(pos);
        if text.is_empty() {
            return clamped;
        }

        let (prefix, suffix) = {
            let line_str = &self.lines[clamped.line];
            let byte_offset = char_col_to_byte_col(line_str, clamped.character);
            (
                line_str[..byte_offset].to_string(),
                line_str[byte_offset..].to_string(),
            )
        };

        let normalized = text.replace("\r\n", "\n").replace('\r', "\n");
        let new_lines: Vec<&str> = normalized.split('\n').collect();

        if new_lines.len() == 1 {
            let mut updated = String::with_capacity(prefix.len() + text.len() + suffix.len());
            updated.push_str(&prefix);
            updated.push_str(&normalized);
            updated.push_str(&suffix);
            self.lines[clamped.line] = updated;

            let end_char = clamped.character + normalized.chars().count();
            Position::new(clamped.line, end_char)
        } else {
            let mut first_line = String::with_capacity(prefix.len() + new_lines[0].len());
            first_line.push_str(&prefix);
            first_line.push_str(new_lines[0]);
            self.lines[clamped.line] = first_line;

            for i in 1..new_lines.len() - 1 {
                self.lines.insert(clamped.line + i, new_lines[i].to_string());
            }

            let last_idx = new_lines.len() - 1;
            let end_line_idx = clamped.line + last_idx;
            let end_char_count = new_lines[last_idx].chars().count();

            let mut last_line = String::with_capacity(new_lines[last_idx].len() + suffix.len());
            last_line.push_str(new_lines[last_idx]);
            last_line.push_str(&suffix);
            self.lines.insert(end_line_idx, last_line);

            Position::new(end_line_idx, end_char_count)
        }
    }

    /// Delete text between start position and end position.
    /// Returns deleted text string.
    pub fn delete_range(&mut self, start: Position, end: Position) -> String {
        let (start, end) = if start <= end { (start, end) } else { (end, start) };
        let start = self.clamp_position(start);
        let end = self.clamp_position(end);

        if start == end {
            return String::new();
        }

        if start.line == end.line {
            let line_str = &self.lines[start.line];
            let start_byte = char_col_to_byte_col(line_str, start.character);
            let end_byte = char_col_to_byte_col(line_str, end.character);

            let deleted = line_str[start_byte..end_byte].to_string();
            let mut updated = String::with_capacity(line_str.len() - (end_byte - start_byte));
            updated.push_str(&line_str[..start_byte]);
            updated.push_str(&line_str[end_byte..]);
            self.lines[start.line] = updated;
            deleted
        } else {
            let mut deleted_lines = Vec::new();

            let (start_kept, end_kept) = {
                let start_line_str = &self.lines[start.line];
                let start_byte = char_col_to_byte_col(start_line_str, start.character);
                deleted_lines.push(start_line_str[start_byte..].to_string());
                let s_kept = start_line_str[..start_byte].to_string();

                for line_idx in (start.line + 1)..end.line {
                    deleted_lines.push(self.lines[line_idx].clone());
                }

                let end_line_str = &self.lines[end.line];
                let end_byte = char_col_to_byte_col(end_line_str, end.character);
                deleted_lines.push(end_line_str[..end_byte].to_string());
                let e_kept = end_line_str[end_byte..].to_string();

                (s_kept, e_kept)
            };

            // Drain deleted lines in middle
            self.lines.drain((start.line + 1)..=end.line);

            let mut merged = String::with_capacity(start_kept.len() + end_kept.len());
            merged.push_str(&start_kept);
            merged.push_str(&end_kept);
            self.lines[start.line] = merged;

            deleted_lines.join("\n")
        }
    }

    /// Extract text within range (start, end).
    pub fn get_text_range(&self, start: Position, end: Position) -> String {
        let (start, end) = if start <= end { (start, end) } else { (end, start) };
        let start = self.clamp_position(start);
        let end = self.clamp_position(end);

        if start == end {
            return String::new();
        }

        if start.line == end.line {
            let line_str = &self.lines[start.line];
            let start_byte = char_col_to_byte_col(line_str, start.character);
            let end_byte = char_col_to_byte_col(line_str, end.character);
            line_str[start_byte..end_byte].to_string()
        } else {
            let mut result_lines = Vec::new();

            let start_line_str = &self.lines[start.line];
            let start_byte = char_col_to_byte_col(start_line_str, start.character);
            result_lines.push(start_line_str[start_byte..].to_string());

            for line_idx in (start.line + 1)..end.line {
                result_lines.push(self.lines[line_idx].clone());
            }

            let end_line_str = &self.lines[end.line];
            let end_byte = char_col_to_byte_col(end_line_str, end.character);
            result_lines.push(end_line_str[..end_byte].to_string());

            result_lines.join("\n")
        }
    }
}
