package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 咨询消息实体类
 * @author system
 */
@Data
@TableName("consultation_message")
@Builder
public class ConsultationMessage {
    // 消息ID
    @TableId(type = IdType.AUTO)
    private Long id;

    // 会话ID
    @NotNull(message = "会话ID不能为空")
    @TableField("session_id")
    private Long sessionId;

    // 发送者类型 1:用户 2:AI助手
    @NotNull(message = "发送者类型不能为空")
    @TableField("sender_type")
    private Integer senderType;

    // 消息类型 1:文本
    @NotNull(message = "消息类型不能为空")
    @TableField("message_type")
    private Integer messageType;

    // 消息内容
    @NotBlank(message = "消息内容不能为空")
    private String content;

    // 情绪标签
    @Size(max = 50, message = "情绪标签长度不能超过50个字符")
    @TableField("emotion_tag")
    private String emotionTag;

    // 使用的AI模型
    @Size(max = 50, message = "AI模型名称长度不能超过50个字符")
    @TableField("ai_model")
    private String aiModel;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 获取发送者类型描述
     */
    public String getSenderTypeDesc() {
        if (senderType == null) {
            return "未知";
        }
        return switch (senderType) {
            case 1 -> "用户";
            case 2 -> "AI助手";
            default -> "未知";
        };
    }

    /**
     * 获取消息类型描述
     */
    public String getMessageTypeDesc() {
        if (messageType == null) {
            return "未知";
        }
        return switch (messageType) {
            case 1 -> "文本";
            default -> "未知";
        };
    }
}