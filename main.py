from pathlib import Path

p = Path("app/src/main/java/com/crestcode/MainActivity.kt")
s = p.read_text()

old = """                        ) {
                            CrestEditorScreen(
                                onWebViewCreated = { wv ->
                                    webView = wv
                                    if (activeTabId.isNotEmpty()) {
                                        openFile(File(activeTabId))
                                    }
                                },
                                bridge = remember { CrestAndroidBridge() }
                            )

                            if (openTabs.isEmpty()) {"""

new = """                        ) {
                            if (showTerminal) {
                                TerminalScreen(
                                    onClose = {
                                        showTerminal = false
                                    },
                                    initialPath = workspaceDir.absolutePath,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                CrestEditorScreen(
                                    onWebViewCreated = { wv ->
                                        webView = wv
                                        if (activeTabId.isNotEmpty()) {
                                            openFile(File(activeTabId))
                                        }
                                    },
                                    bridge = remember { CrestAndroidBridge() }
                                )
                            }

                            if (!showTerminal && openTabs.isEmpty()) {"""

if old not in s:
    raise SystemExit("❌ Expected editor block not found")

s = s.replace(old, new, 1)

p.write_text(s)
print("✅ TerminalScreen connected to showTerminal state")
