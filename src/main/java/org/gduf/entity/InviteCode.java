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
@TableName("invite_code")
public class InviteCode implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField
    private String code;
    @TableField("used_by")
    private Long usedBy;
    @TableField("used_at")
    private Date usedAt;
    @TableField("created_at")
    private Date createdAt;
}
