package dev.enterprisepulse.web.views;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Route(value = "", layout = MainLayout.class)
@PageTitle("Gösterge Paneli · EnterprisePulse")
public class HomeView extends Div implements BeforeEnterObserver {
    private final Div body = new Div();

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        Session s = Session.current();
        if (!s.isAuthenticated()) return;
        removeAll();
        JsonNode company = s.company();
        Div hello = new Div(Ui.title(Ui.T("Merhaba", "Hello") + ", " + s.user().path("firstName").asText()),
                Ui.muted((company == null ? "" : company.path("name").asText()) + " · "
                        + LocalDate.now().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, EEEE", Fmt.locale(s.locale)))));
        hello.addClassName("ep-grow");
        add(Ui.pageHeader(hello, Ui.iconButton(VaadinIcon.REFRESH, Ui.L("refresh"), this::load)), body);
        load();
    }

    private void load() {
        body.removeAll();
        JsonNode[] sections = new JsonNode[1];
        Ui.run(() -> sections[0] = Ui.api().get("/api/dashboard"));
        if (sections[0] == null) return;
        for (JsonNode section : sections[0]) {
            H3 title = new H3(section.path("title").asText());
            title.addClassName("ep-section-title");
            Div cards = new Div();
            cards.addClassName("ep-cards");
            for (JsonNode card : section.path("cards")) {
                Icon icon = Ui.icon(card.path("icon").asText(null)).create();
                Div iconBox = new Div(icon);
                iconBox.addClassNames("ep-card-icon", card.path("color").asText("primary"));
                Span value = new Span(card.path("value").asText());
                value.addClassName("ep-card-value");
                Div text = new Div(value, new Div(Ui.muted(card.path("title").asText())));
                if (card.hasNonNull("hint")) text.add(new Div(Ui.muted(card.get("hint").asText())));
                Div c = Ui.card(iconBox, text);
                c.addClassName("ep-stat");
                String link = card.path("link").asText(null);
                if (link != null) {
                    c.addClassName("ep-clickable-card");
                    c.getElement().addEventListener("click", e -> Ui.go(link));
                }
                cards.add(c);
            }
            body.add(title, cards);
        }
    }
}
