package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.gduf.entity.Feedback;

@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {
}
