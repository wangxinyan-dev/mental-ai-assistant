package org.example.aispingboot.AiService.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档分块器
 * 将长文本按固定大小切分为重叠片段，保证语义连续性
 *
 * 分块策略：chunkSize=512字符，overlap=128字符
 * 对中文文本，按字符（非字节）计算长度
 */
@Component
public class DocumentChunker {

    private static final int CHUNK_SIZE = 512;
    private static final int OVERLAP = 128;

    /**
     * 将文本切分为重叠分块
     *
     * @param text 原始文本（已去除HTML标签）
     * @return 分块列表
     */
    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        // 去除多余空白
        String cleaned = text.replaceAll("\\s+", " ").trim();

        if (cleaned.length() <= CHUNK_SIZE) {
            chunks.add(cleaned);
            return chunks;
        }

        int step = CHUNK_SIZE - OVERLAP; // 步长 = 384
        int start = 0;
        while (start < cleaned.length()) {
            int end = Math.min(start + CHUNK_SIZE, cleaned.length());
            String chunk = cleaned.substring(start, end);
            chunks.add(chunk);

            if (end >= cleaned.length()) {
                break;
            }
            start += step;
        }

        return chunks;
    }
}
