package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    public MailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public void send(String to, String subject, String text) {
        if (to == null || to.isBlank()) {
            log.info("[邮件跳过] 收件人未绑定邮箱：{}", subject);
            return;
        }
        if (!enabled) {
            log.info("[邮件-开发模式] to={}, subject={}, text={}", to, subject, text);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("[邮件未配置] 缺少 JavaMailSender，subject={}", subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            sender.send(message);
            log.info("[邮件已发送] to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.warn("[邮件发送失败] to={}, subject={}, error={}", to, subject, e.getMessage());
        }
    }
}
