package com.crestcode.workspace

import android.content.Context
import com.crestcode.ui.components.FileTreeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object WorkspaceManager {

    fun getWorkspaceDir(context: Context): File {
        val workspace = File(context.filesDir, "workspace")
        if (!workspace.exists()) {
            workspace.mkdirs()
        }
        ensureStarterFiles(workspace)
        return workspace
    }

    private fun ensureStarterFiles(workspace: File) {
        val starterFiles = mapOf(
            "index.html" to """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>Crest Workspace</title>
  <link rel="stylesheet" href="style.css">
</head>
<body>
  <h1>Welcome to Crest Editor</h1>
  <p>Real file system backed workspace initialized!</p>
  <script src="main.js"></script>
</body>
</html>""",
            "main.js" to """// Crest Mobile Workspace Starter Script
console.log("Hello from Crest Editor!");

function calculateTotal(items) {
  return items.reduce((sum, item) => sum + item.price, 0);
}

const cart = [
  { name: "Crest App", price: 0 },
  { name: "Monaco Engine", price: 0 }
];

console.log("Total:", calculateTotal(cart));""",
            "style.css" to """/* Crest Editor Starter Styles */
body {
  background-color: #1e1e1e;
  color: #cccccc;
  font-family: system-ui, -apple-system, sans-serif;
  padding: 24px;
}

h1 {
  color: #4ec9b0;
}""",
            "notes.txt" to """Crest Editor Workspace Notes:
- High performance Android code editor
- Real internal storage backed file workspace
- Powered by Jetpack Compose & Monaco Editor
- Instant file & language switching"""
        )

        for ((filename, content) in starterFiles) {
            val file = File(workspace, filename)
            if (!file.exists()) {
                file.writeText(content)
            }
        }
    }

    fun listFilesRecursively(dir: File, level: Int = 0): List<FileTreeItem> {
        val result = mutableListOf<FileTreeItem>()
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return result

        for (file in files) {
            val item = FileTreeItem(
                id = file.absolutePath,
                name = file.name,
                isDirectory = file.isDirectory,
                level = level,
                path = file.absolutePath
            )
            result.add(item)
            if (file.isDirectory) {
                result.addAll(listFilesRecursively(file, level + 1))
            }
        }
        return result
    }

    fun detectLanguage(filename: String): String {
        val ext = filename.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "js", "jsx", "mjs", "cjs" -> "javascript"
            "ts", "tsx" -> "typescript"
            "html", "htm" -> "html"
            "css" -> "css"
            "json" -> "json"
            "md", "markdown" -> "markdown"
            "py" -> "python"
            "kt", "kts" -> "kotlin"
            "java" -> "java"
            "xml" -> "xml"
            "sql" -> "sql"
            "sh", "bash" -> "shell"
            else -> "plaintext"
        }
    }

    fun validateName(parentDir: File, name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return "A file or folder name must be provided."
        }
        val illegalChars = Regex("[/\\\\:*?\"<>|]")
        if (illegalChars.containsMatchIn(trimmed)) {
            return "The name is not valid as a file or folder name."
        }
        val target = File(parentDir, trimmed)
        if (target.exists()) {
            return "A file or folder '$trimmed' already exists at this location."
        }
        return null
    }

    suspend fun createNewFile(parentDir: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val trimmed = name.trim()
            val validationError = validateName(parentDir, trimmed)
            if (validationError != null) {
                return@withContext Result.failure(IllegalArgumentException(validationError))
            }
            val newFile = File(parentDir, trimmed)
            if (newFile.createNewFile()) {
                Result.success(newFile)
            } else {
                Result.failure(IllegalStateException("Could not create file."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createNewFolder(parentDir: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val trimmed = name.trim()
            val validationError = validateName(parentDir, trimmed)
            if (validationError != null) {
                return@withContext Result.failure(IllegalArgumentException(validationError))
            }
            val newDir = File(parentDir, trimmed)
            if (newDir.mkdirs()) {
                Result.success(newDir)
            } else {
                Result.failure(IllegalStateException("Could not create directory."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readFileContent(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (file.exists() && file.isFile) {
                Result.success(file.readText())
            } else {
                Result.failure(IllegalArgumentException("File does not exist or is not a file: ${file.path}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun writeFileContent(file: File, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            file.writeText(content)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
