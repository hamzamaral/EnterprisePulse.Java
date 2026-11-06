package dev.enterprisepulse.web.views.assets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.server.StreamResource;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.ResourceViews.TabContext;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Custom tabs of the asset card: depreciation and label, assignment history, maintenance. */
public final class AssetTabs {
    private AssetTabs() {}

    private static Div section(String title, Component... parts) {
        Div d = Ui.card(new H4(title));
        d.add(parts);
        d.addClassName("ep-section");
        return d;
    }

    private static <T> Grid<T> grid(String empty) {
        Grid<T> g = new Grid<>();
        g.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        g.addClassName("ep-grid");
        g.setAllRowsVisible(true);
        g.setEmptyStateText(empty);
        return g;
    }

    /** Holder, condition and warranty; straight-line depreciation with its yearly schedule; the QR label. */
    public static Component overview(TabContext ctx) {
        String id = ctx.record().path("id").asText();
        Div root = new Div();
        root.addClassName("ep-asset-overview");
        Ui.run(() -> {
            JsonNode a = Ui.api().get("/api/assets/" + id);
            String currency = a.path("currency").asText("TRY");
            Div main = new Div();
            main.addClassName("ep-stack");

            Component holder;
            JsonNode h = a.get("holder");
            if (h != null && !h.isNull()) {
                Anchor link = new Anchor("assets/assignments/" + h.path("assignmentId").asText(), h.path("name").asText());
                holder = new Span(link, Ui.muted(" · " + AssetUi.enumLabel("AssigneeType", h.get("type"))));
            } else {
                holder = new Span(AssetUi.text(a.get("locationName")));
            }
            Span warranty = new Span(AssetUi.day(a.get("warrantyEndDate")) + " ");
            Span state = AssetUi.warranty(a.hasNonNull("warrantyState") ? a.get("warrantyState").asText() : null);
            if (state != null) warranty.add(state);
            main.add(section(Ui.T("Sorumlu ve garanti", "Holder and warranty"), AssetUi.facts(
                    AssetUi.fact(Ui.T("Sorumlu", "Holder"), holder),
                    AssetUi.fact(Ui.T("Durum", "Condition"), AssetUi.conditionBadge(a.get("condition"))),
                    AssetUi.fact(Ui.T("Garanti bitişi", "Warranty ends"), warranty))));

            JsonNode dep = a.get("depreciation");
            if (dep == null || dep.isNull()) {
                main.add(section(Ui.T("Amortisman (doğrusal)", "Depreciation (straight-line)"),
                        Ui.muted(Ui.T("Amortisman için alış tarihi ve maliyeti girilmelidir.", "Purchase date and cost are needed to compute depreciation."))));
            } else {
                int life = dep.path("usefulLifeMonths").asInt(), elapsed = dep.path("elapsedMonths").asInt();
                Span book = new Span(Fmt.money(AssetUi.dec(dep.get("bookValue")), currency, Ui.locale()));
                book.addClassName("ep-stat-value");
                ProgressBar bar = new ProgressBar(0, 1, life <= 0 ? 0 : Math.min(1, (double) elapsed / life));
                String months = Ui.T(elapsed + " / " + life + " ay geçti", elapsed + " of " + life + " months elapsed")
                        + (dep.path("fullyDepreciated").asBoolean() ? " · " + Ui.T("Tamamen amorti edildi", "Fully depreciated") : "");
                Div progress = new Div(bar, Ui.muted(months));
                progress.addClassName("ep-grow");
                Div head = new Div(new Div(Ui.muted(Ui.T("Net defter değeri", "Book value")), new Div(book)), progress);
                head.addClassName("ep-dep-head");

                Grid<JsonNode> schedule = grid("");
                int thisYear = LocalDate.now().getYear();
                schedule.addColumn(y -> y.path("year").asText()).setHeader(Ui.T("Yıl", "Year"));
                schedule.addColumn(y -> y.path("months").asText()).setHeader(Ui.T("Ay", "Months")).setTextAlign(ColumnTextAlign.END);
                schedule.addColumn(y -> Fmt.number(AssetUi.dec(y.get("depreciation")), 2, Ui.locale())).setHeader(Ui.T("Amortisman", "Depreciation"))
                        .setTextAlign(ColumnTextAlign.END);
                schedule.addColumn(y -> Fmt.number(AssetUi.dec(y.get("bookValueEnd")), 2, Ui.locale())).setHeader(Ui.T("Yıl sonu defter değeri", "Book value at year end"))
                        .setTextAlign(ColumnTextAlign.END);
                schedule.setPartNameGenerator(y -> y.path("year").asInt() == thisYear ? "ep-row-current" : null);
                List<JsonNode> years = new ArrayList<>();
                dep.path("schedule").forEach(years::add);
                schedule.setItems(years);

                main.add(section(Ui.T("Amortisman (doğrusal)", "Depreciation (straight-line)"), head, AssetUi.facts(
                        AssetUi.fact(Ui.T("Alış maliyeti", "Purchase cost"), AssetUi.money(a.get("purchaseCost"), currency)),
                        AssetUi.fact(Ui.T("Hurda değeri", "Salvage value"), AssetUi.money(a.get("salvageValue"), currency)),
                        AssetUi.fact(Ui.T("Aylık amortisman", "Monthly depreciation"), AssetUi.money(dep.get("monthlyAmount"), currency)),
                        AssetUi.fact(Ui.T("Birikmiş amortisman", "Accumulated"), AssetUi.money(dep.get("accumulated"), currency))), schedule));
            }

            ApiClient.Download label = Ui.api().download("/api/assets/" + id + "/label");
            Image qr = new Image(new StreamResource(label.fileName(), () -> new ByteArrayInputStream(label.bytes())), "QR");
            qr.setWidth("160px");
            qr.setHeight("160px");
            Span tag = new Span(a.path("assetTag").asText());
            tag.addClassName("ep-mono");
            Button download = new Button(Ui.T("Etiketi indir", "Download label"), VaadinIcon.DOWNLOAD.create(), e -> Ui.save(label));
            download.addThemeVariants(ButtonVariant.LUMO_SMALL);
            Div qrCard = section(Ui.T("QR etiketi", "QR label"), qr, tag, Ui.muted(a.path("name").asText()), download);
            qrCard.addClassName("ep-qr");
            root.add(main, qrCard);
        });
        return root;
    }

