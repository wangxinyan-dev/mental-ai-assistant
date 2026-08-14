package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.aispingboot.entity.EmotionDiary;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.mapper.EmotionDiaryMapper;
import org.example.aispingboot.mapper.UserMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmotionDiaryService {

    @Resource
    private EmotionDiaryMapper diaryMapper;

    @Resource
    private UserMapper userMapper;

    public void saveDiary(Long userId, Map<String, Object> dto) {
        LocalDate diaryDate = LocalDate.now();
        if (dto.containsKey("diaryDate") && dto.get("diaryDate") != null) {
            try {
                diaryDate = LocalDate.parse(dto.get("diaryDate").toString());
            } catch (Exception ignored) {}
        }

        // 每次提交都是新记录，允许一天多条
        EmotionDiary diary = new EmotionDiary();
        diary.setUserId(userId);
        diary.setDiaryDate(diaryDate);
        diary.setCreatedAt(LocalDateTime.now());

        if (dto.containsKey("moodScore")) diary.setMoodScore(toInt(dto.get("moodScore")));
        if (dto.containsKey("dominantEmotion")) diary.setDominantEmotion((String) dto.get("dominantEmotion"));
        if (dto.containsKey("emotionTriggers")) diary.setEmotionTriggers((String) dto.get("emotionTriggers"));
        if (dto.containsKey("diaryContent")) diary.setDiaryContent((String) dto.get("diaryContent"));
        if (dto.containsKey("sleepQuality")) diary.setSleepQuality(toInt(dto.get("sleepQuality")));
        if (dto.containsKey("stressLevel")) diary.setStressLevel(toInt(dto.get("stressLevel")));
        diary.setUpdatedAt(LocalDateTime.now());

        diaryMapper.insert(diary);
    }

    public Map<String, Object> adminPage(Integer current, Integer size, Long userId, String moodScoreRange) {
        Page<EmotionDiary> page = new Page<>(current, size);
        LambdaQueryWrapper<EmotionDiary> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(EmotionDiary::getCreatedAt);

        if (userId != null) {
            qw.eq(EmotionDiary::getUserId, userId);
        }
        if (moodScoreRange != null && moodScoreRange.contains("-")) {
            String[] parts = moodScoreRange.split("-");
            qw.between(EmotionDiary::getMoodScore, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }

        Page<EmotionDiary> result = diaryMapper.selectPage(page, qw);

        // Join user info
        List<Long> userIds = result.getRecords().stream()
                .map(EmotionDiary::getUserId).distinct().collect(Collectors.toList());
        List<User> users = userIds.isEmpty() ? List.of() : userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Map<String, Object>> enriched = result.getRecords().stream().map(d -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", d.getId());
            map.put("userId", d.getUserId());
            User u = userMap.get(d.getUserId());
            map.put("username", u != null ? u.getUsername() : "");
            map.put("nickname", u != null ? (u.getNickname() != null ? u.getNickname() : u.getUsername()) : "");
            map.put("diaryDate", d.getDiaryDate() != null ? d.getDiaryDate().toString() : "");
            map.put("moodScore", d.getMoodScore());
            map.put("dominantEmotion", d.getDominantEmotion());
            map.put("sleepQuality", d.getSleepQuality());
            map.put("stressLevel", d.getStressLevel());
            map.put("emotionTriggers", d.getEmotionTriggers());
            map.put("diaryContent", d.getDiaryContent());
            map.put("aiEmotionAnalysis", d.getAiEmotionAnalysis());
            map.put("createdAt", d.getCreatedAt() != null ? d.getCreatedAt().toString() : "");
            map.put("updatedAt", d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : "");
            return map;
        }).collect(Collectors.toList());

        return Map.of("records", enriched, "total", result.getTotal());
    }

    public Map<String, Object> userPage(Integer current, Integer size, Long userId) {
        Page<EmotionDiary> page = new Page<>(current, size);
        LambdaQueryWrapper<EmotionDiary> qw = new LambdaQueryWrapper<>();
        qw.eq(EmotionDiary::getUserId, userId)
          .orderByDesc(EmotionDiary::getCreatedAt);

        Page<EmotionDiary> result = diaryMapper.selectPage(page, qw);

        List<Map<String, Object>> records = result.getRecords().stream().map(d -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", d.getId());
            map.put("diaryDate", d.getDiaryDate() != null ? d.getDiaryDate().toString() : "");
            map.put("moodScore", d.getMoodScore());
            map.put("dominantEmotion", d.getDominantEmotion());
            map.put("sleepQuality", d.getSleepQuality());
            map.put("stressLevel", d.getStressLevel());
            map.put("emotionTriggers", d.getEmotionTriggers());
            map.put("diaryContent", d.getDiaryContent());
            map.put("aiEmotionAnalysis", d.getAiEmotionAnalysis());
            map.put("createdAt", d.getCreatedAt() != null ? d.getCreatedAt().toString() : "");
            map.put("updatedAt", d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : "");
            return map;
        }).collect(Collectors.toList());

        return Map.of("records", records, "total", result.getTotal());
    }

    public void deleteDiary(Long id) {
        diaryMapper.deleteById(id);
    }

    /**
     * 情绪趋势分析：检测最近一段时间情绪评分的走势，连续多日下降时触发关怀提示。
     * 实现从"被动记录"到"主动关怀"的服务升级。
     */
    public Map<String, Object> analyzeTrend(Long userId) {
        // 取最近 14 天的记录，按日期升序
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(13);

        LambdaQueryWrapper<EmotionDiary> qw = new LambdaQueryWrapper<>();
        qw.eq(EmotionDiary::getUserId, userId)
          .ge(EmotionDiary::getDiaryDate, startDate)
          .le(EmotionDiary::getDiaryDate, today)
          .isNotNull(EmotionDiary::getMoodScore)
          .orderByAsc(EmotionDiary::getDiaryDate);
        List<EmotionDiary> records = diaryMapper.selectList(qw);

        // 按日期分组并计算每天平均评分
        Map<LocalDate, Double> dailyAvgMap = new TreeMap<>();
        for (EmotionDiary d : records) {
            if (d.getDiaryDate() == null || d.getMoodScore() == null) continue;
            dailyAvgMap.merge(d.getDiaryDate(), d.getMoodScore().doubleValue(),
                    (old, v) -> (old + v) / 2);
        }

        Map<String, Object> result = new LinkedHashMap<>();

        // 数据不足：少于 2 天的评分数据无法判断趋势
        if (dailyAvgMap.size() < 2) {
            result.put("trendType", "insufficient");
            result.put("level", "info");
            result.put("careMessage", "继续记录您的情绪，连续记录几天后即可看到情绪趋势分析哦~");
            result.put("recentScores", List.of());
            result.put("averageScore", null);
            result.put("consecutiveDeclineDays", 0);
            return result;
        }

        // 转为有序列表
        List<Map<String, Object>> recentScores = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        for (Map.Entry<LocalDate, Double> e : dailyAvgMap.entrySet()) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", e.getKey().toString());
            point.put("score", Math.round(e.getValue() * 10) / 10.0);
            recentScores.add(point);
            scores.add(e.getValue());
        }

        // 最近 14 天平均分
        double averageScore = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        // 检测从末尾开始的连续下降天数
        int declineDays = 0;
        for (int i = scores.size() - 1; i > 0; i--) {
            if (scores.get(i) < scores.get(i - 1)) {
                declineDays++;
            } else {
                break;
            }
        }

        // 最近一天的评分
        double latestScore = scores.get(scores.size() - 1);

        // 综合判断趋势与关怀等级
        String trendType;
        String level;
        String careMessage;
        int consecutiveDeclineDays = declineDays;

        if (declineDays >= 3) {
            // 连续 3 天及以上评分下降 → 强关怀
            trendType = "declining";
            level = "warning";
            careMessage = buildDeclineCareMessage(declineDays, latestScore);
        } else if (latestScore <= 3) {
            // 评分过低 → 强关怀（无论趋势）
            trendType = "low";
            level = "warning";
            careMessage = "您最近的情绪评分较低，看起来这段时间过得不太容易。" +
                    "请记得，低落是正常的，您不必独自承受。可以试着和信任的人聊聊，" +
                    "或拨打心理援助热线 400-161-9995。如果愿意，也可以和 AI 助手说说心里话。";
        } else if (declineDays >= 1 && latestScore < averageScore) {
            // 轻微下降且低于平均 → 提醒
            trendType = "slight_decline";
            level = "info";
            careMessage = "今天的情绪评分相比前几天有所下降。不妨留意一下最近是否有特别消耗精力的事情，" +
                    "给自己一些喘息的时间，做点喜欢的事放松一下~";
        } else if (latestScore > averageScore) {
            // 评分高于平均 → 鼓励
            trendType = "improving";
            level = "success";
            careMessage = "您最近的情绪状态不错，比之前有所好转，继续保持呀！记得给自己一些小小的肯定~";
        } else {
            // 平稳
            trendType = "stable";
            level = "info";
            careMessage = "您的情绪状态整体平稳。坚持记录是个很棒的习惯，有助于更好地了解自己~";
        }

        result.put("trendType", trendType);
        result.put("level", level);
        result.put("careMessage", careMessage);
        result.put("recentScores", recentScores);
        result.put("averageScore", Math.round(averageScore * 10) / 10.0);
        result.put("consecutiveDeclineDays", consecutiveDeclineDays);
        result.put("latestScore", Math.round(latestScore * 10) / 10.0);
        return result;
    }

    private String buildDeclineCareMessage(int declineDays, double latestScore) {
        StringBuilder sb = new StringBuilder();
        sb.append("检测到您连续 ").append(declineDays).append(" 天的情绪评分持续下降");
        if (latestScore <= 3) {
            sb.append("，且当前评分较低，我们很关心您的状态。");
        } else {
            sb.append("，我们注意到您最近可能承受了一些压力。");
        }
        sb.append("情绪的起伏是人生的一部分，当感到疲惫或低落时，请允许自己休息一下。")
          .append("您可以尝试与亲友倾诉、做些放松的活动，或者和 AI 助手聊一聊。")
          .append("若感到难以独自应对，全国 24 小时心理援助热线 400-161-9995 始终为您守候。")
          .append("您不是一个人，我们都在这里陪着您。");
        return sb.toString();
    }

    private Integer toInt(Object val) {
        if (val == null) return null;
        if (val instanceof Integer) return (Integer) val;
        try { return Integer.valueOf(val.toString()); } catch (Exception e) { return null; }
    }
}
