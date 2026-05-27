package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.gduf.entity.PkMatchQuestion;

import java.util.List;
import java.util.Map;

public interface PkMatchQuestionMapper extends BaseMapper<PkMatchQuestion> {

    List<Map<String, Object>> selectReplayQuestions(@Param("matchId") Long matchId);
}
