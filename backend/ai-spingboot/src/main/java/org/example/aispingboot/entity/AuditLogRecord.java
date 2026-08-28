package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计记录（audit_log 表）。
 *
 * 谁（user_id/username）在什么时候（created_at）对什么（module/action/target_id）
 * 做了什么（detail 入参快照）、结果如何（result/error_msg）、花了多久（cost_ms）。
 */
@Data
@TableName("audit_log")
public class AuditLogRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作人ID；登录取不到（匿名/无上下文）为 NULL */
    private Long userId;

    /** 操作人用户名（冗余存储，方便直接看表） */
    private String username;

    private String module;

    private String action;

    /** 操作对象ID（第一个 Long 类型入参），如文章ID/用户ID */
    private String targetId;

    /** 入参快照（JSON，截断到 500 字符） */
    private String detail;

    /** 0=成功 1=失败 */
    private Integer result;

    /** 失败原因（异常 message） */
    private String errorMsg;

    /** 客户端 IP */
    private String ip;

    /** 方法执行耗时（毫秒） */
    private Long costMs;

    private LocalDateTime createdAt;
}