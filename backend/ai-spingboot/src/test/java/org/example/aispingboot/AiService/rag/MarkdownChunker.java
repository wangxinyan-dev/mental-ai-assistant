package org.example.aispingboot.AiService.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 轻量结构化 Markdown 分块器（P0 chunking 策略 C）。
 *
 * 背景：Spring AI 1.0.0 的 commons 里只有 TokenTextSplitter（不支持 overlap，见 RagService:97），
 * 没有 ParagraphTextSplitter / HeaderTextSplitter（那些是更高版本才有）。心理文章几乎都是 Markdown
 * （## 小节 + 段落），本节写一个极简、可测的「按二级标题层级切」chunker：
 *  - 每个 ## 小节独立成 chunk，chunk 内容=「## 小节标题行」+ 其下正文（到下一个 ## 前）；
 *  - 整块保留小节标题，保证「标题 + 正文」语义完整、黄金子串不因切块而切断；
 *  - 小节标题写进 metadata["section"]，供检索拼接 title + section 增强上下文。
 *
 * 不继承/不修改生产 TextSplitter，只产出 Spring AI 的 Document 结构供评测复用。
 *
 * 设计局限（诚实标注）：极简实现，仅按二级标题切分；不处理嵌套列表、代码块、引用块等
 * 复杂 Markdown；生产若按结构分块应换 HeaderTextSplitter 或自研更完整 splitter。
 */
public final class MarkdownChunker {

    private MarkdownChunker() {}

