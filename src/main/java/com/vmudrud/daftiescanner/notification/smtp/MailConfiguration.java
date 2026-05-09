package com.vmudrud.daftiescanner.notification.smtp;

import org.springframework.context.annotation.Configuration;

/**
 * Ensures the smtp package is component-scanned.
 * JavaMailSender is auto-configured by Spring Boot from spring.mail.* properties.
 * SmtpEmailNotifier is always registered; it is invoked only when a tenant declares the "email" channel.
 */
@Configuration
public class MailConfiguration {
}
