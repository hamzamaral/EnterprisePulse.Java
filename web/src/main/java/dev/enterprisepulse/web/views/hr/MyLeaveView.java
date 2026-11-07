package dev.enterprisepulse.web.views.hr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.PromptDialog;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Route(value = "hr/my-leave", layout = MainLayout.class)
@PageTitle("İzinlerim · EnterprisePulse")
public class MyLeaveView extends Div implements BeforeEnterObserver {
    private final Div balances = new Div();
    private final Grid<ObjectNode> grid = new Grid<>();

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        removeAll();
        boolean hasEmployee = Session.current().user().hasNonNull("employeeId");
        Div header = Ui.pageHeader(Ui.grow(Ui.title(Ui.T("İzinlerim", "My leave")), Ui.muted(Ui.T("İzin bakiyeniz ve talepleriniz", "Your leave balance and requests"))));
        if (hasEmployee) {
            Button create = new Button(Ui.T("Yeni talep", "New request"), VaadinIcon.PLUS.create(), e -> LeaveRequestDialog.open(false, this::load));
            create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            header.add(create);
        }
        add(header);
        if (!hasEmployee) {
            add(Ui.alert(Ui.T("Hesabınıza bağlı bir personel kaydı yok. İK ile iletişime geçin.", "No employee record is linked to your account. Please contact HR."), "info"));
            return;
        }
        balances.addClassName("ep-cards");
        grid.removeAllColumns();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.addClassNames("ep-grid", "ep-clickable");
        grid.setAllRowsVisible(true);
        grid.setEmptyStateText(Ui.T("Henüz izin talebiniz yok", "You have no leave requests yet"));
        grid.addColumn(r -> r.path("number").asText()).setHeader(Ui.T("Numara", "Number")).setAutoWidth(true);
        grid.addColumn(new ComponentRenderer<>(r -> new Span(Ui.dot(r.path("leaveTypeColor").asText()), new Span(r.path("leaveTypeName").asText()))))
                .setHeader(Ui.T("Tür", "Type")).setAutoWidth(true);
        grid.addColumn(r -> Fmt.date(r.get("startDate")) + " – " + Fmt.date(r.get("endDate"))).setHeader(Ui.T("Tarih", "Dates")).setAutoWidth(true);
        grid.addColumn(r -> r.path("days").asText()).setHeader(Ui.T("Gün", "Days")).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(new ComponentRenderer<>(r -> Ui.badge(Ui.meta().enumLabel("LeaveRequestStatus", r.path("status").asText(), Ui.locale()),
                Fmt.statusColor(r.path("status").asText())))).setHeader(Ui.L("status"));
        grid.addColumn(new ComponentRenderer<>(this::cancelButton)).setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
        grid.addItemClickListener(e -> {
            String instance = HrUi.text(e.getItem().get("workflowInstanceId"));
            if (instance != null) UI.getCurrent().navigate("workflow/instances/" + instance);
        });
        add(balances, grid);
        load();
    }

    private Div cancelButton(ObjectNode row) {
        Div box = new Div();
        if (!row.path("canCancel").asBoolean()) return box;
        boolean pending = row.path("status").asText().equals("PENDING");
        Button b = new Button(pending ? Ui.L("withdraw") : Ui.L("cancel"), VaadinIcon.CLOSE_CIRCLE.create(), e ->
                PromptDialog.ask(Ui.T("Talebi iptal et", "Cancel request"), Ui.L("comment"), false, null, Ui.L("yes"), "error", comment -> {
                    if (Ui.run(() -> Ui.api().post("/api/hr/leave-requests/" + row.path("id").asText() + "/cancel",
                            ApiClient.obj("comment", comment.isBlank() ? null : comment)), Ui.L("saved"))) load();
                }));
        b.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        box.add(Ui.stopClick(b));
        return box;
    }

    private void load() {
        balances.removeAll();
        List<ObjectNode> rows = new ArrayList<>();
        Ui.run(() -> {
            String employeeId = Session.current().user() == null ? null : Session.current().user().path("employeeId").asText(null);
            String balanceUrl = "/api/hr/leave-balances?year=" + LocalDate.now().getYear()
                    + (employeeId == null || employeeId.isBlank() ? "" : "&employeeId=" + employeeId);
            for (JsonNode b : Ui.api().get(balanceUrl)) {
                Span value = new Span(b.path("remainingDays").asText());
                value.addClassName("ep-card-value");
                Div card = Ui.card(Ui.muted(b.path("leaveTypeName").asText() + " · " + b.path("year").asText()),
                        new Div(value, Ui.muted(" " + Ui.T("gün kaldı", "days left"))),
                        Ui.muted(Ui.T("Hak: " + b.path("entitledDays").asText() + " · Kullanılan: " + b.path("usedDays").asText() + " · Bekleyen: " + b.path("pendingDays").asText(),
                                "Entitled: " + b.path("entitledDays").asText() + " · Used: " + b.path("usedDays").asText() + " · Pending: " + b.path("pendingDays").asText())));
                card.addClassName("ep-balance");
                card.getStyle().set("border-left", "4px solid " + b.path("leaveTypeColor").asText());
                balances.add(card);
            }
            rows.addAll(Ui.rows(Ui.api().get("/api/hr/leave-requests?mine=true&pageSize=200").get("data")));
        });
        grid.setItems(rows);
    }
}
