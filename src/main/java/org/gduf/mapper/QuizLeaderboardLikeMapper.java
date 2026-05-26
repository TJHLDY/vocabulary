package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.gduf.entity.QuizLeaderboardLike;

public interface QuizLeaderboardLikeMapper extends BaseMapper<QuizLeaderboardLike> {

    int insertIgnore(@Param("fromUserId") Long fromUserId,
                     @Param("toUserId") Long toUserId);
}
