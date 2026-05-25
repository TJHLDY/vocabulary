package org.gduf.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("quiz_daily_stats")
public class QuizDailyStats implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Date quizDate;
    private Integer totalQuestions;
    private Integer correctAnswers;
}
