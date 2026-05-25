package org.gduf.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.gduf.entity.User;

import java.util.List;
import java.util.Map;

public interface UserService extends IService<User> {
    User register(String username, String email, String password, String code, String inviteCode);
    String login(String loginId, String password, boolean remember);
    void sendRegisterCode(String email);
    void sendResetCode(String email);
    void resetPassword(String email, String code, String newPassword);
    boolean isAvailable(String field, String value);
    void deleteUser(Long userId);
    int deleteDisabledExpired(int days);
    void grantTester(Long userId);
    void revokeTester(Long userId);
    List<String> generateCodesForTester(Long testerId, int count);
    void changePassword(Long userId, String oldPassword, String newPassword);
    Map<String, Object> getProfile(Long userId);
    String updateAvatar(Long userId, String avatarUrl);
}
