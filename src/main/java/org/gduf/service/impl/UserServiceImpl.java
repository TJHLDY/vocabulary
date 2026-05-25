package org.gduf.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.gduf.common.JwtUtils;
import org.gduf.common.VerificationCodeStore;
import org.gduf.entity.InviteCode;
import org.gduf.entity.User;
import org.gduf.mapper.*;
import org.gduf.service.EmailService;
import org.gduf.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final InviteCodeMapper inviteCodeMapper;
    private final VerificationCodeStore codeStore;
    private final EmailService emailService;
    private final WordMapper wordMapper;
    private final WordRelationMapper wordRelationMapper;
    private final QuizProgressMapper quizProgressMapper;
    private final QuizDailyStatsMapper quizDailyStatsMapper;
    private final FeedbackMapper feedbackMapper;
    private final SentenceHistoryMapper sentenceHistoryMapper;

    @Value("${verify-code.expire-minutes}")
    private int codeExpireMinutes;

    @Value("${verify-code.resend-seconds}")
    private long resendCooldownSeconds;

    private static final Pattern USERNAME_RE = Pattern.compile("^[a-zA-Z0-9_\\u4e00-\\u9fa5]{2,20}$");

    public UserServiceImpl(JwtUtils jwtUtils, PasswordEncoder passwordEncoder,
                           InviteCodeMapper inviteCodeMapper, VerificationCodeStore codeStore,
                           EmailService emailService, WordMapper wordMapper,
                           WordRelationMapper wordRelationMapper, QuizProgressMapper quizProgressMapper,
                           QuizDailyStatsMapper quizDailyStatsMapper, FeedbackMapper feedbackMapper,
                           SentenceHistoryMapper sentenceHistoryMapper) {
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
        this.inviteCodeMapper = inviteCodeMapper;
        this.codeStore = codeStore;
        this.emailService = emailService;
        this.wordMapper = wordMapper;
        this.wordRelationMapper = wordRelationMapper;
        this.quizProgressMapper = quizProgressMapper;
        this.quizDailyStatsMapper = quizDailyStatsMapper;
        this.feedbackMapper = feedbackMapper;
        this.sentenceHistoryMapper = sentenceHistoryMapper;
    }

    @Override
    public User register(String username, String email, String password, String code, String inviteCode) {
        if (email == null || email.isBlank()) {
            throw new RuntimeException("邮箱不能为空");
        }
        if (!codeStore.verify(email, code)) {
            throw new RuntimeException("验证码错误或已过期");
        }
        if (username == null || username.isBlank() || username.length() < 2 || username.length() > 20) {
            throw new RuntimeException("用户名需2-20位");
        }
        if (!USERNAME_RE.matcher(username).matches()) {
            throw new RuntimeException("用户名只能包含中英文、数字和下划线");
        }

        QueryWrapper<InviteCode> cq = new QueryWrapper<>();
        cq.eq("code", inviteCode).isNull("used_by");
        InviteCode ic = inviteCodeMapper.selectOne(cq);
        if (ic == null) {
            throw new RuntimeException("邀请码无效或已被使用");
        }

        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("username", username).or().eq("email", email);
        if (baseMapper.selectCount(qw) > 0) {
            throw new RuntimeException("用户名或邮箱已注册");
        }

        User user = new User()
                .setUsername(username)
                .setEmail(email)
                .setPassword(passwordEncoder.encode(password))
                .setEnabled(true)
                .setRole("user");
        if (ic.getCreatedBy() != null) {
            user.setInvitedBy(ic.getCreatedBy());
        }
        baseMapper.insert(user);

        ic.setUsedBy(user.getId());
        ic.setUsedAt(new Date());
        inviteCodeMapper.updateById(ic);

        return user;
    }

    @Override
    public String login(String loginId, String password) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("username", loginId).or().eq("email", loginId);
        User user = baseMapper.selectOne(qw);
        if (user == null) {
            throw new RuntimeException("用户名或邮箱不存在");
        }
        if (user.getEnabled() != null && !user.getEnabled()) {
            throw new RuntimeException("账号已被停用");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        return jwtUtils.generate(user.getId(), user.getUsername(),
                user.getRole() != null ? user.getRole() : "user");
    }

    @Override
    public void sendResetCode(String email) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("email", email);
        if (baseMapper.selectCount(qw) == 0) {
            throw new RuntimeException("该邮箱未注册");
        }
        if (!codeStore.canResend(email, resendCooldownSeconds)) {
            throw new RuntimeException("验证码已发送，请60秒后再试");
        }
        String code = String.format("%06d", new Random().nextInt(1000000));
        codeStore.put(email, code, codeExpireMinutes * 60 * 1000L);
        emailService.sendResetCode(email, code);
    }

    @Override
    public void sendRegisterCode(String email) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("email", email);
        if (baseMapper.selectCount(qw) > 0) {
            throw new RuntimeException("该邮箱已被注册");
        }
        if (!codeStore.canResend(email, resendCooldownSeconds)) {
            throw new RuntimeException("验证码已发送，请60秒后再试");
        }
        String code = String.format("%06d", new Random().nextInt(1000000));
        codeStore.put(email, code, codeExpireMinutes * 60 * 1000L);
        emailService.sendRegisterCode(email, code);
    }

    @Override
    public void resetPassword(String email, String code, String newPassword) {
        if (!codeStore.verify(email, code)) {
            throw new RuntimeException("验证码错误或已过期");
        }
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("email", email);
        User user = baseMapper.selectOne(qw);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        baseMapper.updateById(user);
    }

    @Override
    public boolean isAvailable(String field, String value) {
        if (!"username".equals(field) && !"email".equals(field)) {
            throw new RuntimeException("无效的字段名");
        }
        if (value == null || value.isBlank()) {
            return false;
        }
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq(field, value);
        return baseMapper.selectCount(qw) == 0;
    }

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = baseMapper.selectById(userId);
        if (user == null) return;
        if ("admin".equals(user.getRole())) throw new RuntimeException("不能删除管理员");

        log.info("删除用户: id={}, username={}", userId, user.getUsername());

        wordMapper.deleteAllByUser(userId);
        wordRelationMapper.delete(new QueryWrapper<org.gduf.entity.WordRelation>().eq("user_id", userId));
        quizProgressMapper.deleteAllByUser(userId);
        quizDailyStatsMapper.deleteAllByUser(userId);
        feedbackMapper.delete(new QueryWrapper<org.gduf.entity.Feedback>().eq("user_id", userId));
        sentenceHistoryMapper.delete(new QueryWrapper<org.gduf.entity.SentenceHistory>().eq("user_id", userId));

        // 释放邀请码（用 UpdateWrapper.set 避免 null 被忽略）
        inviteCodeMapper.update(null,
            new UpdateWrapper<InviteCode>().eq("used_by", userId).set("used_by", null).set("used_at", null));

        // 删除该用户创建的未使用邀请码
        inviteCodeMapper.delete(
            new QueryWrapper<InviteCode>().eq("created_by", userId).isNull("used_by"));

        baseMapper.deleteById(userId);
    }

    @Override
    @Transactional
    public int deleteDisabledExpired(int days) {
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("enabled", false)
          .ne("role", "admin")
          .isNotNull("updated_at")
          .apply("updated_at < DATE_SUB(NOW(), INTERVAL {0} DAY)", days);
        List<User> expired = baseMapper.selectList(qw);
        for (User u : expired) {
            deleteUser(u.getId());
        }
        return expired.size();
    }

    @Override
    @Transactional
    public void grantTester(Long userId) {
        User user = baseMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");
        if ("admin".equals(user.getRole())) throw new RuntimeException("不能修改管理员角色");
        user.setRole("tester");
        user.setUpdatedAt(new Date());
        baseMapper.updateById(user);
        generateCodesForTester(userId, 10);
    }

    @Override
    public void revokeTester(Long userId) {
        User user = baseMapper.selectById(userId);
        if (user == null) throw new RuntimeException("用户不存在");
        if (!"tester".equals(user.getRole())) throw new RuntimeException("该用户不是内测人");
        user.setRole("user");
        user.setUpdatedAt(new Date());
        baseMapper.updateById(user);
    }

    @Override
    public List<String> generateCodesForTester(Long testerId, int count) {
        User tester = baseMapper.selectById(testerId);
        if (tester == null) throw new RuntimeException("用户不存在");
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
            InviteCode ic = new InviteCode()
                    .setCode(c)
                    .setCreatedBy(testerId);
            inviteCodeMapper.insert(ic);
        }
        return codes;
    }
}
