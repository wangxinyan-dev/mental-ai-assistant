package org.example.aispingboot.AiService.rag;

import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * TF-IDF 向量存储与检索引擎
 *
 * 使用字符二元组（character bigrams）作为分词单元，
 * 适用于中文文本，无需引入中文分词库。
 *
 * 流程：
 * 1. buildIndex: 对所有分块构建词汇表 + 计算IDF + 生成TF-IDF向量
 * 2. search: 将查询文本向量化，与所有分块做余弦相似度，返回Top-K
 */
@Component
public class TfidfVectorStore {

    /**
     * 全局词汇表：term -> 在多少个文档中出现过（DF）
     */
    private Map<String, Integer> documentFrequency = new HashMap<>();

    /**
     * 总文档数（分块数）
     */
    private int totalDocuments = 0;

    /**
     * 已索引的分块列表（包含TF-IDF向量）
     */
    private final List<IndexedChunk> indexedChunks = new ArrayList<>();

    /**
     * 索引中的分块条目
     */
    public static class IndexedChunk {
        public final Long chunkId;
        public final Long articleId;
        public final String title;
        public final String content;
        public final Map<String, Double> vector;

        public IndexedChunk(Long chunkId, Long articleId, String title, String content, Map<String, Double> vector) {
            this.chunkId = chunkId;
            this.articleId = articleId;
            this.title = title;
            this.content = content;
            this.vector = vector;
        }
    }

    /**
     * 检索结果条目
     */
    public static class SearchResult {
        public final Long articleId;
        public final String title;
        public final String content;
        public final double score;

        public SearchResult(Long articleId, String title, String content, double score) {
            this.articleId = articleId;
            this.title = title;
            this.content = content;
            this.score = score;
        }
    }

    /**
     * 清空索引
     */
    public void clear() {
        documentFrequency.clear();
        totalDocuments = 0;
        indexedChunks.clear();
    }

