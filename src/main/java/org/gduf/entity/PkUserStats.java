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
@TableName("pk_user_stats")
public class PkUserStats implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer points;
    private Integer wins;
    private Integer losses;
    private Integer draws;
    private Integer matches;
    private Integer highestStreak;
    private Date updatedAt;
}
