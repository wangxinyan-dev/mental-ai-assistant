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
        return Flux.create(sink -> {
            Long dbSessionId = extractSessionId(sessionId);
            if (dbSessionId == null) {
                sink.error(new RuntimeException("会话ID格式错误"));
                return;
            }

            // ============ AI安全层：Prompt注入检测 ============
            PromptInjectionGuard.GuardResult guardResult = promptInjectionGuard.check(userMessage);
            if (guardResult.isBlocked()) {
                saveUserMessageIfNeeded(dbSessionId, userMessage);
                // 模拟流式逐字输出安全回复，不调用大模型
                String safeReply = guardResult.getSafeReply();
                streamSafeReplyBlocking(sink, dbSessionId, sessionId, safeReply);
                return;
            }

            // ============ 心理安全层：检测危机关键词 ============
            CrisisSafetyFilter.CrisisResult crisisResult = crisisSafetyFilter.detect(userMessage);

            // 保存用户消息（如果不是初始会话消息）
            saveUserMessageIfNeeded(dbSessionId, userMessage);

            // 对话记忆与Prompt
            String conversationId = "conversation_" + sessionId;
            List<Message> userMessages = new ArrayList<>();
            userMessages.add(new UserMessage(userMessage));
            chatMemory.add(conversationId, userMessages);
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(buildSystemPrompt(crisisResult, userMessage))
            ));

            StringBuilder fullResponse = new StringBuilder();

            chatClient.prompt(prompt)
                    .user(userMessage)
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .stream()
                    .content()
                    .doOnNext(fragment -> {
                        fullResponse.append(fragment);
                        sink.next(fragment);
                    })
                    .doOnComplete(() -> {
                        // 心理安全层：若命中危机关键词，在AI回复末尾嵌入援助热线
                        String aiContent = fullResponse.toString();
                        String finalContent = crisisSafetyFilter.appendHotlineIfNeeded(aiContent, crisisResult);
                        // 如果追加热线，则把热线再次流式输出
                        if (crisisResult.isTriggered() && finalContent.length() > aiContent.length()) {
                            String hotlineOnly = finalContent.substring(aiContent.length());
                            // 热线按字符推送，保持流式体验
                            char[] hotlineChars = hotlineOnly.toCharArray();
                            for (char c : hotlineChars) {
                                sink.next(String.valueOf(c));
                            }
                            try { Thread.sleep(10); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                        }
                        // 保存最终AI回复（含热线）
                        consultationMessageService.saveAimessage(dbSessionId, finalContent, "deepseek");
                        List<Message> aiMessages = new ArrayList<>();
                        aiMessages.add(new AssistantMessage(finalContent));
                        chatMemory.add(conversationId, aiMessages);
                        sink.complete();
                    })
                    .doOnError(error -> {
                        // 即使大模型出错，如果有危机词也要把热线推给用户
                        if (crisisResult.isTriggered()) {
                            sink.next(CrisisSafetyFilter.MENTAL_HEALTH_HOTLINE);
                        }
                        sink.error(error);
                    })
                    .subscribe();
        });
    }

    /**
     * 当Prompt注入被拦截时，模拟流式逐字输出安全回复，不走大模型
     */
    private void streamSafeReplyBlocking(reactor.core.publisher.FluxSink<String> sink,
                                         Long dbSessionId,
                                         String sessionId,
                                         String safeReply) {
        StringBuilder sb = new StringBuilder();
        List<String> chunks = splitIntoChunks(safeReply, 3);
        Flux.fromIterable(chunks)
                .concatMap(c -> Mono.just(c).delayElement(Duration.ofMillis(40)))
                .doOnNext(chunk -> {
                    sb.append(chunk);
                    sink.next(chunk);
                })
                .doOnComplete(() -> {
                    consultationMessageService.saveAimessage(dbSessionId, sb.toString(), "safety-guard");
                    String conversationId = "conversation_" + sessionId;
                    List<Message> aiMessages = new ArrayList<>();
                    aiMessages.add(new AssistantMessage(sb.toString()));
                    chatMemory.add(conversationId, aiMessages);
                    sink.complete();
                })
                .doOnError(sink::error)
                .subscribe();
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
