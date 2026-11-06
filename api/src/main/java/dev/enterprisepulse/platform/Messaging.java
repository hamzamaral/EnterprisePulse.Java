package dev.enterprisepulse.platform;

import dev.enterprisepulse.common.AppProperties;
import jakarta.mail.internet.MimeMessage;
import java.util.Collection;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

public final class Messaging {
    private Messaging() {}

    /** Real-time delivery to connected clients (STOMP in the API host). */
    public interface Pusher {
        void push(Collection<UUID> userIds, String event, Object payload);
    }

    @Component
    public static class MailService {
        private static final Logger log = LoggerFactory.getLogger(MailService.class);
        private final JavaMailSender sender;
        private final AppProperties props;

        public MailService(JavaMailSender sender, AppProperties props) {
            this.sender = sender;
            this.props = props;
        }

        public void send(String to, String subject, String html) {
            try {
                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
                helper.setFrom(props.mailFrom);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(layout(subject, html), true);
                sender.send(message);
                log.info("Mail sent to {}: {}", to, subject);
            } catch (Exception e) {
                log.warn("Mail to {} failed: {}", to, e.getMessage());
            }
        }

        private static String layout(String title, String body) {
            return """
                    <div style="font-family:Segoe UI,Arial,sans-serif;max-width:560px;margin:auto">
                      <h2 style="color:#4338ca">EnterprisePulse</h2>
                      <h3>%s</h3>
                      <div>%s</div>
                    </div>""".formatted(HtmlUtils.htmlEscape(title), body);
        }
    }

    /** Sends mail off the request thread. */
    @Component
    public static class MailQueue {
        private final MailService mail;
        private final TaskExecutor executor;

        public MailQueue(MailService mail, TaskExecutor executor) {
            this.mail = mail;
            this.executor = executor;
        }

        public void send(String to, String subject, String html) {
            executor.execute(() -> mail.send(to, subject, html));
        }
    }
}
