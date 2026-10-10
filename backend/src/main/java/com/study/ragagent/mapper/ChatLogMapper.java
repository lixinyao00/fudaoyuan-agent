package com.study.ragagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.ragagent.domain.entity.ChatLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatLogMapper extends BaseMapper<ChatLog> {
}