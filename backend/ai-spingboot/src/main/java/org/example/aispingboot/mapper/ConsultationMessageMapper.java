package org.example.aispingboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.example.aispingboot.entity.ConsultationMessage;

import java.util.List;
import java.util.Map;

@Mapper
public interface ConsultationMessageMapper extends BaseMapper<ConsultationMessage> {

    /**
     * 批量查询多个会话的消息数量
     * 返回 List<Map>，每个 Map 包含 session_id 和 cnt
     */
    @Select("<script>" +
            "SELECT session_id, COUNT(*) AS cnt FROM consultation_message " +
            "WHERE session_id IN " +
            "<foreach collection='sessionIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY session_id" +
            "</script>")
    List<Map<String, Object>> batchCountBySessionIds(@Param("sessionIds") List<Long> sessionIds);

    /**
     * 批量查询每个会话的最后一条消息（MySQL 8+ 窗口函数）
     */
    @Select("<script>" +
            "SELECT * FROM (" +
            "  SELECT *, ROW_NUMBER() OVER(PARTITION BY session_id ORDER BY created_at DESC) AS rn" +
            "  FROM consultation_message" +
            "  WHERE session_id IN " +
            "  <foreach collection='sessionIds' item='id' open='(' separator=',' close=')'>" +
            "  #{id}" +
            "  </foreach>" +
            ") t WHERE rn = 1" +
            "</script>")
    List<ConsultationMessage> batchLastMessage(@Param("sessionIds") List<Long> sessionIds);
}
