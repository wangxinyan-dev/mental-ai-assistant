package org.example.aispingboot.AiService;

import org.example.aispingboot.AiService.rag.RagService;
import org.example.aispingboot.AiService.safety.CrisisSafetyFilter;
import org.example.aispingboot.AiService.safety.PromptInjectionGuard;
import org.example.aispingboot.DTO.command.ConsultationSessionCreateDTO;
import org.example.aispingboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispingboot.entity.ConsultationSession;
import org.example.aispingboot.service.ConsultationMessageService;
import org.example.aispingboot.service.ConsultationSessionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class PsychologicalSupportService {
    @Autowired
    @Qualifier("open-ai")
    private ChatClient chatClient;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private ConsultationSessionService consultationSessionService;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    @Autowired
    private CrisisSafetyFilter crisisSafetyFilter;

    @Autowired
    private PromptInjectionGuard promptInjectionGuard;

    @Autowired
    private RagService ragService;

    public StructOutPut.StreamChatSession startSession(Long userId, ConsultationSessionCreateDTO createDTO) {
        // 创建数据库会话记录
        ConsultationSession consultationSession = consultationSessionService.createSession(userId, createDTO);

        // 将初始用户消息保存到Message表
        consultationMessageService.saveUserMessage(consultationSession.getId(), createDTO.getInitialMessage(), null);

        // 创建会话信息
        String sessionId = "session_" + consultationSession.getId();
        return new StructOutPut.StreamChatSession(
                sessionId,
                userId,
                createDTO.getInitialMessage(),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 86400000L, // 24小时
                1,
                "ACTIVE"
        );
    }

    public Flux<String> streamPsychologicalChat(String sessionId, String userMessage) {
        Long dbSessionId = extractSessionId(sessionId);
        if (dbSessionId == null) {
            return Flux.error(new RuntimeException("会话ID格式错误"));
        }

        // ============ AI安全层：Prompt注入检测 ============
        PromptInjectionGuard.GuardResult guardResult = promptInjectionGuard.check(userMessage);
        if (guardResult.isBlocked()) {
            saveUserMessageIfNeeded(dbSessionId, userMessage);
            // 拦截分支不走 ChatClient/advisor，需手动把用户消息加入对话记忆，保证上下文完整
            List<Message> interceptedUserMessages = new ArrayList<>();
            interceptedUserMessages.add(new UserMessage(userMessage));
            chatMemory.add("conversation_" + sessionId, interceptedUserMessages);
            // 模拟流式逐字输出安全回复，不调用大模型
            return streamSafeReply(dbSessionId, sessionId, guardResult.getSafeReply());
        }

        // ============ 心理安全层：检测危机关键词 ============
        CrisisSafetyFilter.CrisisResult crisisResult = crisisSafetyFilter.detect(userMessage);

        // 保存用户消息（如果不是初始会话消息）
        saveUserMessageIfNeeded(dbSessionId, userMessage);

        // 关键：buildSystemPrompt 内部的 RAG 检索（Embedding HTTP + PG JDBC）是阻塞 IO，
        // 用 Mono.fromCallable + subscribeOn(boundedElastic) 移到弹性线程池，
        // 避免阻塞 Netty 事件循环 / 请求线程
        return Mono.fromCallable(() -> buildSystemPrompt(crisisResult, userMessage))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(systemPrompt ->
                        buildChatStream(dbSessionId, sessionId, userMessage, crisisResult, systemPrompt));
    }

    /**
     * 构建 AI 聊天流（RAG 检索完成后调用），返回纯响应式 Flux：
     * chatClient 流式输出 → 危机热线拼接 → 收尾落库。不再使用 Flux.create + 手动 subscribe。
     */
    private Flux<String> buildChatStream(Long dbSessionId, String sessionId, String userMessage,
                                         CrisisSafetyFilter.CrisisResult crisisResult, String systemPrompt) {
        String conversationId = "conversation_" + sessionId;
        // 用户消息由 MessageChatMemoryAdvisor 自动写入 chatMemory，无需手动 add（避免重复占用记忆窗口）
        Prompt prompt = new Prompt(List.of(new SystemMessage(systemPrompt)));

        StringBuilder fullResponse = new StringBuilder();

        // AI 主流：累积回复内容
        Flux<String> mainStream = chatClient.prompt(prompt)
                .user(userMessage)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .doOnNext(fullResponse::append);

        // 命中危机关键词时，在 AI 回复末尾以"非阻塞逐字"方式推送援助热线
        if (crisisResult.isTriggered()) {
            mainStream = mainStream.concatWith(Flux.defer(() -> {
                String aiContent = fullResponse.toString();
                String finalContent = crisisSafetyFilter.appendHotlineIfNeeded(aiContent, crisisResult);
                // 回复中已包含热线号码则不重复追加
                if (finalContent.length() <= aiContent.length()) {
                    return Flux.empty();
                }
                String hotlineOnly = finalContent.substring(aiContent.length());
                List<String> chars = new ArrayList<>();
                for (char c : hotlineOnly.toCharArray()) {
                    chars.add(String.valueOf(c));
                }
                return Flux.fromIterable(chars)
                        .concatMap(c -> Mono.just(c).delayElement(Duration.ofMillis(10)));
            }));
        }

        return mainStream
                .doOnComplete(() -> {
                    // 收尾：保存最终回复（含热线）、更新对话记忆
                    String finalContent = crisisSafetyFilter.appendHotlineIfNeeded(fullResponse.toString(), crisisResult);
                    consultationMessageService.saveAimessage(dbSessionId, finalContent, "deepseek");
                    List<Message> aiMessages = new ArrayList<>();
                    aiMessages.add(new AssistantMessage(finalContent));
                    chatMemory.add(conversationId, aiMessages);
                })
                .onErrorResume(error -> {
                    // 即使大模型出错，如果有危机词也要把热线推给用户
                    if (crisisResult.isTriggered()) {
                        return Flux.just(CrisisSafetyFilter.MENTAL_HEALTH_HOTLINE)
                                .concatWith(Flux.error(error));
                    }
                    return Flux.error(error);
                });
    }

    /**
     * 当Prompt注入被拦截时，模拟流式逐字输出安全回复，不走大模型
     */
    private Flux<String> streamSafeReply(Long dbSessionId, String sessionId, String safeReply) {
        StringBuilder sb = new StringBuilder();
        List<String> chunks = splitIntoChunks(safeReply, 3);
        return Flux.fromIterable(chunks)
                .concatMap(c -> Mono.just(c).delayElement(Duration.ofMillis(40)))
                .doOnNext(chunk -> sb.append(chunk))
                .doOnComplete(() -> {
                    consultationMessageService.saveAimessage(dbSessionId, sb.toString(), "safety-guard");
                    String conversationId = "conversation_" + sessionId;
                    List<Message> aiMessages = new ArrayList<>();
                    aiMessages.add(new AssistantMessage(sb.toString()));
                    chatMemory.add(conversationId, aiMessages);
                });
    }

    /**
     * 根据危机等级，动态拼接系统提示词，增加危机干预指令
     * 同时通过RAG检索知识库相关片段，增强AI回复的专业性
     */
    private String buildSystemPrompt(CrisisSafetyFilter.CrisisResult crisisResult, String userMessage) {
        String base = PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT;

        // RAG检索：从知识库中检索与用户消息相关的专业心理知识片段
        String ragContext = ragService.buildAugmentedContext(userMessage);

        if (!crisisResult.isTriggered()) {
            return base + ragContext;
        }
        if (crisisResult.getLevel() == CrisisSafetyFilter.RiskLevel.HIGH) {
            return base + "\n\n【紧急危机干预指令】\n" +
                    "用户当前表达出强烈的自杀/自伤倾向（命中词汇：" + crisisResult.getMatchedKeywords() + "）。\n" +
                    "1. 请务必首先温柔而坚定地表达关心，不要评判；\n" +
                    "2. 认真共情其痛苦，但强调生命的价值和可改变性；\n" +
                    "3. 明确引导用户寻求现实中的帮助：家人、朋友、学校心理咨询中心；\n" +
                    "4. 务必在回复中以醒目的方式提醒全国24小时心理援助热线 400-161-9995；\n" +
                    "5. 禁止给出任何可能被理解为鼓励或默许伤害自己的建议。" + ragContext;
        }
        // 中风险
        return base + "\n\n【情绪风险提示】\n" +
                "用户当前情绪较为低落、绝望（命中词汇：" + crisisResult.getMatchedKeywords() + "）。\n" +
                "请以温暖、陪伴的口吻，引导用户表达内心，给予希望感；\n" +
                "并在回复末尾提醒：如感到无法支撑，可以拨打全国心理援助热线 400-161-9995。" + ragContext;
    }

    private void saveUserMessageIfNeeded(Long dbSessionId, String userMessage) {
        boolean isInitialMessage = false;
        Integer messageCount = consultationMessageService.getMessageCountBySessionId(dbSessionId);
        if (messageCount != null && messageCount == 1) {
            ConsultationMessageResponseDTO last = consultationMessageService.getLastMessageBySessionId(dbSessionId);
            if (last != null && last.getSenderType() == 1 && userMessage.equals(last.getContent())) {
                isInitialMessage = true;
            }
        }
        if (!isInitialMessage) {
            consultationMessageService.saveUserMessage(dbSessionId, userMessage, null);
        }
    }

    private List<String> splitIntoChunks(String text, int size) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            result.add(text.substring(i, Math.min(i + size, text.length())));
        }
        return result;
    }

    public Long extractSessionId(String sessionId) {
        if (sessionId != null && sessionId.startsWith("session_")) {
            String idStr = sessionId.substring("session_".length());
            return Long.parseLong(idStr);
        }
        return null;
    }
}
