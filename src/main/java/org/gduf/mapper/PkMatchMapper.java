package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.gduf.entity.PkMatch;

import java.util.List;
import java.util.Map;

public interface PkMatchMapper extends BaseMapper<PkMatch> {

    int updateSettled(@Param("id") Long id,
                      @Param("status") String status,
                      @Param("winnerUserId") Long winnerUserId,
                      @Param("questionsCompleted") int questionsCompleted,
                      @Param("playerAScore") int playerAScore,
                      @Param("playerBScore") int playerBScore,
                      @Param("playerAPointsDelta") int playerAPointsDelta,
                      @Param("playerBPointsDelta") int playerBPointsDelta);

    List<Map<String, Object>> selectRecentMatches(@Param("userId") Long userId,
                                                   @Param("limit") int limit);

    Long selectPreviousWinnerBetween(@Param("userAId") Long userAId,
                                     @Param("userBId") Long userBId,
                                     @Param("excludeMatchId") Long excludeMatchId);
}
