package org.gduf.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.gduf.common.R;
import org.gduf.entity.Feedback;
import org.gduf.entity.InviteCode;
import org.gduf.entity.User;
import org.gduf.mapper.FeedbackMapper;
import org.gduf.mapper.InviteCodeMapper;
import org.gduf.security.JwtAuthFilter;
import org.gduf.security.UserContext;
import org.gduf.service.UserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Resource
    private UserService userService;

    @Resource
    private InviteCodeMapper inviteCodeMapper;

    @Resource
    private FeedbackMapper feedbackMapper;

    private void checkAdmin() {
        if (!"admin".equals(UserContext.getRole())) {
            throw new AccessDeniedException("无权限");
        }
    }

    @GetMapping("/users")
    public R users() {
        checkAdmin();
        List<User> list = userService.list(new QueryWrapper<User>()
                .select("id", "username", "email", "enabled", "role", "created_at")
                .orderByDesc("created_at"));
        long now = System.currentTimeMillis();
        long fiveMin = 5 * 60 * 1000;
        for (User u : list) {
            Long last = JwtAuthFilter.LAST_ACTIVE.get(u.getId());
            u.setOnline(last != null && (now - last) < fiveMin);
        }
        return R.ok(list);
    }

    @PutMapping("/users/{id}/toggle")
    public R toggleUser(@PathVariable Long id) {
        checkAdmin();
        User user = userService.getById(id);
        if (user == null) return R.fail("用户不存在");
        if ("admin".equals(user.getRole())) return R.fail("不能停用管理员");
        user.setEnabled(!(user.getEnabled() != null && user.getEnabled()));
        user.setUpdatedAt(new Date());
        userService.updateById(user);
        return R.ok(Map.of("enabled", user.getEnabled()));
    }

    @DeleteMapping("/users/{id}")
    public R deleteUser(@PathVariable Long id) {
        checkAdmin();
        userService.deleteUser(id);
        return R.ok(null);
    }

    @PostMapping("/codes")
    public R generateCodes(@RequestParam(defaultValue = "5") int count) {
        checkAdmin();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rng = new Random();
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 6; j++) {
                sb.append(chars.charAt(rng.nextInt(chars.length())));
            }
            codes.add(sb.toString());
        }
        for (String c : codes) {
            InviteCode ic = new InviteCode().setCode(c);
            inviteCodeMapper.insert(ic);
        }
        return R.ok(codes);
    }

    @GetMapping("/codes")
    public R listCodes() {
        checkAdmin();
        List<InviteCode> list = inviteCodeMapper.selectList(
                new QueryWrapper<InviteCode>().isNull("used_by").orderByDesc("created_at"));
        return R.ok(list);
    }

    @PostMapping("/feedback")
    public R submitFeedback(@RequestBody Map<String, String> body) {
        String message = body.get("message");
        if (message == null || message.isBlank()) return R.fail("内容不能为空");
        if (message.length() > 500) return R.fail("内容不能超过500字");
        Feedback fb = new Feedback()
                .setUserId(UserContext.getUserId())
                .setMessage(message.trim())
                .setCreatedAt(new Date());
        feedbackMapper.insert(fb);
        return R.ok("感谢您的反馈！");
    }

    @GetMapping("/feedback")
    public R listFeedback() {
        checkAdmin();
        List<Feedback> list = feedbackMapper.selectList(
                new QueryWrapper<Feedback>().orderByDesc("created_at"));
        return R.ok(list);
    }
}
