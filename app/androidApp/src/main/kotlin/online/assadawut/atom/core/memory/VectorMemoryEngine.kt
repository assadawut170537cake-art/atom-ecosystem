package online.assadawut.atom.core.memory

import online.assadawut.atom.database.MemoryRecord
import kotlin.math.sqrt

data class ScoredMemory(
    val memory: MemoryRecord,
    val similarityScore: Float
)

class VectorMemoryEngine {

    // Simple Bag-of-Words / Character N-Gram Feature Embedding Generator for Fast Local RAG
    fun generateEmbedding(text: String): FloatArray {
        val vocabularySize = 128
        val vector = FloatArray(vocabularySize)
        val cleanText = text.lowercase()

        for (char in cleanText) {
            val index = Math.abs(char.hashCode()) % vocabularySize
            vector[index] += 1.0f
        }

        // L2 Normalization
        val magnitude = sqrt(vector.map { it * it }.sum())
        if (magnitude > 0) {
            for (i in vector.indices) {
                vector[i] /= magnitude
            }
        }
        return vector
    }

    fun calculateCosineSimilarity(vecA: FloatArray, vecB: FloatArray): Float {
        if (vecA.size != vecB.size) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in vecA.indices) {
            dotProduct += vecA[i] * vecB[i]
            normA += vecA[i] * vecA[i]
            normB += vecB[i] * vecB[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    fun searchSimilarMemories(query: String, memories: List<MemoryRecord>, topK: Int = 3): List<ScoredMemory> {
        if (memories.isEmpty() || query.isBlank()) return emptyList()

        val queryVec = generateEmbedding(query)
        return memories.map { mem ->
            val memVec = generateEmbedding(mem.content)
            val score = calculateCosineSimilarity(queryVec, memVec)
            ScoredMemory(mem, score)
        }
        .sortedByDescending { it.similarityScore }
        .take(topK)
    }
}
