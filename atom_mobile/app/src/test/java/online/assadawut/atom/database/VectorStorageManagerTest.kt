package online.assadawut.atom.database

import online.assadawut.atom.model.ExecutionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for VectorStorageManager retrieval.
 *
 * Uses a deterministic fake embedder so no Ollama / HTTP is needed: identical
 * text maps to an identical vector, and unrelated text maps elsewhere. The
 * assertions here are the ones the previous implementation failed - it ignored
 * queryText entirely and returned two hard-coded strings.
 */
class VectorStorageManagerTest {

    /** Deterministic embedder: same input -> same vector, so results are stable. */
    private class FakeEmbedder : TextEmbedder {
        override fun embed(text: String): List<Float> = when {
            text.contains("กาแฟ") -> listOf(1f, 0f, 0f)
            text.contains("เสียง") -> listOf(0f, 1f, 0f)
            text.contains("โค้ด")  -> listOf(0f, 0f, 1f)
            // Generic hash-ish vector for anything else, kept out of other axes.
            else -> listOf(0.2f, 0.2f, 0.2f)
        }
    }

    private lateinit var storage: VectorStorageManager

    @Before
    fun setUp() {
        storage = VectorStorageManager(embedder = FakeEmbedder(), store = VectorStore())
    }

    @Test
    fun `search returns different results for different queries`() {
        storage.remember("m1", "ผู้ใช้ชอบดื่มกาแฟ")
        storage.remember("m2", "ระบบสตรีมเสียงสด")

        val coffee = storage.searchSimilar(queryText = "กาแฟที่ชอบ", topK = 3)
        val audio = storage.searchSimilar(queryText = "เรื่องเสียง", topK = 3)

        assertTrue("coffee query must not be empty", coffee.isNotEmpty())
        assertTrue("audio query must not be empty", audio.isNotEmpty())
        assertTrue(
            "queries must not return identical id sets",
            coffee.map { it.id } != audio.map { it.id }
        )
        assertEquals("coffee query should surface the coffee memory", "m1", coffee.first().id)
        assertEquals("audio query should surface the audio memory", "m2", audio.first().id)
    }

    @Test
    fun `results are ordered by descending similarity`() {
        storage.remember("m1", "ผู้ใช้ชอบดื่มกาแฟ")
        storage.remember("m2", "ผู้ใช้ชอบดื่มกาแฟมาก")
        storage.remember("m3", "เรื่องเสียงและเสียงบันทึก")

        val results = storage.searchSimilar(queryText = "กาแฟ", topK = 3)

        assertTrue(results.size >= 2)
        for (i in 0 until results.size - 1) {
            assertTrue(
                "scores must descend: ${results[i].similarityScore} < " +
                    "${results[i + 1].similarityScore}",
                results[i].similarityScore >= results[i + 1].similarityScore
            )
        }
    }

    @Test
    fun `topK limits the number of results`() {
        storage.remember("m1", "ผู้ใช้ชอบดื่มกาแฟ")
        storage.remember("m2", "ผู้ใช้ชอบดื่มกาแฟมาก")
        storage.remember("m3", "ผู้ใช้ชอบดื่มกาแฟทุกเช้า")

        val results = storage.searchSimilar(queryText = "กาแฟ", topK = 2)
        assertEquals("topK=2 must cap results", 2, results.size)
    }

    @Test
    fun `topK of zero or negative returns nothing`() {
        storage.remember("m1", "ผู้ใช้ชอบดื่มกาแฟ")
        assertTrue(storage.searchSimilar(queryText = "กาแฟ", topK = 0).isEmpty())
        assertTrue(storage.searchSimilar(queryText = "กาแฟ", topK = -1).isEmpty())
    }

    @Test
    fun `blank query returns nothing`() {
        storage.remember("m1", "ผู้ใช้ชอบดื่มกาแฟ")
        assertTrue(storage.searchSimilar(queryText = "", topK = 3).isEmpty())
        assertTrue(storage.searchSimilar(queryText = "   ", topK = 3).isEmpty())
    }

    @Test
    fun `empty store returns nothing`() {
        assertTrue(storage.searchSimilar(queryText = "กาแฟ", topK = 3).isEmpty())
    }

    @Test
    fun `remember stores the record`() {
        assertTrue(storage.remember("m1", "ผู้ใช้ชอบดื่มกาแฟ"))
        assertEquals(1, storage.storedCount())
    }

    @Test
    fun `remember rejects blank content`() {
        assertEquals(false, storage.remember("m1", ""))
        assertEquals(0, storage.storedCount())
    }

    @Test
    fun `remember with duplicate id replaces rather than appends`() {
        storage.remember("m1", "ผู้ใช้ชอบดื่มกาแฟ")
        storage.remember("m1", "ผู้ใช้ชอบดื่มชา")
        assertEquals(1, storage.storedCount())
    }

    @Test
    fun `identical vectors score 1 and orthogonal vectors score 0`() {
        val store = VectorStore()
        assertEquals(1.0, store.cosineSimilarity(listOf(1f, 0f), listOf(1f, 0f)), 1e-6)
        assertEquals(0.0, store.cosineSimilarity(listOf(1f, 0f), listOf(0f, 1f)), 1e-6)
        assertEquals(0.0, store.cosineSimilarity(listOf(1f, 0f), listOf(1f, 1f, 1f)), 1e-6)
        assertEquals(0.0, store.cosineSimilarity(listOf(0f, 0f), listOf(0f, 0f)), 1e-6)
        assertEquals(0.0, store.cosineSimilarity(emptyList(), listOf(1f)), 1e-6)
    }

    @Test
    fun `both execution modes return results`() {
        storage.remember("m1", "ผู้ใช้ชอบดื่มกาแฟ")
        for (mode in ExecutionMode.entries) {
            val results = storage.searchSimilar(
                queryText = "กาแฟ", topK = 3, mode = mode
            )
            assertTrue("mode $mode must return results", results.isNotEmpty())
        }
    }

    @Test
    fun `clearAll empties the store`() {
        storage.remember("m1", "ผู้ใช้ชอบดื่มกาแฟ")
        storage.clearAll()
        assertEquals(0, storage.storedCount())
        assertTrue(storage.searchSimilar(queryText = "กาแฟ", topK = 3).isEmpty())
    }
}