package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.gduf.entity.QuizDailyStats;

import java.util.List;
import java.util.Map;

public interface QuizDailyStatsMapper extends BaseMapper<QuizDailyStats> {

    int upsert(@Param("userId") Long userId,
               @Param("correct") boolean correct);

    List<QuizDailyStats> selectWeekly(@Param("userId") Long userId);

    int deleteAllByUser(@Param("userId") Long userId);

    List<Map<String, Object>> selectLeaderboard(@Param("viewerId") Long viewerId,
                                                 @Param("scope") String scope);
}
