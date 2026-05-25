package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.gduf.entity.QuizDailyStats;

import java.util.List;

public interface QuizDailyStatsMapper extends BaseMapper<QuizDailyStats> {

    int upsert(@Param("userId") Long userId,
               @Param("correct") boolean correct);

    List<QuizDailyStats> selectWeekly(@Param("userId") Long userId);

    int deleteAllByUser(@Param("userId") Long userId);
}
