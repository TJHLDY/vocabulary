package org.gduf.controller;

import com.aliyun.oss.OSS;
import jakarta.annotation.Resource;
import org.gduf.common.R;
import org.gduf.security.UserContext;
import org.gduf.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private OSS ossClient;

    @Value("${oss.bucket}")
    private String bucket;

    @Value("${oss.endpoint}")
    private String endpoint;

    @GetMapping("/me")
    public R me() {
        return R.ok(userService.getProfile(UserContext.getUserId()));
    }

    @PostMapping("/avatar")
    public R uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return R.fail("文件为空");
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return R.fail("仅支持图片文件");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            return R.fail("图片大小不能超过5MB");
        }
        try {
            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf("."));
            }
            String key = "avatars/" + UUID.randomUUID().toString() + ext;
            ossClient.putObject(bucket, key, file.getInputStream());
            String url = "https://" + bucket + "." + endpoint + "/" + key;
            String avatarUrl = userService.updateAvatar(UserContext.getUserId(), url);
            return R.ok(Map.of("avatarUrl", avatarUrl));
        } catch (IOException e) {
            return R.fail("上传失败");
        }
    }

    @PutMapping("/password")
    public R changePassword(@RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || oldPassword.isBlank() || newPassword == null || newPassword.isBlank()) {
            return R.fail("旧密码和新密码不能为空");
        }
        try {
            userService.changePassword(UserContext.getUserId(), oldPassword, newPassword);
            return R.ok(Map.of("msg", "密码已修改"));
        } catch (RuntimeException e) {
            return R.fail(e.getMessage());
        }
    }
}
