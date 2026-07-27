package com.pxuzy.floatingpen

import com.pxuzy.floatingpen.core.DrawingElement
import com.pxuzy.floatingpen.core.DrawingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class FloatInkHistoryRepositoryTest {
    private val context = org.robolectric.RuntimeEnvironment.getApplication()

    @Before
    fun cleanStorage() {
        FloatInkStorage.rootDirectory(context).deleteRecursively()
    }

    @Test
    fun `repository lists renames copies deletes and restores sessions`() {
        val repository = FloatInkHistoryRepository(context)
        val original = FloatInkStorage.sessionFile(context, "session-original")
        FloatInkSessionStore.save(original, DrawingSession().apply {
            addElement(DrawingElement.Line(0f to 0f, 1f to 1f, 1, 2f))
        }, "session-original")
        repository.register("session-original", "第一次讲解")

        assertEquals("第一次讲解", repository.list().single().name)
        assertTrue(repository.rename("session-original", "重命名会话"))
        assertEquals("重命名会话", repository.list().single().name)
        val copy = repository.copy("session-original")
        assertEquals(2, repository.list().size)
        assertTrue(copy!!.name.endsWith("副本"))

        assertTrue(repository.delete("session-original"))
        assertEquals(1, repository.list().size)
        assertEquals(1, repository.listTrash().size)
        assertTrue(repository.restore("session-original"))
        assertEquals(2, repository.list().size)
    }

    @Test
    fun `repository imports a valid floatink file as a new session`() {
        val source = File(context.cacheDir, "source.floatink")
        FloatInkSessionStore.save(source, DrawingSession(), "import-source")
        val repository = FloatInkHistoryRepository(context)

        val imported = repository.import(source)

        assertTrue(imported.file.exists())
        assertFalse(imported.sessionId == "import-source")
        assertEquals(1, repository.list().size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `repository rejects non floatink import`() {
        FloatInkHistoryRepository(context).import(File(context.cacheDir, "invalid.txt").apply { writeText("bad") })
    }
}
