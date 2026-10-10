pub mod buffer;
pub mod editor;
pub mod history;
pub mod jni_api;
pub mod lsp;
pub mod syntax;

pub use buffer::{Document, Position, Selection, TextBuffer};
pub use editor::EditorEngine;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_buffer_insert_delete_replace() {
        let mut buf = TextBuffer::from_str("Hello World");
        let end_pos = buf.insert(Position::new(0, 5), ", Rust");
        assert_eq!(buf.get_text(), "Hello, Rust World");
        assert_eq!(end_pos, Position::new(0, 11));

        let deleted = buf.delete_range(Position::new(0, 5), Position::new(0, 11));
        assert_eq!(deleted, ", Rust");
        assert_eq!(buf.get_text(), "Hello World");
    }

    #[test]
    fn test_buffer_multiline_operations() {
        let mut buf = TextBuffer::from_str("line 1\nline 2\nline 3");
        assert_eq!(buf.line_count(), 3);

        buf.insert(Position::new(1, 6), "\nline 2.5");
        assert_eq!(buf.line_count(), 4);
        assert_eq!(buf.get_line(2), Some("line 2.5"));
    }

    #[test]
    fn test_unicode_position_mapping() {
        use buffer::position::{
            byte_col_to_char_col, char_col_to_byte_col, utf16_col_to_utf8_col, utf8_col_to_utf16_col,
        };

        let sample = "🚀 Hello 世界";
        // 🚀 is 4 bytes UTF-8, 2 code units UTF-16, 1 char
        // 世界 is 3 bytes each UTF-8, 1 code unit each UTF-16, 1 char each

        let u16_offset = utf8_col_to_utf16_col(sample, 3); // 🚀, ' ', 'H'
        assert_eq!(u16_offset, 4); // 2 + 1 + 1

        let u8_idx = utf16_col_to_utf8_col(sample, 4);
        assert_eq!(u8_idx, 3);

        let byte_col = char_col_to_byte_col(sample, 1); // after rocket
        assert_eq!(byte_col, 4);

        let char_col = byte_col_to_char_col(sample, 4);
        assert_eq!(char_col, 1);
    }

    #[test]
    fn test_editor_undo_redo() {
        let mut engine = EditorEngine::new();
        engine.open_document("file:///test.rs", "rust", "fn main() {}");

        engine.insert_text("\n// test comment");
        assert!(engine.get_active_document().unwrap().buffer.get_text().contains("// test comment"));

        let undone = engine.undo();
        assert!(undone);
        assert_eq!(engine.get_active_document().unwrap().buffer.get_text(), "fn main() {}");

        let redone = engine.redo();
        assert!(redone);
        assert!(engine.get_active_document().unwrap().buffer.get_text().contains("// test comment"));
    }

    #[test]
    fn test_multi_document_management() {
        let mut engine = EditorEngine::new();
        engine.open_document("file:///a.rs", "rust", "struct A;");
        engine.open_document("file:///b.kt", "kotlin", "class B");

        assert_eq!(engine.get_active_document().unwrap().uri, "file:///b.kt");

        engine.set_active_document("file:///a.rs");
        assert_eq!(engine.get_active_document().unwrap().uri, "file:///a.rs");

        engine.close_document("file:///a.rs");
        assert_eq!(engine.get_active_document().unwrap().uri, "file:///b.kt");
    }

    #[test]
    fn test_syntax_highlighting() {
        let highlighter = syntax::SyntaxHighlighter::for_language("rust");
        let spans = highlighter.highlight_line("fn main() { let x = 42; }");
        assert!(!spans.is_empty());
        assert_eq!(spans[0].token_type, syntax::TokenType::Keyword); // "fn"
    }

    #[test]
    fn test_jsonrpc_framing() {
        use lsp::transport::{read_message, write_message};
        use std::io::Cursor;

        let payload = r#"{"jsonrpc":"2.0","id":1,"method":"test","params":{}}"#;
        let mut buf = Vec::new();
        write_message(&mut buf, payload).unwrap();

        let mut cursor = std::io::BufReader::new(Cursor::new(buf));
        let read_back = read_message(&mut cursor).unwrap().unwrap();
        assert_eq!(read_back, payload);
    }

    #[test]
    fn test_auto_closing_pairs_and_bracket_matching() {
        let mut engine = EditorEngine::new();
        engine.open_document("file:///test.rs", "rust", "fn test");

        // Move cursor to end of line
        engine.set_selection(Position::new(0, 7), Position::new(0, 7));

        // Insert opening bracket '(' -> should auto-close to '()'
        engine.insert_text("(");
        let doc = engine.get_active_document().unwrap();
        assert_eq!(doc.buffer.get_line(0), Some("fn test()"));
        assert_eq!(doc.selection.head, Position::new(0, 8)); // Cursor inside ()

        // Verify bracket matching detects (0,7) and (0,8)
        let brackets = doc.buffer.find_matching_bracket(Position::new(0, 8));
        assert!(brackets.is_some());
        let (open_pos, close_pos) = brackets.unwrap();
        assert_eq!(open_pos, Position::new(0, 7));
        assert_eq!(close_pos, Position::new(0, 8));

        // Delete backspace between () -> should erase both ( and )
        engine.delete_backspace();
        let doc_after = engine.get_active_document().unwrap();
        assert_eq!(doc_after.buffer.get_line(0), Some("fn test"));
    }

    #[test]
    fn test_word_selection_and_clipboard() {
        let mut engine = EditorEngine::new();
        engine.open_document("file:///test.rs", "rust", "hello world_code test");

        // Long press word selection at 'world_code' (char 7)
        engine.select_word_at(0, 7);
        assert_eq!(engine.get_selected_text(), "world_code");

        // Delete selection
        engine.delete_selection();
        let doc = engine.get_active_document().unwrap();
        assert_eq!(doc.buffer.get_line(0), Some("hello  test"));

        // Select all
        engine.select_all();
        assert_eq!(engine.get_selected_text(), "hello  test");
    }
}
