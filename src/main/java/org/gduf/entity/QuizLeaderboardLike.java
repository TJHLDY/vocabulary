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
@TableName("quiz_leaderboard_like")
public class QuizLeaderboardLike implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("from_user_id")
    private Long fromUserId;
    @TableField("to_user_id")
    private Long toUserId;
    private Date likeDate;
    @TableField("created_at")
    private Date createdAt;
}
