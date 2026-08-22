package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.aispingboot.entity.*;
import org.example.aispingboot.mapper.*;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DataAnalyticsService {

    @Resource private UserMapper userMapper;
    @Resource private EmotionDiaryMapper diaryMapper;
    @Resource private ConsultationSessionMapper sessionMapper;
    @Resource private ConsultationMessageMapper messageMapper;
    @Resource private KnowledgeArticleMapper articleMapper;

    public Map<String, Object> getOverview() {
        Map<String, Object> result = new LinkedHashMap<>();

        // System Overview
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("totalUsers", userMapper.selectCount(null));
        overview.put("activeUsers", userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1)));
        overview.put("totalDiaries", diaryMapper.selectCount(null));
        overview.put("todayNewDiaries", diaryMapper.selectCount(
                new LambdaQueryWrapper<EmotionDiary>().eq(EmotionDiary::getDiaryDate, LocalDate.now())));
        overview.put("totalSessions", sessionMapper.selectCount(null));
        overview.put("todayNewSessions", sessionMapper.selectCount(
                new LambdaQueryWrapper<ConsultationSession>()
                        .ge(ConsultationSession::getStartedAt, LocalDate.now().atStartOfDay())));

        List<EmotionDiary> allDiaries = diaryMapper.selectList(null);
        double avgMood = allDiaries.stream().filter(d -> d.getMoodScore() != null)
                .mapToInt(EmotionDiary::getMoodScore).average().orElse(0.0);
        overview.put("avgMoodScore", Math.round(avgMood * 10.0) / 10.0);
        result.put("systemOverview", overview);

        // Emotion Trend (last 30 days)
        result.put("emotionTrend", getEmotionTrend());

        // Consultation Stats
        result.put("consultationStats", getConsultationStats());

        // User Activity
        result.put("userActivity", getUserActivity());

        return result;
    }

    private List<Map<String, Object>> getEmotionTrend() {
        List<EmotionDiary> diaries = diaryMapper.selectList(
                new LambdaQueryWrapper<EmotionDiary>()
                        .ge(EmotionDiary::getDiaryDate, LocalDate.now().minusDays(30))
                        .orderByAsc(EmotionDiary::getDiaryDate));
        Map<LocalDate, List<EmotionDiary>> grouped = diaries.stream()
                .collect(Collectors.groupingBy(EmotionDiary::getDiaryDate, LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> trend = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            double avg = entry.getValue().stream().filter(d -> d.getMoodScore() != null)
                    .mapToInt(EmotionDiary::getMoodScore).average().orElse(0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", entry.getKey().toString());
            item.put("avgMoodScore", Math.round(avg * 10.0) / 10.0);
            item.put("recordCount", entry.getValue().size());
            trend.add(item);
        }
        return trend;
    }

    private Map<String, Object> getConsultationStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSessions", sessionMapper.selectCount(null));
        stats.put("avgDurationMinutes", 0);

        List<ConsultationSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<ConsultationSession>()
                        .ge(ConsultationSession::getStartedAt, LocalDateTime.now().minusDays(30)));
        Map<LocalDate, List<ConsultationSession>> grouped = sessions.stream()
                .collect(Collectors.groupingBy(s -> s.getStartedAt().toLocalDate(), LinkedHashMap::new, Collectors.toList()));
        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        for (var entry : grouped.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", entry.getKey().toString());
            item.put("sessionCount", entry.getValue().size());
            item.put("userCount", entry.getValue().stream().map(ConsultationSession::getUserId).distinct().count());
            dailyTrend.add(item);
        }
        stats.put("dailyTrend", dailyTrend);
        return stats;
    }

    private List<Map<String, Object>> getUserActivity() {
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(29);
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = today.plusDays(1).atStartOfDay();

        // 一次性查询 30 天数据（原来循环 30 天 × 3 次 = 90 次 SQL，现降为 3 次）
        List<User> recentUsers = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .ge(User::getCreatedAt, startTime)
                        .lt(User::getCreatedAt, endTime));
        Map<LocalDate, Long> newUsersByDay = recentUsers.stream()
                .collect(Collectors.groupingBy(u -> u.getCreatedAt().toLocalDate(), Collectors.counting()));

        List<EmotionDiary> recentDiaries = diaryMapper.selectList(
                new LambdaQueryWrapper<EmotionDiary>()
                        .ge(EmotionDiary::getDiaryDate, startDate)
                        .le(EmotionDiary::getDiaryDate, today));
        Map<LocalDate, Set<Long>> diaryUsersByDay = new HashMap<>();
        for (EmotionDiary d : recentDiaries) {
            diaryUsersByDay.computeIfAbsent(d.getDiaryDate(), k -> new HashSet<>()).add(d.getUserId());
        }

        List<ConsultationSession> recentSessions = sessionMapper.selectList(
                new LambdaQueryWrapper<ConsultationSession>()
                        .ge(ConsultationSession::getStartedAt, startTime)
                        .lt(ConsultationSession::getStartedAt, endTime));
        Map<LocalDate, Set<Long>> sessionUsersByDay = new HashMap<>();
        for (ConsultationSession s : recentSessions) {
            sessionUsersByDay.computeIfAbsent(s.getStartedAt().toLocalDate(), k -> new HashSet<>()).add(s.getUserId());
        }

        // 组装 30 天结果
        List<Map<String, Object>> activity = new ArrayList<>();
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", date.toString());
            item.put("newUsers", newUsersByDay.getOrDefault(date, 0L));

            Set<Long> diaryUsers = diaryUsersByDay.getOrDefault(date, Collections.emptySet());
            Set<Long> sessionUsers = sessionUsersByDay.getOrDefault(date, Collections.emptySet());
            item.put("diaryUsers", diaryUsers.size());
            item.put("consultationUsers", sessionUsers.size());

            Set<Long> activeSet = new HashSet<>(diaryUsers);
            activeSet.addAll(sessionUsers);
            item.put("activeUsers", activeSet.size());

            activity.add(item);
        }
        return activity;
    }
}
