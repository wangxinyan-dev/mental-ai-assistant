package org.example.aispingboot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.aispingboot.entity.EmotionDiary;
import org.example.aispingboot.mapper.EmotionDiaryMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 情绪日记「AI 情绪分析」异步任务 —— 把 README 声称的"AI 生成关怀建议"落到真 LLM 调用。
 *
 * 背景（为什么之前字段为空）：ai_emotion_analysis 建表列、实体字段、管理端 emotional.vue 全套
 * "AI 情绪分析结果"面板（primaryEmotion/emotionScore/riskLevel/isNegative/suggestion/…）都就绪，
 * 但后端从不写值（全仓库无 setAiEmotionAnalysis 调用）→ 管理端面板始终空白。本类补齐最后一环。
 *
 * 设计（独立 Bean + @Async，照 RagAsyncTask 模式）：
 * - Spring @Async 基于 AOP 代理，同类内部互调绕代理会失效 → 抽独立 Bean，由 EmotionDiaryService 跨 Bean 调用。
 * - 用 ragTaskExecutor 线程池隔离，不阻塞日记保存主链路。
 * - LLM 返回纯 JSON 但有被 markdown 围栏/多余文本包裹的风险 → 解析容错 + 兜底默认，失败仅记日志不抛（异步异常不传播，须自吞）。
 */
@Slf4j
@Component
public class EmotionAsyncTask {

    private final EmotionDiaryMapper diaryMapper;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmotionAsyncTask(EmotionDiaryMapper diaryMapper,
                            @Qualifier("open-ai") ChatClient chatClient) {
        this.diaryMapper = diaryMapper;
        this.chatClient = chatClient;
    }

    /**
     * 异步对单条日记做 AI 情绪分析，写出 aiEmotionAnalysis JSON。
     * 失败只记日志回滚不动主业务（保存日记已成功）。
     */
    @Async("ragTaskExecutor")
    public void analyzeDiary(Long diaryId) {
        long start = System.currentTimeMillis();
        try {
            EmotionDiary diary = diaryMapper.selectById(diaryId);
            if (diary == null) {
                log.warn("[EMOTION-AI] 日记 {} 不存在，跳过情绪分析", diaryId);
                return;
            }
            String analysisJson = callEmotionAnalysis(diary);
            diary.setAiEmotionAnalysis(analysisJson);
            diary.setUpdatedAt(java.time.LocalDateTime.now());
            diaryMapper.updateById(diary);
            log.info("[EMOTION-AI] 日记 {} AI情绪分析完成，耗时 {}ms，analysis={}",
                    diaryId, System.currentTimeMillis() - start,
                    analysisJson != null ? analysisJson.length() : 0);
        } catch (Exception e) {
            log.error("[EMOTION-AI] 日记 {} AI情绪分析失败，耗时 {}ms: {}",
                    diaryId, System.currentTimeMillis() - start, e.getMessage(), e);
        }
    }

    /**
     * 调 LLM 生成情绪分析并返回 JSON 字符串。
     * 解析失败返回一套安全的兜底 JSON（永不返回 null、不抛），保证管理端面板有据可渲。
     */
    private String callEmotionAnalysis(EmotionDiary diary) {
        String prompt = buildPrompt(diary);
        try {
            // prompt 是系统+用户二合一的中文指令，让模型严格返回 JSON
            String raw = chatClient.prompt()
                    .system("你是一名专业的心理健康分析师。根据用户的情绪日记记录，输出一份 JSON 格式的情绪分析。"
                            + "只输出 JSON 本身，不要包含 markdown 围栏、不要有多余解释。")
                    .user(prompt)
                    .call()
                    .content();
            if (raw == null || raw.isBlank()) {
                return fallbackJson(diary);
            }
            String parsed = extractJson(raw);
            // 校验关键字段存在，缺失走兜底
            JsonNode node = objectMapper.readTree(parsed);
            if (node == null || !node.has("primaryEmotion")) {
                return fallbackJson(diary);
            }
            return parsed;
        } catch (Exception e) {
            log.warn("[EMOTION-AI] 日记 {} LLM 分析解析失败，回退默认: {}", diary.getId(), e.getMessage());
            return fallbackJson(diary);
        }
    }

    private String buildPrompt(EmotionDiary diary) {
        return "日记内容：" + (diary.getDiaryContent() == null ? "（无）" : diary.getDiaryContent()) + "\n"
                + "情绪评分（0-10）：" + diary.getMoodScore() + "\n"
                + "主导情绪：" + (diary.getDominantEmotion() == null ? "（未标注）" : diary.getDominantEmotion()) + "\n"
                + "睡眠质量（0-5）：" + diary.getSleepQuality() + "\n"
                + "压力水平（0-5）：" + diary.getStressLevel() + "\n\n"
                + "请输出如下 JSON 结构（严格字段名）：\n"
                + "{"
                + "\"primaryEmotion\":\"主要情绪(如焦虑/快乐/悲伤/平静等)\","
                + "\"emotionScore\":0到100的整数,"
                + "\"riskLevel\":\"正常/关注/预警/危机\","
                + "\"isNegative\":true或false,"
                + "\"suggestion\":\"一段专业共情的建议(50字内)\","
                + "\"improvementSuggestions\":[\"建议1\",\"建议2\",\"建议3\"],"
                + "\"riskDescription\":\"风险描述，无风险则空字符串\""
                + "}";
    }

    /** 剥离 markdown 代码块围栏等包裹，提取纯 JSON 内容。 */
    private String extractJson(String raw) {
        String s = raw.trim();
        // 去掉 ```json ... ``` 围栏
        int start = s.indexOf("```");
        if (start >= 0) {
            int contentStart = s.indexOf('\n', start);
            int end = s.indexOf("```", contentStart);
            if (contentStart >= 0 && end > contentStart) {
                s = s.substring(contentStart + 1, end).trim();
            }
        }
        // 只取第一个 { 到最后一个 } 之间的内容
        int b = s.indexOf('{');
        int e = s.lastIndexOf('}');
        if (b >= 0 && e > b) {
            return s.substring(b, e + 1);
        }
        return s;
    }

    /** 兜底默认分析：保证管理端面板不空白（字段与 emotional.vue 渲染一致）。 */
    private String fallbackJson(EmotionDiary diary) {
        boolean negative = diary.getMoodScore() != null && diary.getMoodScore() <= 5;
        String primary = diary.getDominantEmotion() != null ? diary.getDominantEmotion() : "平静";
        String risk = (diary.getMoodScore() != null && diary.getMoodScore() <= 3) ? "关注" : "正常";
        return "{"
                + "\"primaryEmotion\":\"" + safe(primary) + "\","
                + "\"emotionScore\":" + (diary.getMoodScore() == null ? 50 : diary.getMoodScore() * 10) + ","
                + "\"riskLevel\":\"" + risk + "\","
                + "\"isNegative\":" + negative + ","
                + "\"suggestion\":\"保持记录与觉察，必要时可寻求专业或身边人的支持。\","
                + "\"improvementSuggestions\":[\"坚持每日记录心情\",\"适当进行户外运动\",\"保持规律作息\"],"
                + "\"riskDescription\":\"\""
                + "}";
    }

    private String safe(String s) {
        if (s == null) return "";
        return s.replace("\"", "\\\"");
    }
}
