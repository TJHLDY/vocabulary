package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.gduf.entity.WordRelation;

import java.util.List;

public interface WordRelationMapper extends BaseMapper<WordRelation> {

    int insertOrIncrement(@Param("userId") Long userId,
                          @Param("sourceWord") String sourceWord,
                          @Param("relatedWord") String relatedWord);

    List<WordRelation> selectTopByWord(@Param("userId") Long userId,
                                       @Param("sourceWord") String sourceWord,
                                       @Param("limit") int limit);
}
