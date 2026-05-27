package org.gduf.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Getter
@Setter
@ToString
@Accessors(chain = true)
@TableName("pk_match_question")
public class PkMatchQuestion implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long matchId;
    private Integer questionIndex;
    private Long cetWordId;
    private String mode;
    private String optionsJson;
}
