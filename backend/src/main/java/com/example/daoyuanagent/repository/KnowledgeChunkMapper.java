package com.example.daoyuanagent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.daoyuanagent.domain.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    @Select("SELECT * FROM knowledge_chunk WHERE review_status = 'APPROVED' " +
            "AND MATCH(content) AGAINST(#{query} IN BOOLEAN MODE) LIMIT #{limit}")
    List<KnowledgeChunk> fullTextSearch(@Param("query") String query, @Param("limit") int limit);

    @Select("SELECT * FROM knowledge_chunk WHERE review_status = 'APPROVED' " +
            "AND categories LIKE CONCAT('%', #{category}, '%') LIMIT #{limit}")
    List<KnowledgeChunk> findByCategory(@Param("category") String category, @Param("limit") int limit);
}
