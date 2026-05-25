package org.gduf.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("word_relation")
public class WordRelation implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField
    private Long userId;
    @TableField
    private String sourceWord;
    @TableField
    private String relatedWord;
    @TableField
    private Integer count;
}
