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
@TableName("pk_user_setting")
public class PkUserSetting implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Boolean invisible;
    private String firstWinDeclaration;
    private String revengeDeclaration;
    private String sweepDeclaration;
    private String finalWinDeclaration;
    private Date updatedAt;
}
