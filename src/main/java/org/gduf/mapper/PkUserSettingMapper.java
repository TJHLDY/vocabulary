package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.gduf.entity.PkUserSetting;

public interface PkUserSettingMapper extends BaseMapper<PkUserSetting> {

    PkUserSetting selectByUserId(@Param("userId") Long userId);

    int upsertInvisible(@Param("userId") Long userId, @Param("invisible") boolean invisible);

    int upsertDeclarations(@Param("userId") Long userId,
                            @Param("firstWinDeclaration") String firstWinDeclaration,
                            @Param("revengeDeclaration") String revengeDeclaration,
                            @Param("sweepDeclaration") String sweepDeclaration,
                            @Param("finalWinDeclaration") String finalWinDeclaration);
}
