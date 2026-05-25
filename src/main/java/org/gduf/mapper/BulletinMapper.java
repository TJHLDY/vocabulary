package org.gduf.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.gduf.entity.Bulletin;

public interface BulletinMapper extends BaseMapper<Bulletin> {
    @Select("SELECT * FROM bulletin ORDER BY id DESC LIMIT 1")
    Bulletin selectLatest();
}
