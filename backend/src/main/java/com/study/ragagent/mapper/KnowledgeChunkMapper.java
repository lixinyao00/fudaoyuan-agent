package com.study.ragagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.ragagent.domain.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    @Select("""
            <script>
            SELECT * FROM knowledge_chunk
            WHERE
            <foreach collection='keywords' item='kw' separator=' AND '>
              content LIKE CONCAT('%', #{kw}, '%')
            </foreach>
            LIMIT #{limit}
            </script>
            """)
    List<KnowledgeChunk> searchByAllKeywords(@Param("keywords") List<String> keywords, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM knowledge_chunk
            WHERE
            <foreach collection='keywords' item='kw' separator=' OR '>
              content LIKE CONCAT('%', #{kw}, '%')
            </foreach>
            LIMIT #{limit}
            </script>
            """)
    List<KnowledgeChunk> searchByAnyKeyword(@Param("keywords") List<String> keywords, @Param("limit") int limit);
}
