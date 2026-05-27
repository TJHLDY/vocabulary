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
@TableName("pk_pair_daily_reward")
public class PkPairDailyReward implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userLowId;
    private Long userHighId;
    private Date rewardDate;
    private Integer rewardedMatches;
}
