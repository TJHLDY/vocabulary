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
@TableName("sentence_history")
public class SentenceHistory implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String text;
    @TableField("created_at")
    private Date createdAt;
}
