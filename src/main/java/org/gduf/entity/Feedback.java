package org.gduf.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@Accessors(chain = true)
@TableName("feedback")
public class Feedback implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String message;
    @TableField("created_at")
    private Date createdAt;
}
