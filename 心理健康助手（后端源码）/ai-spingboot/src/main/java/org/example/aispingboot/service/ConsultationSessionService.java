package org.example.aispingboot.service;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.aispingboot.DTO.command.ConsultationSessionCreateDTO;
import org.example.aispingboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispingboot.entity.ConsultationMessage;
import org.example.aispingboot.entity.ConsultationSession;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.mapper.ConsultationMessageMapper;
import org.example.aispingboot.mapper.ConsultationSessionMapper;
import org.example.aispingboot.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConsultationSessionService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;

    public ConsultationSession createSession(Long userId, ConsultationSessionCreateDTO createDTO) {
        // 验证用户是否存在
        User user =userMapper.selectById(userId);
        if (user != null) {
            // 创建会话记录
             ConsultationSession session = ConsultationSession.builder()
                    .userId(userId)
                    .sessionTitle(createDTO.getSessionTitle())
                    .startedAt(LocalDateTime.now())
                    .build();
            // 如果未提供标题
            if (StrUtil.isBlank(createDTO.getSessionTitle())) {
                session.setSessionTitle(String.format("AI心理助手 - " + DateUtil.format(LocalDateTime.now(), "MM-dd HH:mm")));
            }

            // 插入记录
            consultationSessionMapper.insert(session);
            return session;
        }

        return null;
    }

    public Map<String, Object> getUserSessions(Long userId, Integer pageNum, Integer pageSize) {
        // ① 分页查会话（1 次 SQL）
        Page<ConsultationSession> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ConsultationSession> qw = new LambdaQueryWrapper<>();
        qw.eq(ConsultationSession::getUserId, userId)
          .orderByDesc(ConsultationSession::getStartedAt);
        Page<ConsultationSession> result = consultationSessionMapper.selectPage(page, qw);

        List<ConsultationSession> sessions = result.getRecords();
        if (sessions.isEmpty()) {
            return Map.of("records", List.of(), "total", result.getTotal());
        }

        List<Long> sessionIds = sessions.stream().map(ConsultationSession::getId).collect(Collectors.toList());

        // ② 批量查消息数（1 次 SQL 替代 N 次）
        List<Map<String, Object>> countRows = consultationMessageMapper.batchCountBySessionIds(sessionIds);
        Map<Long, Integer> countMap = new HashMap<>();
        for (Map<String, Object> row : countRows) {
            countMap.put(((Number) row.get("session_id")).longValue(), ((Number) row.get("cnt")).intValue());
        }

        // ③ 批量查每个会话的最后一条消息（1 次 SQL 替代 N 次）
        List<ConsultationMessage> lastMessages = consultationMessageMapper.batchLastMessage(sessionIds);
        Map<Long, ConsultationMessage> lastMsgMap = lastMessages.stream()
                .collect(Collectors.toMap(ConsultationMessage::getSessionId, m -> m, (a, b) -> a));

        // ④ 查用户信息（1 次 SQL，userId 已知）
        User user = userMapper.selectById(userId);

        // ⑤ 内存组装，无额外 SQL
        List<Map<String, Object>> enriched = sessions.stream().map(session -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", session.getId());
            map.put("sessionTitle", session.getSessionTitle());
            map.put("startedAt", session.getStartedAt() != null ? session.getStartedAt().toString() : "");

            ConsultationMessage lastMsg = lastMsgMap.get(session.getId());
            map.put("lastMessageContent", lastMsg != null ? lastMsg.getContent() : "");
            map.put("lastMessageTime", lastMsg != null ? (lastMsg.getCreatedAt() != null ? lastMsg.getCreatedAt().toString() : "") : "");

            map.put("messageCount", countMap.getOrDefault(session.getId(), 0));

            map.put("userNickname", user != null ? (user.getNickname() != null ? user.getNickname() : user.getUsername()) : "");

            long minutes = 0;
            if (session.getStartedAt() != null) {
                LocalDateTime end = (lastMsg != null && lastMsg.getCreatedAt() != null)
                        ? lastMsg.getCreatedAt() : LocalDateTime.now();
                minutes = Duration.between(session.getStartedAt(), end).toMinutes();
            }
            map.put("durationMinutes", minutes);

            return map;
        }).collect(Collectors.toList());

        return Map.of("records", enriched, "total", result.getTotal());
    }

    public List<Map<String, Object>> getUserSessionsForAdmin(Integer pageNum, Integer pageSize) {
        // ① 分页查会话（1 次 SQL）
        Page<ConsultationSession> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ConsultationSession> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(ConsultationSession::getStartedAt);
        Page<ConsultationSession> result = consultationSessionMapper.selectPage(page, qw);

        List<ConsultationSession> sessions = result.getRecords();
        if (sessions.isEmpty()) {
            return List.of();
        }

        List<Long> sessionIds = sessions.stream().map(ConsultationSession::getId).collect(Collectors.toList());

        // ② 批量查用户信息（1 次 SQL，已有优化）
        List<Long> userIds = sessions.stream()
                .map(ConsultationSession::getUserId).distinct().collect(Collectors.toList());
        List<User> users = userIds.isEmpty() ? List.of() : userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        // ③ 批量查消息数（1 次 SQL 替代 N 次）
        List<Map<String, Object>> countRows = consultationMessageMapper.batchCountBySessionIds(sessionIds);
        Map<Long, Integer> countMap = new HashMap<>();
        for (Map<String, Object> row : countRows) {
            countMap.put(((Number) row.get("session_id")).longValue(), ((Number) row.get("cnt")).intValue());
        }

        // ④ 批量查最后一条消息（1 次 SQL 替代 N 次）
        List<ConsultationMessage> lastMessages = consultationMessageMapper.batchLastMessage(sessionIds);
        Map<Long, ConsultationMessage> lastMsgMap = lastMessages.stream()
                .collect(Collectors.toMap(ConsultationMessage::getSessionId, m -> m, (a, b) -> a));

        // ⑤ 内存组装，无额外 SQL
        List<Map<String, Object>> enriched = sessions.stream().map(session -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", session.getId());
            map.put("sessionTitle", session.getSessionTitle());
            User u = userMap.get(session.getUserId());
            map.put("userNickname", u != null ? (u.getNickname() != null ? u.getNickname() : u.getUsername()) : "");
            map.put("startedAt", session.getStartedAt() != null ? session.getStartedAt().toString() : "");

            ConsultationMessage lastMsg = lastMsgMap.get(session.getId());
            map.put("lastMessageContent", lastMsg != null ? lastMsg.getContent() : "");
            map.put("lastMessageTime", lastMsg != null ? (lastMsg.getCreatedAt() != null ? lastMsg.getCreatedAt().toString() : "") : "");

            map.put("messageCount", countMap.getOrDefault(session.getId(), 0));

            return map;
        }).collect(Collectors.toList());

        return enriched;
    }

    public int getTotalSessionCount() {
        return consultationSessionMapper.selectCount(null).intValue();
    }

    public void deleteSession(Long sessionId, Long userId) {
        ConsultationSession session = consultationSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在或无权限删除");
        }
        LambdaQueryWrapper<ConsultationMessage> msgQw = new LambdaQueryWrapper<>();
        msgQw.eq(ConsultationMessage::getSessionId, sessionId);
        consultationMessageMapper.delete(msgQw);
        consultationSessionMapper.deleteById(sessionId);
    }

    public List<ConsultationMessage> getSessionMessages(Long sessionId) {
        LambdaQueryWrapper<ConsultationMessage> qw = new LambdaQueryWrapper<>();
        qw.eq(ConsultationMessage::getSessionId, sessionId)
          .orderByAsc(ConsultationMessage::getCreatedAt);
        return consultationMessageMapper.selectList(qw);
    }

    public Map<String, Object> getSessionEmotion(Long sessionId) {
        ConsultationSession session = consultationSessionMapper.selectById(sessionId);
        if (session == null) {
            return new HashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("primaryEmotion", "中性");
        result.put("emotionScore", 50);
        result.put("isNegative", false);
        result.put("riskLevel", 0);
        result.put("suggestion", "情绪状态平稳，继续保持良好的心态");
        result.put("improvementSuggestions", List.of("坚持每日记录心情", "适当进行户外运动", "保持社交联系"));
        result.put("riskDescription", "");

        if (session.getLastEmotionAnalysis() != null && !session.getLastEmotionAnalysis().isEmpty()) {
            try {
                cn.hutool.json.JSONObject analysis = new cn.hutool.json.JSONObject(session.getLastEmotionAnalysis());
                if (analysis.containsKey("primaryEmotion")) result.put("primaryEmotion", analysis.get("primaryEmotion"));
                if (analysis.containsKey("emotionScore")) result.put("emotionScore", analysis.get("emotionScore"));
                if (analysis.containsKey("isNegative")) result.put("isNegative", analysis.get("isNegative"));
                if (analysis.containsKey("riskLevel")) result.put("riskLevel", analysis.get("riskLevel"));
                if (analysis.containsKey("suggestion")) result.put("suggestion", analysis.get("suggestion"));
                if (analysis.containsKey("improvementSuggestions")) result.put("improvementSuggestions", analysis.get("improvementSuggestions"));
                if (analysis.containsKey("riskDescription")) result.put("riskDescription", analysis.get("riskDescription"));
            } catch (Exception ignored) {}
        }

        return result;
    }

    public Map<String, Object> getAdminSessionPage(Integer currentPage, Integer size) {
        List<Map<String, Object>> records = getUserSessionsForAdmin(currentPage, size);
        int total = getTotalSessionCount();
        return Map.of("records", records, "total", total);
    }
}
