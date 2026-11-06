package dev.enterprisepulse.web.views.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import dev.enterprisepulse.web.core.ApiException;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Meta;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.List;
import java.util.stream.Stream;

@Route(value = "workflow/inbox", layout = MainLayout.class)
@RouteAlias(value = "workflow/instances", layout = MainLayout.class)
@PageTitle("Onay Süreçleri · EnterprisePulse")
public class InboxView extends Div implements BeforeEnterObserver {
    private boolean inbox;
    private String status;
    private boolean mine;
    private Grid<ObjectNode> grid;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated() || Meta.get() == null) return;
        inbox = event.getLocation().getPath().startsWith("workflow/inbox");
        mine = !Session.current().has("workflow:read");
        status = null;
        build();
    }

    private void build() {
        removeAll();
        Div header = Ui.pageHeader(Ui.grow(Ui.title(inbox ? Ui.L("inbox") : Ui.T("Onay süreçleri", "Approval flows"))));
        if (!inbox) {
            ComboBox<String> statusBox = new ComboBox<>(Ui.L("status"), List.of("PENDING", "APPROVED", "REJECTED", "CANCELLED"));
            statusBox.setItemLabelGenerator(s -> Meta.get().enumLabel("WorkflowStatus", s, Ui.locale()));
            statusBox.setClearButtonVisible(true);
            statusBox.addValueChangeListener(e -> {
                status = e.getValue();
                grid.getDataProvider().refreshAll();
            });
            Checkbox mineBox = new Checkbox(Ui.L("myRequests"), mine);
            mineBox.setEnabled(Session.current().has("workflow:read"));
            mineBox.addValueChangeListener(e -> {
                mine = e.getValue();
                grid.getDataProvider().refreshAll();
            });
            header.add(statusBox, mineBox);
        }
        Tab inboxTab = new Tab(Ui.L("inbox"));
        Tab allTab = new Tab(Ui.T("Tüm süreçler", "All flows"));
        Tabs tabs = new Tabs(inboxTab, allTab);
        tabs.setSelectedTab(inbox ? inboxTab : allTab);
        tabs.addSelectedChangeListener(e -> UI.getCurrent().navigate(e.getSelectedTab() == inboxTab ? "workflow/inbox" : "workflow/instances"));

        grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT);
        grid.addClassNames("ep-grid", "ep-clickable");
        grid.setPageSize(25);
        grid.setEmptyStateText(inbox ? Ui.T("Onayınızı bekleyen talep yok 🎉", "Nothing waiting for your approval") : Ui.L("noRecords"));
        grid.addColumn(new ComponentRenderer<>(r -> {
            Span s = new Span(r.path("title").asText());
            s.addClassName("ep-strong");
            return s;
        })).setHeader(Ui.T("Başlık", "Title")).setAutoWidth(true);
        grid.addColumn(r -> r.path("definitionName").asText()).setHeader(Ui.T("Akış", "Flow")).setAutoWidth(true);
        grid.addColumn(r -> r.path("currentStepOrder").asText() + "/" + r.path("totalSteps").asText() + " · " + r.path("currentStepName").asText(""))
                .setHeader(Ui.L("step")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(r -> Ui.badge(Meta.get().enumLabel("WorkflowStatus", r.path("status").asText(), Ui.locale()),
                Fmt.statusColor(r.path("status").asText())))).setHeader(Ui.L("status")).setAutoWidth(true);
        grid.addColumn(r -> r.path("startedByName").asText("")).setHeader(Ui.L("startedBy")).setAutoWidth(true);
        grid.addColumn(r -> Fmt.dateTime(r.get("startedAt"))).setHeader(Ui.L("startedAt")).setAutoWidth(true);
        grid.addItemClickListener(e -> UI.getCurrent().navigate("workflow/instances/" + e.getItem().path("id").asText()));
        grid.setItems(q -> fetch(q.getOffset(), q.getLimit()));
        add(header, tabs, grid);
    }

    private Stream<ObjectNode> fetch(int offset, int limit) {
        int pageSize = grid.getPageSize();
        int page = offset / pageSize + 1;
        try {
            String url = inbox
                    ? "/api/workflow/inbox?page=" + page + "&pageSize=" + pageSize
                    : "/api/workflow/instances?page=" + page + "&pageSize=" + pageSize + "&mine=" + mine + (status != null ? "&status=" + status : "");
            JsonNode res = Ui.api().get(url);
            return Ui.rows(res.get("data")).stream().skip(offset - (long) (page - 1) * pageSize).limit(limit);
        } catch (ApiException ex) {
            if (ex.status != 401) Ui.error(ex.getMessage());
            return Stream.empty();
        }
    }
}
