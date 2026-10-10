use std::io::{BufRead, BufReader, Read, Write};

/// Reads framed JSON-RPC message from LSP stdout stream.
pub fn read_message<R: Read>(reader: &mut BufReader<R>) -> Result<Option<String>, std::io::Error> {
    let mut content_length: Option<usize> = None;
    let mut line = String::new();

    loop {
        line.clear();
        let bytes_read = reader.read_line(&mut line)?;
        if bytes_read == 0 {
            return Ok(None); // EOF
        }

        let trimmed = line.trim();
        if trimmed.is_empty() {
            // End of headers, body follows
            break;
        }

        if let Some((key, val)) = trimmed.split_once(':') {
            if key.trim().eq_ignore_ascii_case("Content-Length") {
                if let Ok(len) = val.trim().parse::<usize>() {
                    content_length = Some(len);
                }
            }
        }
    }

    if let Some(length) = content_length {
        let mut buf = vec![0u8; length];
        reader.read_exact(&mut buf)?;
        let json_str = String::from_utf8(buf)
            .map_err(|e| std::io::Error::new(std::io::ErrorKind::InvalidData, e))?;
        Ok(Some(json_str))
    } else {
        Err(std::io::Error::new(
            std::io::ErrorKind::InvalidData,
            "LSP message missing Content-Length header",
        ))
    }
}

/// Writes framed JSON-RPC message to LSP stdin stream.
pub fn write_message<W: Write>(writer: &mut W, json_payload: &str) -> Result<(), std::io::Error> {
    let header = format!("Content-Length: {}\r\n\r\n", json_payload.len());
    writer.write_all(header.as_bytes())?;
    writer.write_all(json_payload.as_bytes())?;
    writer.flush()?;
    Ok(())
}
