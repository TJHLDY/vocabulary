package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

public interface PkPairDailyRewardMapper extends BaseMapper<org.gduf.entity.PkPairDailyReward> {

    int selectTodayCount(@Param("userLowId") long userLowId, @Param("userHighId") long userHighId);

    int incrementToday(@Param("userLowId") long userLowId, @Param("userHighId") long userHighId);
}
