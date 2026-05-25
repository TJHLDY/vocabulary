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
@TableName("user")
public class User implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField
    private String username;
    @TableField
    private String email;
    @TableField
    private String password;
    @TableField("created_at")
    private Date createdAt;
    @TableField("updated_at")
    private Date updatedAt;
    @TableField
    private Boolean enabled;
    @TableField
    private String role;
    @TableField(exist = false)
    private Boolean online;
    @TableField("seen_bulletin_id")
    private Long seenBulletinId;
}
