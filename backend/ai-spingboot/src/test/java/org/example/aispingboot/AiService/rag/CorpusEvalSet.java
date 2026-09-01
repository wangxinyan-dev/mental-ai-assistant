package org.example.aispingboot.AiService.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 从「自建高仿真模拟语料」目录构建评测集（RagEvalSet.EvalSet）。
 *
 * 背景：第一版 RagEvalSet 内置 20 篇模拟文，答案跨篇唯一 → 测不出 chunking 区分度。
 * 本 loader 读取 scripts/corpus 下由 DeepSeek 生成的「答案非唯一、多篇冗余」语料：
 *   每簇(如失眠)有 8 篇相似但不同的文章，黄金知识点在 8 篇里都以不同措辞出现，
 *   让评测在「多篇相近文档」间逼 chunking / rerank 做区分，这才有正向/负向信号。
 *
 * 数据性质：自建模拟知识库，仅技术验证，不含版权第三方内容（同 RagEvalSet 声明）。
 * 文件路径约定（相对项目根）：
 *   scripts/corpus/articles/*.md      每篇一文件
 *   scripts/corpus/eval_questions.json 评测问题 + 黄金关键词
 * 目录缺失/为空时返回空 EvalSet（调用方自行判定跳过），不抛异常中断。
 */
public final class CorpusEvalSet {

    private CorpusEvalSet() {}

    /**
     * 语料根目录（相对模块工作目录）。
     * Maven test 的工作目录在 backend/ai-spingboot 下；语料在项目根 scripts/corpus，
     * 即需回溯两层（.. 到 backend，再 .. 到项目根）后再进 scripts/corpus。
     */
    private static final Path CORPUS_ROOT = Paths.get("..", "..", "scripts", "corpus");

    /**
     * 读语料目录构建评测集。
     * @return 非空 EvalSet；若语料目录/问题文件缺失则返回空集（articles/questions 均为空）。
     */
    public static RagEvalSet.EvalSet load() {
        Path articlesDir = CORPUS_ROOT.resolve("articles");
        Path qFile = CORPUS_ROOT.resolve("eval_questions.json");
        if (!Files.isDirectory(articlesDir) || !Files.isRegularFile(qFile)) {
            return new RagEvalSet.EvalSet(List.of(), List.of());
        }

        List<RagEvalSet.Article> articles = new ArrayList<>();
        try (var stream = Files.list(articlesDir)) {
            stream.filter(p -> p.toString().endsWith(".md"))
                  .sorted(Comparator.comparing(Path::getFileName))
                  .forEach(p -> {
                      try {
                          String md = Files.readString(p, StandardCharsets.UTF_8);
                          String title = md.stripLeading().split("\n", 2)[0].replaceAll("^#+", "").strip()
                                  .isEmpty() ? p.getFileName().toString() : md.stripLeading().split("\n", 2)[0];
                          articles.add(new RagEvalSet.Article(title, md));
                      } catch (IOException ignored) {
                          // 单篇读取失败则跳过该篇，不中断整体加载
                      }
                  });
        } catch (IOException e) {
            return new RagEvalSet.EvalSet(List.of(), List.of());
        }

        // 问题：eval_questions.json -> [{cluster, question, goldenKeyword}]
        List<RagEvalSet.EvalQuestion> questions = new ArrayList<>();
        try {
            ObjectMapper om = new ObjectMapper();
            JsonNode arr = om.readTree(Files.readAllBytes(qFile));
            if (arr != null && arr.isArray()) {
                arr.forEach(n -> questions.add(new RagEvalSet.EvalQuestion(
                        n.path("question").asText(""),
                        n.path("goldenKeyword").asText(""),
                        n.path("cluster").asText(""))));
            }
        } catch (IOException ignored) {
            // 问题文件损坏则视为无问题
        }
        return new RagEvalSet.EvalSet(articles, questions);
    }
}
