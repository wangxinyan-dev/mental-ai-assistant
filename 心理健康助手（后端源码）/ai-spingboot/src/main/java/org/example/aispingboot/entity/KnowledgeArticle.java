package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_article")
public class KnowledgeArticle {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    private String summary;

    @TableField("category_id")
    private Long categoryId;

    @TableField("cover_image")
    private String coverImage;

    private String tags;

    @TableField("author_name")
    private String authorName;

    @TableField("author_id")
    private Long authorId;

    @TableField("read_count")
    private Integer readCount;

    private Integer status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
