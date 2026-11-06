package dev.enterprisepulse.platform;

import dev.enterprisepulse.common.AppProperties;
import dev.enterprisepulse.data.Db;
import dev.enterprisepulse.data.generated.Notification;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

@Service
@Transactional
public class NotificationService {
    public record NotifyRequest(Collection<UUID> userIds, UUID companyId, String type, String title, String body, String link, boolean email) {
        public NotifyRequest(Collection<UUID> userIds, UUID companyId, String type, String title, String body, String link) {
            this(userIds, companyId, type, title, body, link, false);
        }
    }

    private final Db db;
    private final Messaging.Pusher pusher;
    private final Messaging.MailQueue mail;
    private final AppProperties props;

    public NotificationService(Db db, Messaging.Pusher pusher, Messaging.MailQueue mail, AppProperties props) {
        this.db = db;
        this.pusher = pusher;
        this.mail = mail;
        this.props = props;
    }

    public void notify(NotifyRequest request) {
        List<UUID> ids = request.userIds().stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return;
        List<Notification> rows = new ArrayList<>();
        for (UUID id : ids) {
            Notification n = new Notification();
            n.companyId = request.companyId();
            n.userId = id;
            n.type = request.type();
            n.title = request.title();
            n.body = request.body();
            n.link = request.link();
            rows.add(db.add(n));
        }
        db.flush();
        afterCommit(() -> rows.forEach(n -> pusher.push(List.of(n.userId), "notification", n)));

        if (request.email()) {
            List<String> emails = db.list("select u.email from User u where u.id in ?1 and u.isActive = true", String.class, ids);
            String link = request.link() == null ? "" : "<p><a href=\"" + props.webAppUrl + request.link() + "\">Kaydı aç</a></p>";
            afterCommit(() -> emails.forEach(e -> mail.send(e, request.title(), "<p>" + HtmlUtils.htmlEscape(request.body()) + "</p>" + link)));
        }
    }

    /** Active users holding a permission in a company (role based). */
    public List<UUID> usersWithPermission(UUID companyId, String code) {
        return db.list("""
                select distinct ur.userId from UserRole ur
                  join User u on u.id = ur.userId
                  join RolePermission rp on rp.roleId = ur.roleId
                  join Permission p on p.id = rp.permissionId
                 where ur.companyId = ?1 and u.isActive = true and p.code = ?2""", UUID.class, companyId, code);
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
