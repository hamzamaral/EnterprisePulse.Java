package dev.enterprisepulse.web.views.assets;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.ArrayList;
import java.util.List;

@Route(value = "assets/my", layout = MainLayout.class)
@PageTitle("Zimmetlerim · EnterprisePulse")
public class MyAssetsView extends Div implements BeforeEnterObserver {
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        add(Ui.pageHeader(Ui.grow(Ui.title(Ui.T("Zimmetlerim", "My assets")),
                Ui.muted(Ui.T("Üzerinize zimmetli demirbaşlar ve teslim tutanakları", "Assets assigned to you and their hand-over forms")))));
        if (!Session.current().user().hasNonNull("employeeId")) {
            add(Ui.alert(Ui.T("Hesabınıza bağlı bir personel kaydı yok. İK ile iletişime geçin.", "No employee record is linked to your account. Please contact HR."), "info"));
            return;
        }
        List<ObjectNode> rows = new ArrayList<>();
        if (!Ui.run(() -> rows.addAll(Ui.rows(Ui.api().get("/api/assets/assignments/mine"))))) return;
        List<ObjectNode> active = rows.stream().filter(r -> "ACTIVE".equals(r.path("status").asText())).toList();
        List<ObjectNode> past = rows.stream().filter(r -> "RETURNED".equals(r.path("status").asText())).toList();

        if (active.isEmpty()) {
            Div empty = Ui.card(VaadinIcon.PACKAGE.create(), Ui.muted(Ui.T("Üzerinize zimmetli demirbaş yok", "You have no assets assigned")));
            empty.addClassName("ep-empty");
            add(empty);
        } else {
            Div cards = new Div();
            cards.addClassName("ep-asset-cards");
            for (ObjectNode a : active) cards.add(card(a));
            add(cards);
        }
        if (!past.isEmpty()) {
            H3 title = new H3(Ui.T("Geçmiş zimmetler", "Past assignments"));
            title.addClassName("ep-subtitle");
            Grid<ObjectNode> grid = new Grid<>();
            grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
            grid.addClassNames("ep-grid", "ep-clickable");
            grid.setAllRowsVisible(true);
            grid.addColumn(r -> r.path("number").asText()).setHeader(Ui.T("Tutanak", "Form")).setAutoWidth(true);
            grid.addColumn(r -> r.path("assetName").asText() + " · " + r.path("assetTag").asText()).setHeader(Ui.T("Demirbaş", "Asset")).setFlexGrow(1);
            grid.addColumn(r -> AssetUi.day(r.get("assignedAt"))).setHeader(Ui.T("Zimmet", "Assigned"));
            grid.addColumn(r -> AssetUi.day(r.get("returnedAt"))).setHeader(Ui.T("İade", "Returned"));
            grid.addColumn(r -> AssetUi.enumLabel("AssetCondition", r.get("conditionIn"))).setHeader(Ui.T("İade durumu", "Returned condition"));
            grid.addItemClickListener(e -> open(e.getItem()));
            grid.setItems(past);
            add(title, grid);
        }
    }

    private static void open(ObjectNode row) {
        UI.getCurrent().navigate("assets/assignments/" + row.path("id").asText());
    }

    private static Div card(ObjectNode a) {
        boolean acknowledged = a.hasNonNull("acknowledgedAt");
        Span tag = new Span(a.path("assetTag").asText());
        tag.addClassNames("ep-mono", "ep-muted");
        Div head = new Div(new Div(new H4(a.path("assetName").asText()), tag),
                Ui.badge(acknowledged ? Ui.T("Onaylandı", "Acknowledged") : Ui.T("Onay bekliyor", "Awaiting acknowledgement"), acknowledged ? "success" : "warning"));
        head.addClassName("ep-row-between");
        Button open = new Button(acknowledged ? Ui.T("Tutanağı görüntüle", "View form") : Ui.T("İncele ve onayla", "Review and sign"),
                VaadinIcon.FILE_TEXT_O.create(), e -> open(a));
        open.addThemeVariants(ButtonVariant.LUMO_SMALL);
        if (!acknowledged) open.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        open.setWidthFull();
        Div card = Ui.card(head, AssetUi.facts(AssetUi.fact(Ui.T("Zimmet tarihi", "Assigned on"), AssetUi.day(a.get("assignedAt"))),
                AssetUi.fact(Ui.T("Beklenen iade", "Expected return"), AssetUi.day(a.get("expectedReturnDate")))), open);
        card.addClassName("ep-asset-card");
        return card;
    }
}