    /**
     * 将一篇 Markdown 文章按二级标题切分成多个 Document，每块含标题行+正文。
     *
     * @param title 文章标题（注入 metadata["title"]）
     * @param md    Markdown 正文（含 ## 小节）
     * @return 分块后的 Document 列表；每块内容 = ## 小节标题 + 其下正文
     */
    public static List<Document> splitByH2(String title, String md) {
        if (md == null || md.isBlank()) return List.of();

        // 1. 定位所有二级标题行的起始偏移（行首），非 `###`
        List<Integer> h2StartLines = new ArrayList<>(); // 每个 H2 标题所在行 index
        String[] lines = md.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].stripLeading();
            boolean isH2 = (t.startsWith("## ") || t.startsWith("##\t"))
                    || (t.startsWith("##") && !t.startsWith("###"));
            if (isH2) h2StartLines.add(i);
        }

        // 2. 无任何 H2 标题 → 整篇一个块（保留全部内容）
        if (h2StartLines.isEmpty()) {
            return List.of(new Document(md.strip(), Map.of("title", title, "section", "")));
        }

        // 3. 有 H2：按标题行位置切块。
        //    每块 = [第 i 个标题行 … 第 i+1 个标题行前)，其中：
        //    - 第 0 块从文章开头(第 0 行)开始，使「开场内容 + 第一个标题及正文」并入首块，不丢内容；
        //    - 之后每块从第 i 个标题行开始（该行即 `## xxx`），到下一标题行前结束。
        List<Document> docs = new ArrayList<>();
        int blockStartLine = 0; // 首块从文章开头开始
        for (int idx = 0; idx < h2StartLines.size(); idx++) {
            int curH2Line = h2StartLines.get(idx);
            // 首块收纳开场内容：起点=0（含首个标题行）；后续块起点=本标题行
            if (idx > 0) blockStartLine = curH2Line;
            int blockEndLine = (idx + 1 < h2StartLines.size()) ? h2StartLines.get(idx + 1) : lines.length;
            String block = String.join("\n",
                    java.util.Arrays.copyOfRange(lines, blockStartLine, blockEndLine)).strip();
            if (!block.isBlank()) {
                docs.addAll(splitSectionWithinBudget(block, title));
            }
        }
        return docs;
    }

    // ==================== 嵌入模型输入上限适配 ====================

    /**
     * 嵌入模型 bge-large-zh 输入上限 512 token，中文按 1 字≈1.5 token 保守折算，
     * 故单块内容超 {@link #MAX_CHUNK_CHARS} 字符时必须再切，否则 embedding 调用 400 崩溃
     * （实测 500-800 字小节直接触发）。
     *
     * 策略：段内按「换行段」边界累计，凑满预算即成块（保留标题行在首块），
     * 黄金锚点（句子/术语）在整段内不切断——与 B 定长切不同，这里不产生半句残块。
     */
    private static final int MAX_CHUNK_CHARS = 300; // ~450 token，给 512 留足余量

    /** 若单个小节内容超 MAX_CHUNK_CHARS，按段边界切碎；否则原样返回单块。 */
    private static List<Document> splitSectionWithinBudget(String block, String title) {
        if (block.length() <= MAX_CHUNK_CHARS) {
            return List.of(new Document(block, Map.of("title", title, "section", firstH2Title(block))));
        }
        // 超长：按换行段拆，凑满 budget 就收一个块；标题行并入首块保证上下文完整
        String section = firstH2Title(block);
        String[] paras = block.split("\n(?=\\S)", -1);
        List<Document> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String p : paras) {
            // 单个段落本身已超预算（无换行可拆）：按字符硬切，避免产生超长块
            if (p.length() > MAX_CHUNK_CHARS) {
                // 先把已累计的 cur 落盘
                if (cur.length() > 0) {
                    out.add(new Document(cur.toString().strip(), Map.of("title", title, "section", section)));
                    cur.setLength(0);
                }
                for (int s = 0; s < p.length(); s += MAX_CHUNK_CHARS) {
                    String piece = p.substring(s, Math.min(s + MAX_CHUNK_CHARS, p.length()));
                    out.add(new Document(piece.strip(), Map.of("title", title, "section", section)));
                }
                continue;
            }
            if (cur.length() + p.length() > MAX_CHUNK_CHARS && cur.length() > 0) {
                out.add(new Document(cur.toString().strip(), Map.of("title", title, "section", section)));
                cur.setLength(0);
            }
            cur.append(p).append('\n');
        }
        if (cur.length() > 0) {
            out.add(new Document(cur.toString().strip(), Map.of("title", title, "section", section)));
        }
        return out;
    }

    /** 从一段文本里取第一个 `## 标题` 的标题文本；无则返回空串。 */
    private static String firstH2Title(String block) {
        String firstLine = block.stripLeading().split("\n", 2)[0].strip();
        if (firstLine.startsWith("## ")) {
            String t = firstLine.stripLeading();
            if (t.startsWith("## ") || t.startsWith("##\t")
                    || (t.startsWith("##") && !t.startsWith("###"))) {
                return t.replaceFirst("^##\\s*", "").strip();
            }
        }
        return "";
    }

    /**
     * 将整篇按「定长字符 chunk + overlap」切（策略 B 用，TokenTextSplitter 无 overlap 的补充）。
     * 以「字符数」近似 token（中文场景 1 字≈1 token 是合理近似）；overlap 让边界语义不因切断而丢失。
     *
     * @param title     文章标题
     * @param text      纯净正文（无 Markdown 标题）
     * @param chunkSize 每块字符数
     * @param overlap   相邻块重叠字符数（必须 < chunkSize）
     */
    public static List<Document> splitByFixedSize(String title, String text, int chunkSize, int overlap) {
        if (text == null || text.isBlank()) return List.of();
        if (chunkSize <= 0 || overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("需要 0 <= overlap < chunkSize");
        }
        List<Document> out = new ArrayList<>();
        int start = 0;
        int n = text.length();
        int seq = 0;
        while (start < n) {
            int end = Math.min(start + chunkSize, n);
            String chunk = text.substring(start, end);
            if (!chunk.isBlank()) {
                out.add(new Document(chunk, Map.of("title", title, "chunk_seq", seq++)));
            }
            if (end >= n) break;
            start = end - overlap; // 回退 overlap 字符，重叠覆盖边界
        }
        return out;
    }

    /** 复用 Spring AI 的 TokenTextSplitter 分一篇（策略 A：现状一刀切，无 overlap）。 */
    public static List<Document> splitWith(TokenTextSplitter splitter, String title, String md) {
        var doc = new Document(md, Map.of("title", title));
        return splitter.split(doc);
    }
}
