package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.gduf.entity.QuizProgress;

import java.util.List;

public interface QuizProgressMapper extends BaseMapper<QuizProgress> {

    int upsert(@Param("userId") Long userId,
               @Param("cetWordId") Long cetWordId,
               @Param("consecutiveCorrect") int consecutiveCorrect,
               @Param("totalWrong") int totalWrong,
               @Param("lastQuizAt") java.util.Date lastQuizAt);

    List<QuizProgress> selectByUser(@Param("userId") Long userId,
                                    @Param("mastered") Boolean mastered);

    List<QuizProgress> selectWrongWords(@Param("userId") Long userId,
                                        @Param("limit") int limit);

    QuizProgress selectByUserAndWord(@Param("userId") Long userId,
                                     @Param("cetWordId") Long cetWordId);

    int deleteAllByUser(@Param("userId") Long userId);

    boolean allMastered(@Param("userId") Long userId);
}
