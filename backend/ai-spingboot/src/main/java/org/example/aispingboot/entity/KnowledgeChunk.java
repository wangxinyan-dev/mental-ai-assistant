package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档分块实体
 * 存储文章分块后的片段（向量存于 PgVector 的 rag_embedding 表）
 */
@Data
@TableName("knowledge_chunk")
public class KnowledgeChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("article_id")
    private Long articleId;

    @TableField("chunk_index")
    private Integer chunkIndex;

    private String title;

    private String content;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
