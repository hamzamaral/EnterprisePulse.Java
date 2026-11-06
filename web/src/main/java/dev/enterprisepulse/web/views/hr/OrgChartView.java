package dev.enterprisepulse.web.views.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.ListItem;
import com.vaadin.flow.component.html.UnorderedList;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Route(value = "hr/org-chart", layout = MainLayout.class)
@PageTitle("Organizasyon şeması · EnterprisePulse")
public class OrgChartView extends Div implements BeforeEnterObserver {
    private static final Locale TR = Locale.forLanguageTag("tr");
    private final Div chart = new Div();
    private final List<JsonNode> roots = new ArrayList<>();
    private String search = "";

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        TextField searchField = new TextField();
        searchField.setPlaceholder(Ui.L("search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> {
            search = e.getValue().trim().toLowerCase(TR);
            render();
        });
        add(Ui.pageHeader(Ui.grow(Ui.title(Ui.T("Organizasyon şeması", "Organization chart")),
                Ui.muted(Ui.T("Aktif personelin raporlama hiyerarşisi", "Reporting hierarchy of active employees"))), searchField));
        chart.addClassName("ep-org");
        add(chart);
        roots.clear();
        Ui.run(() -> Ui.api().get("/api/hr/org-chart").forEach(roots::add));
        render();
    }

    private void render() {
        chart.removeAll();
        if (roots.isEmpty()) {
            chart.add(Ui.alert(Ui.L("noRecords"), "info"));
            return;
        }
        UnorderedList list = new UnorderedList();
        roots.forEach(n -> list.add(node(n)));
        chart.add(list);
    }

    private boolean match(JsonNode n) {
        if (search.isEmpty()) return false;
        for (String key : List.of("fullName", "positionName", "departmentName"))
            if (n.hasNonNull(key) && n.get(key).asText().toLowerCase(TR).contains(search)) return true;
        return false;
    }

    private ListItem node(JsonNode n) {
        JsonNode children = n.path("children");
        Avatar avatar = new Avatar(n.path("fullName").asText());
        avatar.addThemeVariants(com.vaadin.flow.component.avatar.AvatarVariant.LUMO_SMALL);
        avatar.setColorIndex(0);
        Div name = new Div(n.path("fullName").asText());
        name.addClassName("ep-org-name");
        String dept = n.hasNonNull("departmentName") ? n.get("departmentName").asText() : "";
        if (!children.isEmpty()) dept += " · " + children.size() + " " + Ui.T("kişi", "reports");
        Div info = new Div(name, Ui.muted(HrUi.orDash(n.get("positionName"))), Ui.muted(dept));
        Div card = new Div(avatar, info);
        card.addClassName("ep-org-card");
        if (match(n)) card.addClassName("ep-org-hit");
        String id = n.path("id").asText();
        card.addClickListener(e -> {
            if (Session.current().has("employees:read")) UI.getCurrent().navigate("r/employees/" + id);
        });
        ListItem li = new ListItem(card);
        if (!children.isEmpty()) {
            UnorderedList ul = new UnorderedList();
            children.forEach(c -> ul.add(node(c)));
            li.add(ul);
        }
        return li;
    }
}