    /** Assignment history; a row opens its hand-over form. */
    public static Component assignments(TabContext ctx) {
        Grid<ObjectNode> g = grid(Ui.T("Bu demirbaş henüz zimmetlenmedi", "This asset has not been assigned yet"));
        g.addClassName("ep-clickable");
        g.addColumn(r -> r.path("number").asText()).setHeader(Ui.T("Tutanak", "Form")).setAutoWidth(true);
        g.addColumn(r -> r.path("assigneeName").asText() + " · " + AssetUi.enumLabel("AssigneeType", r.get("assigneeType"))).setHeader(Ui.T("Sorumlu", "Holder"))
                .setAutoWidth(true);
        g.addColumn(r -> AssetUi.day(r.get("assignedAt"))).setHeader(Ui.T("Zimmet", "Assigned"));
        g.addColumn(r -> AssetUi.day(r.get("returnedAt"))).setHeader(Ui.T("İade", "Returned"));
        g.addColumn(new ComponentRenderer<>(r -> Ui.badge(AssetUi.enumLabel("AssignmentStatus", r.get("status")), Fmt.statusColor(r.path("status").asText()))))
                .setHeader(Ui.L("status"));
        g.addColumn(r -> {
            if (r.hasNonNull("acknowledgedAt")) return AssetUi.stamp(r.get("acknowledgedAt"));
            return "ACTIVE".equals(r.path("status").asText()) ? Ui.T("Bekliyor", "Pending") : "—";
        }).setHeader(Ui.T("Onay", "Acknowledged"));
        g.addColumn(r -> AssetUi.enumLabel("AssetCondition", r.hasNonNull("conditionIn") ? r.get("conditionIn") : r.get("conditionOut")))
                .setHeader(Ui.T("Fiziksel durum", "Condition"));
        g.addColumn(r -> AssetUi.text(r.get("returnNotes"))).setHeader(Ui.T("Notlar", "Notes")).setFlexGrow(1);
        g.addItemClickListener(e -> UI.getCurrent().navigate("assets/assignments/" + e.getItem().path("id").asText()));
        Ui.run(() -> g.setItems(Ui.rows(Ui.api().get("/api/assets/" + ctx.record().path("id").asText() + "/assignments"))));
        return g;
    }

