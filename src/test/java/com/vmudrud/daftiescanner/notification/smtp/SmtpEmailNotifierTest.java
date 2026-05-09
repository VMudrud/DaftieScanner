package com.vmudrud.daftiescanner.notification.smtp;

import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.tenant.FilterSpec;
import com.vmudrud.daftiescanner.common.tenant.Tenant;
import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailNotifierTest {

    @Mock
    JavaMailSender mailSender;

    private SmtpEmailNotifier notifier;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        notifier = new SmtpEmailNotifier(mailSender, "noreply@daftie.scanner");
        tenant = new Tenant("1", true, "test@example.com",
                new FilterSpec("residential-to-rent",
                        new FilterSpec.Range(1200, 2300),
                        new FilterSpec.Range(1, 3),
                        List.of("42")),
                List.of("email"));
    }

    @Test
    void channel_returnsEmail() {
        assertThat(notifier.channel()).isEqualTo("email");
    }

    @Test
    void notify_twoListings_sendCalledOnce_subjectContainsCountAndListingDetails() throws Exception {
        var session = Session.getDefaultInstance(new Properties());
        var realMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(realMessage);

        var listings = List.of(
                listing(1001L, "Bright Studio", "/listing/1001"),
                listing(1002L, "Cosy 1-Bed", "/listing/1002")
        );

        notifier.notify(tenant, listings);

        verify(mailSender, times(1)).send(realMessage);
        assertThat(realMessage.getSubject()).contains("2 new rental(s)");

        String htmlBody = findHtmlPart(realMessage);
        assertThat(htmlBody).contains("Bright Studio");
        assertThat(htmlBody).contains("Cosy 1-Bed");
        assertThat(htmlBody).contains("https://www.daft.ie/listing/1001");
        assertThat(htmlBody).contains("https://www.daft.ie/listing/1002");
    }

    @Test
    void notify_emptyList_noEmailSent() {
        notifier.notify(tenant, List.of());

        verifyNoInteractions(mailSender);
    }

    private String findHtmlPart(Object part) throws Exception {
        if (part instanceof MimeMessage msg) {
            return findHtmlPart(msg.getContent());
        }
        if (part instanceof String s) {
            return s;
        }
        if (part instanceof Multipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                BodyPart bp = mp.getBodyPart(i);
                String ct = bp.getContentType();
                if (ct != null && ct.toLowerCase().startsWith("text/html")) {
                    return bp.getContent().toString();
                }
                String nested = findHtmlPart(bp.getContent());
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private ListingResult listing(long id, String title, String path) {
        return new ListingResult(id, title, 1_700_000_000_000L, "€2,000 per month",
                null, null, null, path, null, null, null, null, null, null, null);
    }
}
