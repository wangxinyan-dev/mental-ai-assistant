package org.example.aispingboot.AiService.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RRFusion 单元测试（纯 JUnit，无 DB）。
 *
 * 验证 RRF 融合算法的核心性质：
 *  - 在两路合并时，同时命中两路且名次都靠前的文档融合分更高；
 *  - 只在一路命中的文档按该路名次贡献；
 *  - 完全不命中的不在结果里。
 */
class RRFusionTest {

    @Test
    void 同时命中两路且都前_融合分高于只命中一路() {
        // 路1：doc 1 榜首
        List<Long> vec = List.of(1L, 2L, 3L);
        // 路2：doc 1 第二、doc 4 榜首
        List<Long> kw = List.of(4L, 1L, 5L);

        List<Long> fused = RRFusion.fuse(List.of(vec, kw), 10);

        // doc 1 在两路都命中且名次靠前（1 和 2），融合分最高 → 应排第一
        assertThat(fused.get(0)).isEqualTo(1L);
        // doc 4 只在一路榜首，应紧随 doc 1
        assertThat(fused.subList(0, 3)).contains(4L);
        // 未在任何一路出现的 doc 比如 9 不应混入
        assertThat(fused).doesNotContain(9L);
    }

    @Test
    void 只在单一路出现_按该路名次贡献() {
        List<Long> vec = List.of(1L, 2L);
        List<Long> kw = List.of(3L);
        List<Long> fused = RRFusion.fuse(List.of(vec, kw), 10);
        // 三路合并应包含全部 1,2,3
        assertThat(fused).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void 按融合分降序返回() {
        // 手动构造：doc 的融合分应随所在多路名次单调
        // 路1: A 榜首, B 第二, C 第三
        // 路2: B 榜首, C 第二, A 第三
        List<Long> r1 = List.of(10L, 20L, 30L);
        List<Long> r2 = List.of(20L, 30L, 10L);
        List<Long> fused = RRFusion.fuse(List.of(r1, r2), 3, 60);

        // 20L: 1/(61)+1/(61)=2/61≈0.0328
        // 10L: 1/(63)+1/61 ≈ 0.0159+0.0164
        // 30L: 1/62+1/62 ≈ 0.0323
        // 20L 最高 → 第一；10L 与 30L 排名为 3 名内
        assertThat(fused.get(0)).isEqualTo(20L);
        // 三者都在 top3
        assertThat(fused).containsExactlyInAnyOrder(10L, 20L, 30L);
    }

    @Test
    void topK截断_只返回前k个() {
        List<Long> r1 = List.of(1L, 2L, 3L, 4L, 5L);
        List<Long> fused = RRFusion.fuse(List.of(r1), 2);
        assertThat(fused).hasSize(2);
    }

    @Test
    void 空输入安全返回空() {
        assertThat(RRFusion.fuse(null, 3)).isEmpty();
        assertThat(RRFusion.fuse(List.of(), 3)).isEmpty();
        assertThat(RRFusion.fuse(List.of(List.of(1L)), 0)).isEmpty();
    }
}
