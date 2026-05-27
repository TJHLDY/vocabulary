package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.gduf.entity.PkUserStats;

import java.util.List;
import java.util.Map;

public interface PkUserStatsMapper extends BaseMapper<PkUserStats> {

    int upsert(@Param("userId") Long userId,
               @Param("pointsDelta") int pointsDelta,
               @Param("isWin") boolean isWin,
               @Param("isLoss") boolean isLoss,
               @Param("isDraw") boolean isDraw,
               @Param("highestStreak") int highestStreak);

    List<Map<String, Object>> selectLeaderboard(@Param("scope") String scope);

    List<Map<String, Object>> selectArenaUsers(@Param("userIds") List<Long> userIds);
}
