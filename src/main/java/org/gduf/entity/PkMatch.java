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
@TableName("pk_match")
public class PkMatch implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long playerAId;
    private Long playerBId;
    private Long winnerUserId;
    private String status;
    private Integer questionsTotal;
    private Integer questionsCompleted;
    private Integer playerAScore;
    private Integer playerBScore;
    private Integer playerAPointsDelta;
    private Integer playerBPointsDelta;
    private Date startedAt;
    private Date finishedAt;
}
