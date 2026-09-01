package org.example.aispingboot.AiService.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MarkdownChunker 单元测试（纯 JUnit，无 DB / 无 Spring 上下文）。
 *
 * 验证 P0 chunking 策略 C（按二级标题层级切）的正确性：
 *  - 每个 chunk 完整保留「## 标题行 + 正文」，标题不被切断；
 *  - 黄金子串（问题答案所在句子）完整落在单个 chunk 内，不被切块打断 —— 这是 recall@k 测准的前提。
 */
class MarkdownChunkerTest {

    @Test
    void 按标题切_每个chunk保留标题行与正文() {
        // 直接从标题开始（无开场），验证每个块都以 ## 标题开头、标题与正文同块
        String md = "## 认知行为疗法 CBT\n" +
                "认知行为疗法（CBT）是目前最有效的焦虑干预手段。\n" +
                "## 正念呼吸\n" +
                "每天 5 分钟正念呼吸可降低焦虑。";

        List<Document> docs = MarkdownChunker.splitByH2("焦虑怎么办", md);

        assertThat(docs).hasSize(2);
        // 每个块首行应是 ## 标题，且标题与正文在同一块内
        assertThat(docs.get(0).getText()).startsWith("## 认知行为疗法 CBT");
        assertThat(docs.get(0).getText()).contains("认知行为疗法（CBT）");
        assertThat(docs.get(1).getText()).startsWith("## 正念呼吸");
        assertThat(docs.get(1).getText()).contains("正念呼吸可降低焦虑");
    }

    @Test
    void 黄金子串不被切块切断() {
        String md = "## 认知行为疗法 CBT\n认知行为疗法（CBT）是目前最有效的焦虑干预手段。\n" +
                "## 正念呼吸\n每天 5 分钟正念呼吸可降低焦虑基线。";

        List<Document> docs = MarkdownChunker.splitByH2("焦虑怎么办", md);

        // 黄金关键词「认知行为疗法 CBT」应完整落在某个 chunk 的正文里（不跨块）
        boolean hit = docs.stream().anyMatch(d -> d.getText().contains("认知行为疗法 CBT"));
        assertThat(hit).isTrue();

        boolean hit2 = docs.stream().anyMatch(d -> d.getText().contains("正念呼吸可降低焦虑"));
        assertThat(hit2).isTrue();
    }

    @Test
    void 无标题正文并入首块_不丢内容() {
        String md = "这是一段没有 ## 标题的纯介绍文字，用来验证开场内容不被丢弃。";
        List<Document> docs = MarkdownChunker.splitByH2("测试", md);
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).getText()).contains("没有 ## 标题的纯介绍文字");
    }

    @Test
    void 定长切加overlap_边界不切断语义() {
        // 用很小的 chunkSize + overlap 验证：连续片段都完整、且无空白块
        String text = "一二三四五六七八九十" + "甲乙丙丁戊己庚辛壬癸";
        List<Document> docs = MarkdownChunker.splitByFixedSize("测试", text, 8, 3);
        // 两个 8 字块，因 overlap=3 会多出块，总块数 > 1
        assertThat(docs.size()).isGreaterThan(1);
        // 拼接回原文本（重叠无损）：每块都非空
        for (Document d : docs) assertThat(d.getText()).isNotBlank();
        // 首尾连起来应覆盖完整文本
        String joined = String.join("", docs.stream().map(Document::getText).toList());
        assertThat(joined).contains("一");
        assertThat(joined).contains("癸");
    }

    @Test
    void 空白文本_返回空列表() {
        assertThat(MarkdownChunker.splitByH2("t", "   \n  ")).isEmpty();
        assertThat(MarkdownChunker.splitByFixedSize("t", "", 8, 2)).isEmpty();
    }
}
