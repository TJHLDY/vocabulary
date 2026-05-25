package org.gduf.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.gduf.entity.User;

public interface UserService extends IService<User> {
    User register(String username, String email, String password, String code, String inviteCode);
    String login(String loginId, String password);
    void sendRegisterCode(String email);
    void sendResetCode(String email);
    void resetPassword(String email, String code, String newPassword);
    boolean isAvailable(String field, String value);
    void deleteUser(Long userId);
    int deleteDisabledExpired(int days);
}
