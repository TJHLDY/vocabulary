package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.gduf.entity.Cet6Word;

import java.util.List;

public interface Cet6WordMapper extends BaseMapper<Cet6Word> {

    List<Cet6Word> selectRandomExcluding(@Param("excludeIds") List<Long> excludeIds,
                                         @Param("limit") int limit);

    List<Cet6Word> selectRandomByPos(@Param("pos") String pos,
                                     @Param("excludeWord") String excludeWord,
                                     @Param("limit") int limit);

    long countAll();
}
