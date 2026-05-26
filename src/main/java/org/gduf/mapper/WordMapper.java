package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.gduf.entity.Word;

import java.util.List;

public interface WordMapper extends BaseMapper<Word> {

    int insertOrIncrement(@Param("userId") Long userId, @Param("word") String word, @Param("chinese") String chinese);

    List<Word> selectByUserId(@Param("userId") Long userId, @Param("sort") String sort);

    List<Word> selectPageByUserId(@Param("userId") Long userId,
                                  @Param("sort") String sort,
                                  @Param("keyword") String keyword,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    int countByUserId(@Param("userId") Long userId, @Param("keyword") String keyword);

    int recordDictation(@Param("userId") Long userId, @Param("word") String word, @Param("correct") boolean correct);

    int deleteByUserAndWord(@Param("userId") Long userId, @Param("word") String word);

    int deleteAllByUser(@Param("userId") Long userId);
}
