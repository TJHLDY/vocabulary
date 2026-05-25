package org.gduf.controller;

import jakarta.annotation.Resource;
import org.gduf.common.R;
import org.gduf.entity.Bulletin;
import org.gduf.entity.User;
import org.gduf.mapper.BulletinMapper;
import org.gduf.mapper.UserMapper;
import org.gduf.security.UserContext;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bulletin")
public class BulletinController {

    @Resource
    private BulletinMapper bulletinMapper;

    @Resource
    private UserMapper userMapper;

    @GetMapping("/latest")
    public R latest() {
        Bulletin latest = bulletinMapper.selectLatest();
        if (latest == null) {
            return R.ok(Map.of("hasNew", false));
        }
        User user = userMapper.selectById(UserContext.getUserId());
        boolean seen = user.getSeenBulletinId() != null
                && user.getSeenBulletinId().equals(latest.getId());
        return R.ok(Map.of(
            "hasNew", !seen,
            "id", latest.getId(),
            "content", latest.getContent()
        ));
    }

    @PostMapping("/ack")
    public R ack() {
        Bulletin latest = bulletinMapper.selectLatest();
        if (latest == null) {
            return R.ok(Map.of("msg", "ok"));
        }
        User user = userMapper.selectById(UserContext.getUserId());
        user.setSeenBulletinId(latest.getId());
        userMapper.updateById(user);
        return R.ok(Map.of("msg", "ok"));
    }
}