    /**
     * 添加一批分块到索引中，并重新计算IDF
     *
     * @param chunks 分块列表，每个Entry包含：chunkId, articleId, title, content
     */
    public void buildIndex(List<ChunkEntry> chunks) {
        clear();

        // 第一遍：收集所有分块的词频，构建DF
        List<Map<String, Double>> tfVectors = new ArrayList<>();
        for (ChunkEntry entry : chunks) {
            Map<String, Double> tf = computeTermFrequency(entry.content);
            tfVectors.add(tf);

            // 更新DF：每个term在这个文档中出现过就+1
            for (String term : tf.keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        totalDocuments = chunks.size();

        // 第二遍：计算TF-IDF向量
        for (int i = 0; i < chunks.size(); i++) {
            ChunkEntry entry = chunks.get(i);
            Map<String, Double> tf = tfVectors.get(i);
            Map<String, Double> tfidf = computeTfidf(tf);

            indexedChunks.add(new IndexedChunk(
                    entry.chunkId, entry.articleId, entry.title, entry.content, tfidf
            ));
        }
    }

    /**
     * 检索与查询最相关的Top-K分块
     */
    public List<SearchResult> search(String query, int topK) {
        if (indexedChunks.isEmpty() || query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 计算查询的TF-IDF向量
        Map<String, Double> queryTf = computeTermFrequency(query);
        Map<String, Double> queryVector = computeTfidf(queryTf);

        // 计算余弦相似度
        List<SearchResult> results = new ArrayList<>();
        for (IndexedChunk chunk : indexedChunks) {
            double similarity = cosineSimilarity(queryVector, chunk.vector);
            if (similarity > 0) {
                results.add(new SearchResult(
                        chunk.articleId, chunk.title, chunk.content, similarity
                ));
            }
        }

        // 按相似度降序，取Top-K
        results.sort((a, b) -> Double.compare(b.score, a.score));
        if (results.size() > topK) {
            return results.subList(0, topK);
        }
        return results;
    }

    public int getIndexedChunkCount() {
        return indexedChunks.size();
    }

    /**
     * 将TF-IDF向量序列化为JSON字符串（用于数据库存储）
     */
    public static String serializeVector(Map<String, Double> vector) {
        return JSONUtil.toJsonStr(vector);
    }

    /**
     * 从JSON字符串反序列化TF-IDF向量
     */
    public static Map<String, Double> deserializeVector(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        return JSONUtil.toBean(json, Map.class);
    }

    // ==================== 内部方法 ====================

    /**
     * 中文文本分词：使用字符二元组（bigrams）
     * 例如 "心理健康" → ["心理", "理健", "健康"]
     * 对单字符文本，直接使用该字符
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.length() < 2) {
            if (text != null && !text.isEmpty()) {
                tokens.add(text);
            }
            return tokens;
        }

        for (int i = 0; i < text.length() - 1; i++) {
            // 跳过包含非中文字符的bigram（标点、数字等）
            char c1 = text.charAt(i);
            char c2 = text.charAt(i + 1);
            if (isChineseChar(c1) && isChineseChar(c2)) {
                tokens.add(text.substring(i, i + 2));
            }
        }
        return tokens;
    }

    private boolean isChineseChar(char c) {
        return (c >= '\u4e00' && c <= '\u9fff') || Character.isLetter(c);
    }

    /**
     * 计算词频（Term Frequency）
     * TF = term在文档中出现次数 / 文档总词数
     */
    private Map<String, Double> computeTermFrequency(String text) {
        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Integer> counts = new HashMap<>();
        for (String token : tokens) {
            counts.merge(token, 1, Integer::sum);
        }

        Map<String, Double> tf = new HashMap<>();
        int total = tokens.size();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            tf.put(entry.getKey(), (double) entry.getValue() / total);
        }
        return tf;
    }

    /**
     * 计算TF-IDF向量
     * IDF = log( (1 + N) / (1 + DF) ) + 1  （平滑处理）
     * TF-IDF = TF * IDF
     */
    private Map<String, Double> computeTfidf(Map<String, Double> tfVector) {
        Map<String, Double> tfidf = new HashMap<>();
        for (Map.Entry<String, Double> entry : tfVector.entrySet()) {
            String term = entry.getKey();
            double tf = entry.getValue();
            int df = documentFrequency.getOrDefault(term, 0);
            double idf = Math.log((double) (1 + totalDocuments) / (1 + df)) + 1;
            tfidf.put(term, tf * idf);
        }
        return tfidf;
    }

    /**
     * 计算两个稀疏向量的余弦相似度
     * cos(a, b) = dotProduct(a, b) / (|a| * |b|)
     */
    private double cosineSimilarity(Map<String, Double> v1, Map<String, Double> v2) {
        if (v1.isEmpty() || v2.isEmpty()) {
            return 0.0;
        }

        // 遍历较小的向量，计算点积
        Map<String, Double> smaller = v1.size() < v2.size() ? v1 : v2;
        Map<String, Double> larger = v1.size() < v2.size() ? v2 : v1;

        double dotProduct = 0.0;
        for (Map.Entry<String, Double> entry : smaller.entrySet()) {
            Double other = larger.get(entry.getKey());
            if (other != null) {
                dotProduct += entry.getValue() * other;
            }
        }

        if (dotProduct == 0.0) {
            return 0.0;
        }

        double norm1 = vectorNorm(v1);
        double norm2 = vectorNorm(v2);

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (norm1 * norm2);
    }

    private double vectorNorm(Map<String, Double> vector) {
        double sum = 0.0;
        for (double v : vector.values()) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    /**
     * 分块条目（用于构建索引时的输入）
     */
    public static class ChunkEntry {
        public final Long chunkId;
        public final Long articleId;
        public final String title;
        public final String content;

        public ChunkEntry(Long chunkId, Long articleId, String title, String content) {
            this.chunkId = chunkId;
            this.articleId = articleId;
            this.title = title;
            this.content = content;
        }
    }
}
