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
@TableName("pk_match_answer")
public class PkMatchAnswer implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long matchId;
    private Integer questionIndex;
    private Long cetWordId;
    private Long buzzUserId;
    private Long answerUserId;
    private String chosen;
    private Boolean correct;
    private Integer scoreDelta;
    private Date answeredAt;
}