    /** Maintenance records with add / complete. */
    public static Component maintenance(TabContext ctx) {
        ObjectNode asset = ctx.record();
        Div root = new Div();
        root.addClassName("ep-stack");
        boolean canEdit = Session.current().has("assets:update") && !List.of("RETIRED", "LOST").contains(asset.path("status").asText());
        Grid<ObjectNode> g = grid(Ui.T("Bakım kaydı yok", "No maintenance records"));
        Runnable[] load = new Runnable[1];
        Runnable changed = () -> {
            load[0].run();
            ctx.onChanged().run();
        };
        g.addColumn(r -> AssetUi.day(r.get("startedAt"))).setHeader(Ui.T("Başlangıç", "Started"));
        g.addColumn(r -> AssetUi.enumLabel("MaintenanceType", r.get("type"))).setHeader(Ui.T("Tür", "Type"));
        g.addColumn(new ComponentRenderer<>(r -> new Div(new Span(r.path("title").asText()),
                r.hasNonNull("description") ? new Div(Ui.muted(r.get("description").asText())) : new Div()))).setHeader(Ui.T("Başlık", "Title")).setFlexGrow(1);
        g.addColumn(r -> AssetUi.text(r.get("vendor"))).setHeader(Ui.T("Servis", "Vendor"));
        g.addColumn(r -> r.hasNonNull("cost") ? Fmt.number(AssetUi.dec(r.get("cost")), 2, Ui.locale()) : "—").setHeader(Ui.T("Tutar", "Cost"))
                .setTextAlign(ColumnTextAlign.END);
        g.addColumn(new ComponentRenderer<>(r -> Ui.badge(AssetUi.enumLabel("MaintenanceStatus", r.get("status")), Fmt.statusColor(r.path("status").asText()))))
                .setHeader(Ui.L("status"));
        g.addColumn(r -> AssetUi.day(r.get("completedAt"))).setHeader(Ui.T("Tamamlanma", "Completed"));
        if (canEdit) {
            g.addColumn(new ComponentRenderer<>(r -> {
                Div box = new Div();
                if ("OPEN".equals(r.path("status").asText())) {
                    Button done = new Button(Ui.T("Tamamla", "Complete"), VaadinIcon.CHECK.create(), e -> AssetDialogs.completeMaintenance(asset, r, changed));
                    done.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                    box.add(done);
                }
                return box;
            })).setTextAlign(ColumnTextAlign.END).setAutoWidth(true);
            Button add = new Button(Ui.T("Bakım kaydı ekle", "Add maintenance"), VaadinIcon.PLUS.create(), e -> AssetDialogs.createMaintenance(asset, changed));
            add.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);
            Div bar = new Div(add);
            bar.addClassName("ep-row-end");
            root.add(bar);
        }
        load[0] = () -> Ui.run(() -> g.setItems(Ui.rows(Ui.api().get("/api/assets/" + asset.path("id").asText() + "/maintenances"))));
        load[0].run();
        root.add(g);
        return root;
    }
}
