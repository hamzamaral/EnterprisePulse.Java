package dev.enterprisepulse.web.views.hr;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.PromptDialog;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Route(value = "hr/leave-balances", layout = MainLayout.class)
@PageTitle("İzin bakiyeleri · EnterprisePulse")
public class LeaveBalancesView extends Div implements BeforeEnterObserver {
    private final Grid<ObjectNode> grid = new Grid<>();
    private final List<ObjectNode> rows = new ArrayList<>();
    private int year = LocalDate.now().getYear();
    private String search = "";

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        boolean canEdit = Session.current().has("leave:update");
        TextField searchField = new TextField();
        searchField.setPlaceholder(Ui.L("search"));
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setValueChangeMode(ValueChangeMode.EAGER);
        searchField.addValueChangeListener(e -> {
            search = e.getValue();
            filter();
        });
        Select<Integer> yearSelect = new Select<>();
        int now = LocalDate.now().getYear();
        yearSelect.setItems(now - 1, now, now + 1);
        yearSelect.setValue(year);
        yearSelect.setWidth("110px");
        yearSelect.addValueChangeListener(e -> {
            year = e.getValue();
            load();
        });
        Div header = Ui.pageHeader(Ui.iconButton(VaadinIcon.ARROW_LEFT, Ui.L("back"), () -> Ui.go("r/leave-types")),
                Ui.grow(Ui.title(Ui.T("İzin bakiyeleri", "Leave balances")), Ui.muted(Ui.T("Kalan = hak edilen − kullanılan − onay bekleyen", "Remaining = entitled − used − pending"))),
                searchField, yearSelect);
        if (canEdit) {
            Button accrue = new Button(Ui.T("Eksik bakiyeleri oluştur", "Accrue balances"), VaadinIcon.REFRESH.create(), e -> Ui.run(() -> {
                int created = Ui.api().post("/api/hr/leave-balances/accrue", ApiClient.obj("year", year)).path("created").asInt();
                Ui.ok(Ui.T(created + " bakiye oluşturuldu", created + " balance(s) created"));
                load();
            }));
            header.add(accrue);
        }
        add(header);

        grid.removeAllColumns();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.addClassName("ep-grid");
        grid.setEmptyStateText(Ui.L("noRecords"));
        grid.addColumn(r -> r.path("employeeName").asText()).setHeader(Ui.T("Personel", "Employee")).setAutoWidth(true).setSortable(true);
        grid.addColumn(new ComponentRenderer<>(r -> new Span(Ui.dot(r.path("leaveTypeColor").asText()), new Span(r.path("leaveTypeName").asText()))))
                .setHeader(Ui.T("Tür", "Type")).setAutoWidth(true);
        grid.addColumn(r -> r.path("entitledDays").asInt()).setHeader(Ui.T("Hak edilen", "Entitled")).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(r -> r.path("usedDays").asInt()).setHeader(Ui.T("Kullanılan", "Used")).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(r -> r.path("pendingDays").asInt()).setHeader(Ui.T("Bekleyen", "Pending")).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(new ComponentRenderer<>(r -> {
            Span s = new Span(r.path("remainingDays").asText());
            s.addClassName("ep-strong");
            return s;
        })).setHeader(Ui.T("Kalan", "Remaining")).setTextAlign(ColumnTextAlign.END);
        if (canEdit) grid.addColumn(new ComponentRenderer<>(r -> Ui.iconButton(VaadinIcon.EDIT, Ui.L("edit"), () -> edit(r)))).setFlexGrow(0).setAutoWidth(true);
        grid.setHeight("calc(100vh - 220px)");
        add(grid);
        load();
    }

    private void load() {
        rows.clear();
        Ui.run(() -> rows.addAll(Ui.rows(Ui.api().get("/api/hr/leave-balances?year=" + year))));
        filter();
    }

    private void filter() {
        String q = search.toLowerCase(Locale.forLanguageTag("tr"));
        grid.setItems(q.isBlank() ? rows : rows.stream().filter(r -> r.path("employeeName").asText().toLowerCase(Locale.forLanguageTag("tr")).contains(q)).toList());
    }

    private void edit(ObjectNode row) {
        PromptDialog.ask(row.path("employeeName").asText() + " · " + row.path("leaveTypeName").asText(), Ui.T("Hak edilen gün", "Entitled days"), true,
                Ui.T("Şu an: " + row.path("entitledDays").asText() + " gün", "Currently " + row.path("entitledDays").asText() + " days"), Ui.L("save"), null, value -> {
                    int days;
                    try {
                        days = Integer.parseInt(value.trim());
                    } catch (NumberFormatException ex) {
                        Ui.error(Ui.T("Sayı girin", "Enter a number"));
                        return;
                    }
                    if (Ui.run(() -> Ui.api().patch("/api/hr/leave-balances/" + row.path("id").asText(), ApiClient.obj("entitledDays", days)), Ui.L("saved"))) load();
                });
    }
}
