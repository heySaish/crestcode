package com.crestcode.editor

import org.junit.Assert.*
import org.junit.Test

class NativeEditorEngineTest {

    @Test
    fun testNativeEditorEngineFallbackOperations() {
        val engine = NativeEditorEngine()
        engine.openFile("file:///test.kt", "kotlin", "fun main() {\n    println(\"Hello\")\n}")

        val stateBefore = engine.getRenderState()
        assertEquals(3, stateBefore.lineCount)
        assertEquals("file:///test.kt", stateBefore.uri)
        assertEquals("kotlin", stateBefore.languageId)

        engine.insertText("// comment\n")
        val stateAfter = engine.getRenderState()
        assertTrue(stateAfter.isModified)

        val content = engine.getContent()
        assertTrue(content.contains("// comment"))

        engine.close()
    }
}
