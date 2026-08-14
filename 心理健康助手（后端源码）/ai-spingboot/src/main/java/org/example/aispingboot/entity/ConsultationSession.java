package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("consultation_session")
@Builder
public class ConsultationSession {
    // 会话ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID
    @TableField("user_id")
    private Long userId;

    // 会话标题
    @Size(max = 200, message = "会话标题长度不能超过200个字符")
    @TableField("session_title")
    private String sessionTitle;

    // 开始时间
    @TableField("started_at")
    private LocalDateTime startedAt;

    // 最后一次情绪分析结果(JSON格式)
    @TableField("last_emotion_analysis")
    private String lastEmotionAnalysis;

    // 最后一次情绪分析更新时间
    @TableField("last_emotion_updated_at")
    private LocalDateTime lastEmotionUpdatedAt;
}