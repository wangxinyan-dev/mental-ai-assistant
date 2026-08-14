package org.example.aispingboot.AiService.safety;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 心理安全层：危机关键词检测
 * 检测用户输入中的自杀/自伤等危机倾向词，
 * 并在AI回复末尾嵌入全国24小时心理援助热线
 */
@Component
public class CrisisSafetyFilter {

    public static final String MENTAL_HEALTH_HOTLINE =
            "\n\n-------------------------\n" +
            "💚 **生命热线提醒**：如果你或身边的人正处于危机中，请立即拨打全国24小时心理援助热线：\n" +
            "📞 **400-161-9995**（全国心理援助热线）\n" +
            "📞 **010-82951332**（北京心理危机研究与干预中心）\n" +
            "📞 **400-120-0022**（上海市心理援助热线）\n" +
            "💚 请记住：你不是一个人，总会有人愿意帮助你，生命值得被珍视。";

    /**
     * 高风险自杀/自伤关键词
     */
    private static final List<String> HIGH_RISK_KEYWORDS = Arrays.asList(
            "自杀", "想死", "不想活了", "不想活", "活不下去", "活不下去了",
            "结束生命", "结束自己", "了结生命", "了结自己",
            "割腕", "割手臂", "自残", "自伤", "伤害自己",
            "跳楼", "跳河", "跳桥", "上吊", "服毒", "吃安眠药",
            "我想死", "我要自杀", "我想结束", "想自杀", "想跳楼",
            "一起死", "自杀方式", "自杀计划", "想离开这个世界"
    );

    /**
     * 中风险抑郁/绝望关键词
     */
    private static final List<String> MEDIUM_RISK_KEYWORDS = Arrays.asList(
            "活着没意义", "活着没意思", "人生没意义", "没有希望",
            "绝望", "崩溃", "撑不下去", "撑不住了",
            "没人理解我", "没有人在乎我", "觉得我消失比较好",
            "解脱", "一了百了", "消失", "想逃离",
            "压抑到极点", "快疯了", "想放弃一切"
    );

    /**
     * 危机检测结果
     */
    public static class CrisisResult {
        private final boolean triggered;
        private final RiskLevel level;
        private final List<String> matchedKeywords;

        public CrisisResult(boolean triggered, RiskLevel level, List<String> matchedKeywords) {
            this.triggered = triggered;
            this.level = level;
            this.matchedKeywords = matchedKeywords;
        }

        public boolean isTriggered() { return triggered; }
        public RiskLevel getLevel() { return level; }
        public List<String> getMatchedKeywords() { return matchedKeywords; }
    }

    public enum RiskLevel {
        NONE, MEDIUM, HIGH
    }

    /**
     * 检测用户输入中的危机关键词
     */
    public CrisisResult detect(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return new CrisisResult(false, RiskLevel.NONE, Collections.emptyList());
        }
        String normalized = userMessage.toLowerCase();
        List<String> matched = new ArrayList<>();
        RiskLevel level = RiskLevel.NONE;

        // 高风险
        for (String keyword : HIGH_RISK_KEYWORDS) {
            if (normalized.contains(keyword.toLowerCase())) {
                matched.add(keyword);
                if (level.ordinal() < RiskLevel.HIGH.ordinal()) {
                    level = RiskLevel.HIGH;
                }
            }
        }
        // 中风险（高风险未命中时才判断中风险）
        if (level != RiskLevel.HIGH) {
            for (String keyword : MEDIUM_RISK_KEYWORDS) {
                if (normalized.contains(keyword.toLowerCase())) {
                    matched.add(keyword);
                    if (level.ordinal() < RiskLevel.MEDIUM.ordinal()) {
                        level = RiskLevel.MEDIUM;
                    }
                }
            }
        }

        boolean triggered = level != RiskLevel.NONE;
        return new CrisisResult(triggered, level, matched);
    }

    /**
     * 向AI回复追加心理援助热线
     * 命中高风险：强制追加；命中中风险：回复中不含热线则追加
     */
    public String appendHotlineIfNeeded(String aiResponse, CrisisResult result) {
        if (!result.isTriggered()) {
            return aiResponse;
        }
        if (aiResponse == null) {
            return MENTAL_HEALTH_HOTLINE;
        }
        // 如果回复里已经包含热线号码，则不重复追加
        if (aiResponse.contains("400-161-9995") || aiResponse.contains("4001619995")) {
            return aiResponse;
        }
        return aiResponse + MENTAL_HEALTH_HOTLINE;
    }
}
