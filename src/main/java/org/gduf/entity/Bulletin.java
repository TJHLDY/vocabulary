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
@TableName("bulletin")
public class Bulletin implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String content;
    private Date createdAt;
}
