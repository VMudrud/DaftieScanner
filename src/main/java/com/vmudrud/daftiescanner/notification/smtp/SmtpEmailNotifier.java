package com.vmudrud.daftiescanner.notification.smtp;

import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import com.vmudrud.daftiescanner.notification.router.Notifier;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@ConditionalOnBean(JavaMailSender.class)
public class SmtpEmailNotifier implements Notifier {

    public static final String CHANNEL = "email";
    private static final String DAFT_BASE = "https://www.daft.ie";

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailNotifier(JavaMailSender mailSender,
                             @Value("${daft.mail.from:noreply@daftie.scanner}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public void notify(Tenant tenant, List<ListingResult> listings) {
        if (listings.isEmpty()) {
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(tenant.email());
            helper.setSubject("[DaftieScanner] %d new rental(s) for %s".formatted(listings.size(), tenant.id()));
            helper.setText(buildHtmlBody(listings), true);
            mailSender.send(message);
            log.info("Email sent tenant={} count={}", tenant.id(), listings.size());
        } catch (Exception e) {
            log.error("Failed to send email tenant={}: {}", tenant.id(), e.getMessage(), e);
        }
    }

    private String buildHtmlBody(List<ListingResult> listings) {
        var rows = new StringBuilder();
        listings.forEach(l -> rows.append("""
                <tr>
                  <td><a href="%s%s">%s</a></td>
                  <td>%s</td>
                  <td>%s</td>
                </tr>
                """.formatted(DAFT_BASE, l.seoFriendlyPath(), l.title(), l.price(), l.publishDate())));

        return """
                <!DOCTYPE html>
                <html>
                <body>
                <h2>New rental listings found</h2>
                <table border="1" cellpadding="6" cellspacing="0">
                  <thead>
                    <tr><th>Listing</th><th>Price</th><th>Published</th></tr>
                  </thead>
                  <tbody>
                %s  </tbody>
                </table>
                </body>
                </html>
                """.formatted(rows);
    }
}
