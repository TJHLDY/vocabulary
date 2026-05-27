package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.gduf.entity.PkDailyStats;

public interface PkDailyStatsMapper extends BaseMapper<PkDailyStats> {

    int upsert(@Param("userId") Long userId,
               @Param("netPoints") int netPoints,
               @Param("basePoints") int basePoints,
               @Param("streakPoints") int streakPoints,
               @Param("isWin") boolean isWin,
               @Param("isLoss") boolean isLoss,
               @Param("isDraw") boolean isDraw,
               @Param("highestStreak") int highestStreak);

    PkDailyStats selectTodayByUserId(@Param("userId") Long userId);
}
