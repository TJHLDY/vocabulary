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
@TableName("word")
public class Word implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField
    private Long userId;
    @TableField
    private String word;
    @TableField
    private String chinese;
    @TableField
    private Integer count;
    @TableField
    private Integer dictCorrect;
    @TableField
    private Integer dictTotal;
    @TableField("created_at")
    private Date createdAt;
}
