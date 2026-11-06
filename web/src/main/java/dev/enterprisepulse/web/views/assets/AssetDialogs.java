package dev.enterprisepulse.web.views.assets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.ResourceViews.ActionContext;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Assign / transfer, return, retire and maintenance dialogs of the asset card. */
public final class AssetDialogs {
    private AssetDialogs() {}

    private static Dialog dialog(String title, ObjectNode asset) {
        Dialog d = new Dialog();
        d.setHeaderTitle(title + " · " + asset.path("assetTag").asText() + " " + asset.path("name").asText());
        d.setWidth("min(560px, 96vw)");
        return d;
    }

    private static DatePicker date(String label, LocalDate value) {
        DatePicker p = new DatePicker(label, value);
        p.setI18n(Ui.datePickerI18n());
        p.setWidthFull();
        return p;
    }

    private static String iso(DatePicker p) {
        return p.getValue() == null ? null : p.getValue().toString();
    }

    private static Div body(com.vaadin.flow.component.Component... parts) {
        Div body = new Div(parts);
        body.addClassName("ep-stack");
        return body;
    }

    private static Button primary(String text, Runnable click) {
        Button ok = new Button(text, e -> click.run());
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return ok;
    }

    /** Zimmetle (in-stock assets) or Devret (assigned assets); opens the hand-over form afterwards. */
    public static Consumer<ActionContext> assign(boolean transfer) {
        return ctx -> {
            ObjectNode asset = ctx.record();
            Dialog d = dialog(transfer ? Ui.T("Devret", "Transfer") : Ui.T("Zimmetle", "Assign"), asset);
            Select<String> type = AssetUi.enumSelect(Ui.T("Zimmet türü", "Assignee type"), "AssigneeType", List.of("EMPLOYEE", "DEPARTMENT", "LOCATION"), "EMPLOYEE");
            RefPicker employee = new RefPicker("employees", Ui.T("Personel", "Employee"));
            employee.setFilters(Map.of("status", "ACTIVE"));
            RefPicker department = new RefPicker("departments", Ui.T("Departman", "Department"));
            RefPicker location = new RefPicker("asset-locations", Ui.T("Lokasyon", "Location"));
            for (RefPicker p : List.of(employee, department, location)) p.setWidthFull();
            Runnable sync = () -> {
                employee.setVisible("EMPLOYEE".equals(type.getValue()));
                department.setVisible("DEPARTMENT".equals(type.getValue()));
                location.setVisible("LOCATION".equals(type.getValue()));
            };
            type.addValueChangeListener(e -> sync.run());
            sync.run();
            DatePicker assignedAt = date(transfer ? Ui.T("Devir tarihi", "Transfer date") : Ui.T("Zimmet tarihi", "Assigned on"), LocalDate.now());
            DatePicker expected = date(Ui.T("Beklenen iade (isteğe bağlı)", "Expected return (optional)"), null);
            Div dates = new Div(assignedAt, expected);
            dates.addClassName("ep-form-2");
            TextArea notes = new TextArea(Ui.T("Notlar", "Notes"));
            notes.setWidthFull();
            Div intro = transfer ? Ui.alert(Ui.T("Mevcut zimmet kapatılır ve yeni sorumlu adına tutanak oluşturulur.",
                    "The current assignment is closed and a new hand-over form is created."), "info") : new Div();
            d.add(body(intro, type, employee, department, location, dates, notes));
            Button ok = primary(transfer ? Ui.T("Devret", "Transfer") : Ui.T("Zimmetle", "Assign"), () -> {});
            ok.addClickListener(e -> {
                JsonNode[] res = new JsonNode[1];
                boolean saved = Ui.run(() -> res[0] = Ui.api().post("/api/assets/" + ctx.id() + (transfer ? "/transfer" : "/assign"), ApiClient.obj(
                        "assigneeType", type.getValue(), "employeeId", employee.getRefId(), "departmentId", department.getRefId(),
                        "locationId", location.getRefId(), "assignedAt", iso(assignedAt), "expectedReturnDate", iso(expected),
                        "notes", notes.getValue().isBlank() ? null : notes.getValue())), Ui.T("Zimmet tutanağı oluşturuldu", "Hand-over form created"));
                if (saved) {
                    d.close();
                    UI.getCurrent().navigate("assets/assignments/" + res[0].path("id").asText());
                }
            });
            d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), ok);
            d.open();
        };
    }

    public static void returnAsset(ActionContext ctx) {
        ObjectNode asset = ctx.record();
        Dialog d = dialog(Ui.T("İade al", "Return"), asset);
        DatePicker returnedAt = date(Ui.T("İade tarihi", "Returned on"), LocalDate.now());
        Select<String> condition = AssetUi.enumSelect(Ui.T("Fiziksel durum", "Condition"), "AssetCondition", AssetUi.CONDITIONS,
                asset.path("condition").asText("GOOD"));
        RefPicker location = new RefPicker("asset-locations", Ui.T("Teslim alınan lokasyon", "Location"));
        location.setWidthFull();
        TextArea notes = new TextArea(Ui.T("Notlar", "Notes"));
        notes.setWidthFull();
        d.add(body(returnedAt, condition, location, notes));
        Button ok = primary(Ui.T("İade al", "Return"), () -> {
            boolean saved = Ui.run(() -> Ui.api().post("/api/assets/" + ctx.id() + "/return", ApiClient.obj("returnedAt", iso(returnedAt),
                    "conditionIn", condition.getValue(), "locationId", location.getRefId(), "returnNotes", notes.getValue().isBlank() ? null : notes.getValue())),
                    Ui.T("Demirbaş iade alındı", "Asset returned"));
            if (saved) {
                d.close();
                ctx.reload().run();
            }
        });
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), ok);
        d.open();
    }

    public static void retire(ActionContext ctx) {
        ObjectNode asset = ctx.record();
        Dialog d = dialog(Ui.T("Hizmet dışı bırak", "Retire"), asset);
        Select<String> status = AssetUi.enumSelect(Ui.T("Yeni durum", "New status"), "AssetStatus", List.of("RETIRED", "LOST"), "RETIRED");
        TextArea reason = new TextArea(Ui.T("Neden", "Reason"));
        reason.setRequired(true);
        reason.setWidthFull();
        d.add(body(Ui.alert(Ui.T("Hizmet dışı demirbaşlar düzenlenemez ve zimmetlenemez.", "Retired assets can no longer be edited or assigned."), "warning"),
                status, reason));
        Button ok = primary(Ui.T("Hizmet dışı bırak", "Retire"), () -> {
            boolean saved = Ui.run(() -> Ui.api().post("/api/assets/" + ctx.id() + "/retire", ApiClient.obj("status", status.getValue(), "reason", reason.getValue())),
                    Ui.L("saved"));
            if (saved) {
                d.close();
                ctx.reload().run();
            }
        });
        ok.addThemeVariants(ButtonVariant.LUMO_ERROR);
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), ok);
        d.open();
    }

    /** New maintenance record; an in-stock asset moves to "in maintenance" until it is completed. */
    public static void createMaintenance(ObjectNode asset, Runnable done) {
        Dialog d = dialog(Ui.T("Bakım kaydı", "Maintenance"), asset);
        Select<String> type = AssetUi.enumSelect(Ui.T("Tür", "Type"), "MaintenanceType", List.of("REPAIR", "SERVICE", "INSPECTION"), "REPAIR");
        DatePicker startedAt = date(Ui.T("Başlangıç", "Started on"), LocalDate.now());
        Div row = new Div(type, startedAt);
        row.addClassName("ep-form-2");
        TextField title = new TextField(Ui.T("Başlık", "Title"));
        title.setRequired(true);
        title.setWidthFull();
        TextField vendor = new TextField(Ui.T("Servis / tedarikçi", "Vendor"));
        BigDecimalField cost = new BigDecimalField(Ui.T("Tutar", "Cost"));
        Div row2 = new Div(vendor, cost);
        row2.addClassName("ep-form-2");
        TextArea description = new TextArea(Ui.T("Açıklama", "Description"));
        description.setWidthFull();
        d.add(body(row, title, row2, description));
        Button ok = primary(Ui.L("save"), () -> {
            boolean saved = Ui.run(() -> Ui.api().post("/api/assets/" + asset.path("id").asText() + "/maintenances", ApiClient.obj("type", type.getValue(),
                    "startedAt", iso(startedAt), "title", title.getValue(), "vendor", vendor.getValue().isBlank() ? null : vendor.getValue(),
                    "cost", cost.getValue(), "description", description.getValue().isBlank() ? null : description.getValue())), Ui.L("saved"));
            if (saved) {
                d.close();
                done.run();
            }
        });
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), ok);
        d.open();
    }

    public static void completeMaintenance(ObjectNode asset, ObjectNode maintenance, Runnable done) {
        Dialog d = dialog(Ui.T("Bakımı tamamla", "Complete maintenance"), asset);
        DatePicker completedAt = date(Ui.T("Tamamlanma tarihi", "Completed on"), LocalDate.now());
        BigDecimalField cost = new BigDecimalField(Ui.T("Tutar", "Cost"));
        if (maintenance.hasNonNull("cost")) cost.setValue(maintenance.get("cost").decimalValue());
        cost.setWidthFull();
        TextArea description = new TextArea(Ui.T("Yapılan işlem", "Work done"));
        description.setWidthFull();
        d.add(body(Ui.muted(maintenance.path("title").asText()), completedAt, cost, description));
        Button ok = primary(Ui.T("Tamamla", "Complete"), () -> {
            boolean saved = Ui.run(() -> Ui.api().post("/api/assets/" + asset.path("id").asText() + "/maintenances/" + maintenance.path("id").asText() + "/complete",
                    ApiClient.obj("completedAt", iso(completedAt), "cost", cost.getValue(),
                            "description", description.getValue().isBlank() ? null : description.getValue())), Ui.L("saved"));
            if (saved) {
                d.close();
                done.run();
            }
        });
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), ok);
        d.open();
    }

    /** Starts a count for an optional location / category and opens the count screen. */
    public static void createCount(Runnable done) {
        Dialog d = new Dialog();
        d.setHeaderTitle(Ui.T("Yeni sayım", "New count"));
        d.setWidth("min(560px, 96vw)");
        DatePicker countDate = date(Ui.T("Sayım tarihi", "Count date"), LocalDate.now());
        countDate.setRequired(true);
        RefPicker location = new RefPicker("asset-locations", Ui.T("Konum (isteğe bağlı)", "Location (optional)"));
        RefPicker category = new RefPicker("asset-categories", Ui.T("Kategori (isteğe bağlı)", "Category (optional)"));
        Div scope = new Div(location, category);
        scope.addClassName("ep-form-2");
        TextArea notes = new TextArea(Ui.T("Notlar", "Notes"));
        notes.setWidthFull();
        d.add(body(Ui.alert(Ui.T("Kapsamdaki tüm aktif demirbaşlar, o anki sorumlularıyla sayım listesine alınır.",
                "Every in-service asset in scope is listed with its current holder."), "info"), countDate, scope, notes));
        Button ok = primary(Ui.T("Sayımı başlat", "Start count"), () -> {
            JsonNode[] res = new JsonNode[1];
            boolean saved = Ui.run(() -> res[0] = Ui.api().post("/api/assets/counts", ApiClient.obj("countDate", iso(countDate),
                    "locationId", location.getRefId(), "categoryId", category.getRefId(), "notes", notes.getValue().isBlank() ? null : notes.getValue())));
            if (saved) {
                d.close();
                done.run();
                UI.getCurrent().navigate("assets/counts/" + res[0].path("id").asText());
            }
        });
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), ok);
        d.open();
    }
}
