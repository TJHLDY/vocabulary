package org.gduf.controller;

import jakarta.annotation.Resource;
import org.gduf.common.R;
import org.gduf.entity.User;
import org.gduf.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Resource
    private UserService userService;

    @PostMapping("/register")
    public R register(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        String username = body.get("username");
        String password = body.get("password");
        String inviteCode = body.get("inviteCode");
        if (email == null || email.isBlank()) {
            return R.fail("邮箱不能为空");
        }
        if (code == null || code.isBlank()) {
            return R.fail("验证码不能为空");
        }
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return R.fail("用户名和密码不能为空");
        }
        if (password.length() < 6) {
            return R.fail("密码至少6位");
        }
        if (inviteCode == null || inviteCode.isBlank()) {
            return R.fail("邀请码不能为空");
        }
        try {
            User user = userService.register(username, email, password, code, inviteCode);
            return R.ok(Map.of("id", user.getId(), "username", user.getUsername(), "email", user.getEmail()));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/register/send-code")
    public R sendRegisterCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return R.fail("邮箱不能为空");
        }
        try {
            userService.sendRegisterCode(email);
            return R.ok(Map.of("msg", "验证码已发送至邮箱"));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    @GetMapping("/check")
    public R check(@RequestParam String field, @RequestParam String value) {
        if (field == null || field.isBlank() || value == null || value.isBlank()) {
            return R.fail("参数不完整");
        }
        if (!"username".equals(field) && !"email".equals(field)) {
            return R.fail("无效的字段名");
        }
        try {
            boolean available = userService.isAvailable(field, value);
            return R.ok(Map.of("available", available, "field", field));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/login")
    public R login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        boolean remember = "true".equals(body.get("remember"));
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return R.fail("用户名和密码不能为空");
        }
        try {
            String token = userService.login(username, password, remember);
            return R.ok(Map.of("token", token));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/forgot-password/send-code")
    public R sendResetCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.isBlank()) {
            return R.fail("邮箱不能为空");
        }
        try {
            userService.sendResetCode(email);
            return R.ok(Map.of("msg", "验证码已发送至邮箱"));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }

    @PostMapping("/forgot-password/reset")
    public R resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        String newPassword = body.get("newPassword");
        if (email == null || email.isBlank() || code == null || code.isBlank()
                || newPassword == null || newPassword.isBlank()) {
            return R.fail("邮箱、验证码和新密码不能为空");
        }
        if (newPassword.length() < 6) {
            return R.fail("新密码至少6位");
        }
        try {
            userService.resetPassword(email, code, newPassword);
            return R.ok(Map.of("msg", "密码已重置，请使用新密码登录"));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }
}
