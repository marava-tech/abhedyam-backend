package com.abhedyam.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class GmailSender {
    
    private final JavaMailSender mailSender;
    
    @Value("${app.email.from:marava.technologies@gmail.com}")
    private String fromEmail;
    
    public void sendGmail(String to, String subject, String content) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, true);
        mailSender.send(message);
        log.info("Gmail sent successfully to {}", to);
    }

    public void sendGmailWithAttachment(String to, String subject, String content, String filename, byte[] data) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(fromEmail);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, true);
        helper.addAttachment(filename, new org.springframework.core.io.ByteArrayResource(data), "application/zip");
        mailSender.send(message);
        log.info("Gmail with attachment sent to {}", to);
    }
}

