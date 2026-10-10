use regex::Regex;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum TokenType {
    Keyword,
    Identifier,
    String,
    Number,
    Comment,
    Operator,
    Punctuation,
    Type,
    Function,
    Whitespace,
    Default,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TokenSpan {
    pub col_start: usize,
    pub col_end: usize,
    pub token_type: TokenType,
}

pub struct SyntaxHighlighter {
    keywords: Regex,
    types: Regex,
    numbers: Regex,
    strings: Regex,
    comments: Regex,
    operators: Regex,
}

impl SyntaxHighlighter {
    pub fn for_language(language_id: &str) -> Self {
        let kw_pattern = match language_id {
            "rust" => r"\b(fn|let|mut|pub|use|mod|struct|enum|impl|trait|where|if|else|loop|while|for|in|return|match|as|move|async|await|dyn|static|const)\b",
            "kotlin" => r"\b(fun|val|var|class|object|interface|package|import|if|else|when|for|while|return|by|is|in|sealed|data|abstract|open|override|private|protected|public|internal)\b",
            "python" => r"\b(def|class|import|from|as|if|elif|else|while|for|in|return|yield|try|except|finally|raise|with|lambda|global|nonlocal|pass|break|continue|async|await)\b",
            "javascript" | "typescript" => r"\b(function|const|let|var|class|interface|type|import|export|from|if|else|for|while|return|async|await|try|catch|new|this|extends|super)\b",
            "c" | "cpp" => r"\b(int|char|float|double|void|long|short|unsigned|struct|class|union|enum|if|else|for|while|do|return|switch|case|break|continue|new|delete|namespace|using|public|private)\b",
            _ => r"\b(if|else|for|while|return|function|class|val|var|let|const|import)\b",
        };

        let type_pattern = match language_id {
            "rust" => r"\b(u8|u16|u32|u64|u128|usize|i8|i16|i32|i64|i128|isize|f32|f64|bool|char|String|str|Option|Result|Vec|Self|self)\b",
            "kotlin" => r"\b(Int|Long|Float|Double|Boolean|Char|String|List|Map|Set|Any|Unit|Nothing)\b",
            _ => r"\b[A-Z][a-zA-Z0-9_]*\b",
        };

        Self {
            keywords: Regex::new(kw_pattern).unwrap(),
            types: Regex::new(type_pattern).unwrap(),
            numbers: Regex::new(r"\b0x[0-9a-fA-F]+|\b\d+(\.\d+)?\b").unwrap(),
            strings: Regex::new(r#""[^"\\]*(\\.[^"\\]*)*"|'[^'\\]*(\\.[^'\\]*)*'"#).unwrap(),
            comments: Regex::new(r"//.*|#.*|/\*[\s\S]*?\*/").unwrap(),
            operators: Regex::new(r"[\+\-\*/%=&\|!\^<>~\?:]+").unwrap(),
        }
    }

    pub fn highlight_line(&self, line: &str) -> Vec<TokenSpan> {
        let mut spans = Vec::new();
        let len = line.chars().count();
        if len == 0 {
            return spans;
        }

        // Default all to Default
        let mut types = vec![TokenType::Default; len];

        // Apply regex matching in priority order
        Self::apply_matches(line, &self.keywords, TokenType::Keyword, &mut types);
        Self::apply_matches(line, &self.types, TokenType::Type, &mut types);
        Self::apply_matches(line, &self.numbers, TokenType::Number, &mut types);
        Self::apply_matches(line, &self.operators, TokenType::Operator, &mut types);
        Self::apply_matches(line, &self.strings, TokenType::String, &mut types);
        Self::apply_matches(line, &self.comments, TokenType::Comment, &mut types);

        // Group contiguous ranges into TokenSpans
        let mut i = 0;
        while i < len {
            let current_type = types[i];
            let start = i;
            while i < len && types[i] == current_type {
                i += 1;
            }
            spans.push(TokenSpan {
                col_start: start,
                col_end: i,
                token_type: current_type,
            });
        }

        spans
    }

    fn apply_matches(line: &str, regex: &Regex, token_type: TokenType, types: &mut [TokenType]) {
        for m in regex.find_iter(line) {
            let start_char = line[..m.start()].chars().count();
            let end_char = start_char + m.as_str().chars().count();
            for idx in start_char..end_char {
                if idx < types.len() {
                    types[idx] = token_type;
                }
            }
        }
    }
}
