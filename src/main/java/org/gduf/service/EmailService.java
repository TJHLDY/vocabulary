package org.gduf.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    private void send(String to, String code, String subject) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText("您的验证码是：" + code + "，有效期5分钟，请勿泄露给他人。");
        mailSender.send(msg);
    }

    public void sendRegisterCode(String to, String code) {
        send(to, code, "VocabNote - 注册验证码");
    }

    public void sendResetCode(String to, String code) {
        send(to, code, "VocabNote - 密码重置验证码");
    }
}
