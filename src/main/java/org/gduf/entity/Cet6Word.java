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
@TableName("cet6_word")
public class Cet6Word implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    private String pos;
    private String meaning;
}
