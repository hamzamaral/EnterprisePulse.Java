package dev.enterprisepulse.web.views.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.ArrayList;
import java.util.List;

@Route(value = "notifications", layout = MainLayout.class)
@PageTitle("Bildirimler · EnterprisePulse")
public class NotificationsView extends Div implements BeforeEnterObserver {
    private final Div list = new Div();
    private final Div pager = new Div();
    private boolean unreadOnly;
    private int page = 1;
    private int pages = 1;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        Checkbox unread = new Checkbox(Ui.T("Yalnızca okunmamış", "Unread only"), e -> {
            unreadOnly = e.getValue();
            page = 1;
            load();
        });
        Button readAll = new Button(Ui.L("markAllRead"), VaadinIcon.CHECK_SQUARE_O.create(), e -> {
            if (Ui.run(() -> Ui.api().post("/api/notifications/read-all"))) load();
        });
        Div header = Ui.pageHeader(Ui.grow(Ui.title(Ui.L("notifications"))), unread, readAll);
        if (Session.current().has("notifications:send")) {
            Button announce = new Button(Ui.T("Duyuru gönder", "Send announcement"), VaadinIcon.MEGAPHONE.create(), e -> AnnouncementDialog.open());
            announce.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            header.add(announce);
        }
        list.addClassNames("ep-card", "ep-notifications");
        pager.addClassName("ep-row");
        add(header, list, pager);
        load();
    }

    private void load() {
        list.removeAll();
        List<ObjectNode> rows = new ArrayList<>();
        Ui.run(() -> {
            JsonNode res = Ui.api().get("/api/notifications?page=" + page + "&pageSize=20&unread=" + unreadOnly);
            rows.addAll(Ui.rows(res.get("data")));
            pages = Math.max(1, res.path("meta").path("totalPages").asInt(1));
        });
        for (ObjectNode n : rows) {
            boolean unread = n.get("readAt") == null || n.get("readAt").isNull();
            Span title = new Span(n.path("title").asText());
            if (unread) title.addClassName("ep-strong");
            Div item = new Div((unread ? VaadinIcon.CIRCLE : VaadinIcon.CIRCLE_THIN).create(),
                    new Div(new Div(title), new Div(new Span(n.path("body").asText(""))), Ui.muted(Fmt.dateTime(n.get("createdAt")))));
            item.addClassNames("ep-list-item", unread ? "unread" : "read");
            item.getElement().addEventListener("click", e -> open(n));
            list.add(item);
        }
        if (rows.isEmpty()) list.add(Ui.muted(Ui.L("noRecords")));
        pager.removeAll();
        if (pages > 1) {
            Button prev = new Button(VaadinIcon.ANGLE_LEFT.create(), e -> {
                page--;
                load();
            });
            prev.setEnabled(page > 1);
            Button next = new Button(VaadinIcon.ANGLE_RIGHT.create(), e -> {
                page++;
                load();
            });
            next.setEnabled(page < pages);
            pager.add(prev, new Span(page + " / " + pages), next);
        }
    }

    private void open(ObjectNode n) {
        Ui.run(() -> Ui.api().post("/api/notifications/" + n.path("id").asText() + "/read"));
        String link = n.path("link").asText("");
        if (!link.isEmpty()) Ui.go(link);
        else load();
    }
}
