package dev.enterprisepulse.web.views.resources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Meta;
import dev.enterprisepulse.web.core.Meta.ChildMeta;
import dev.enterprisepulse.web.core.Meta.FieldMeta;
import dev.enterprisepulse.web.core.Meta.ResourceMeta;
import dev.enterprisepulse.web.core.ResourceViews;
import dev.enterprisepulse.web.core.ResourceViews.Action;
import dev.enterprisepulse.web.core.ResourceViews.ActionContext;
import dev.enterprisepulse.web.core.ResourceViews.Tab;
import dev.enterprisepulse.web.core.ResourceViews.TabContext;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.ActionRunner;
import dev.enterprisepulse.web.ui.ChildGrid;
import dev.enterprisepulse.web.ui.FormDialog;
import dev.enterprisepulse.web.ui.Lazy;
import dev.enterprisepulse.web.ui.Panels;
import dev.enterprisepulse.web.ui.ResourceForm;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** Record page: details form, child lines, custom tabs, approval flow, files and history. */
@Route(value = "r/:resource/:id", layout = MainLayout.class)
public class ResourceDetailView extends Div implements BeforeEnterObserver, HasDynamicTitle {
    private String resource;
    private String id;
    private ResourceMeta meta;
    private ResourceViews.View view;
    private ObjectNode record;
    private Map<String, Map<String, String>> labels = new HashMap<>();
    private Map<String, String> fixed = new LinkedHashMap<>();
    private boolean editing;
    private int selectedTab;
    private String heading = "";

    @Override
    public String getPageTitle() {
        return heading + " · EnterprisePulse";
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated() || Meta.get() == null) return;
        resource = event.getRouteParameters().get("resource").orElse("");
        id = event.getRouteParameters().get("id").orElse("");
        meta = Meta.get().get(resource);
        view = ResourceViews.of(resource);
        editing = false;
        selectedTab = 0;
        removeAll();
        if (meta == null) {
            add(Ui.alert(Ui.L("notFound"), "warning"));
            return;
        }
        if (isNew()) {
            fixed = ResourceListView.query(event);
            record = FormDialog.defaults(meta);
            fixed.forEach(record::put);
            labels = new HashMap<>();
            render();
            return;
        }
        reload();
    }

    private boolean isNew() {
        return "new".equals(id);
    }

    private void reload() {
        boolean ok = Ui.run(() -> {
            JsonNode res = Ui.api().get("/api/r/" + resource + "/" + ApiClient.enc(id));
            record = (ObjectNode) res.get("data");
            labels = Ui.labels(res.get("labels"));
        });
        if (ok) render();
    }

    private boolean isDraft() {
        String status = Fmt.raw(record.get("status"));
        return status == null || status.equals("DRAFT");
    }

    private boolean allowed(String action) {
        return Session.current().has(meta.permissionFor(action, Meta.get().registry()));
    }

    private boolean canEdit() {
        return !meta.readOnly() && !view.hideEdit && allowed("update") && (!meta.draftLocked() || isDraft());
    }

    private boolean canDelete() {
        return !meta.readOnly() && !view.hideDelete && allowed("delete") && (!meta.draftLocked() || isDraft());
    }

    private String computeHeading() {
        if (isNew()) return Ui.L("new");
        String text = meta.labelFields().stream().map(f -> Fmt.raw(record.get(f))).filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.joining(" "));
        return text.isEmpty() ? id : text;
    }

    private void render() {
        removeAll();
        heading = computeHeading();
        UI.getCurrent().getPage().setTitle(getPageTitle());

        Div titleBox = new Div(Ui.muted(Ui.T(meta.label())));
        Div titleRow = new Div(Ui.title(heading));
        titleRow.addClassName("ep-row");
        FieldMeta statusField = meta.field("status");
        String status = Fmt.raw(record.get("status"));
        if (status != null && statusField != null && statusField.kind().equals("enum")) {
            titleRow.add(Ui.badge(Meta.get().enumLabel(statusField.enumName(), status, Ui.locale()), Fmt.statusColor(status)));
        }
        titleBox.add(titleRow);
        titleBox.addClassName("ep-grow");
        Div header = Ui.pageHeader(Ui.iconButton(VaadinIcon.ARROW_LEFT, Ui.L("back"), this::back), titleBox);
        header.addClassName("ep-no-print");
        if (!isNew()) {
            for (Action a : view.actions) {
                if (!a.visible.test(record) || (a.permission != null && !Session.current().has(a.permission))) continue;
                Button b = new Button(a.text(Ui.locale()), a.icon.create(), e -> ActionRunner.run(a, new ActionContext(record, this::reload)));
                b.addThemeVariants(ButtonVariant.LUMO_SMALL);
                if ("error".equals(a.theme)) b.addThemeVariants(ButtonVariant.LUMO_ERROR);
                if ("success".equals(a.theme)) b.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
                header.add(b);
            }
            if (canEdit() && !editing) {
                Button edit = new Button(Ui.L("edit"), VaadinIcon.EDIT.create(), e -> {
                    editing = true;
                    selectedTab = 0;
                    render();
                });
                edit.addThemeVariants(ButtonVariant.LUMO_SMALL);
                header.add(edit);
            }
            if (canDelete()) {
                Button delete = new Button(VaadinIcon.TRASH.create(), e -> delete());
                delete.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
                delete.setTooltipText(Ui.L("delete"));
                header.add(delete);
            }
        }
        add(header);
        if (!isNew() && meta.draftLocked() && !isDraft()) {
            add(Ui.alert(Ui.T("Bu belge taslak durumunda olmadığı için satırları ve alanları değiştirilemez.",
                    "This document is no longer a draft, its fields and lines are locked."), "info"));
        }

        TabSheet tabs = new TabSheet();
        tabs.addClassName("ep-tabs");
        tabs.setWidthFull();
        Div details = new Div(new ResourceForm(meta, record, labels, isNew(), !isNew() && !editing, !isNew() && !editing, fixed.keySet(), 4, null));
        if (isNew() || editing) {
            Button cancel = new Button(Ui.L("cancel"), e -> cancelEdit());
            Button save = new Button(Ui.L("save"), VaadinIcon.CHECK.create());
            save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            save.addClickListener(e -> save(save));
            Div buttons = new Div(cancel, save);
            buttons.addClassName("ep-row-end");
            details.add(buttons);
        }
        tabs.add(Ui.L("details"), details);
        if (!isNew()) {
            for (ChildMeta child : meta.children()) {
                ResourceMeta cm = Meta.get().get(child.resource());
                if (view.hiddenChildren.contains(child.resource()) || cm == null || !Session.current().has(cm.permissionFor("read", Meta.get().registry()))) continue;
                tabs.add(Ui.T(cm.label()), new Lazy(() -> new ChildGrid(child, id, canEdit(), this::reloadKeepTab)));
            }
            for (Tab tab : view.tabs) {
                if (tab.permission() != null && !Session.current().has(tab.permission())) continue;
                tabs.add(Ui.T(tab.title(), tab.titleEn()), new Lazy(() -> tab.factory().apply(new TabContext(record, meta, this::reloadKeepTab))));
            }
            if (view.workflowEntity != null) {
                tabs.add(Ui.L("approvalFlow"), new Lazy(() -> new Panels.WorkflowPanel(view.workflowEntity, id, this::reloadKeepTab)));
            }
            if (meta.tenant() && meta.field("id") != null && Session.current().has("files:read")) {
                tabs.add(Ui.L("files"), new Lazy(() -> new Panels.FilesPanel(meta.model(), id)));
            }
            if (Session.current().has("audit:read") && meta.field("id") != null) {
                tabs.add(Ui.L("history"), new Lazy(() -> new Panels.HistoryPanel(meta.model(), id)));
            }
        }
        if (selectedTab > 0 && selectedTab < tabs.getElement().getChildCount()) tabs.setSelectedIndex(selectedTab);
        tabs.addSelectedChangeListener(e -> selectedTab = tabs.getSelectedIndex());
        add(tabs);
    }

    private void reloadKeepTab() {
        reload();
    }

    private void save(Button save) {
        save.setEnabled(false);
        Ui.run(() -> {
            ObjectNode body = FormDialog.payload(meta, record);
            if (isNew()) {
                JsonNode res = Ui.api().post("/api/r/" + resource, body);
                String key = res.path("data").path(meta.keyField()).asText();
                UI.getCurrent().navigate("r/" + resource + "/" + ApiClient.enc(key));
            } else {
                Ui.api().patch("/api/r/" + resource + "/" + ApiClient.enc(id), body);
                editing = false;
                reload();
            }
        }, Ui.L("saved"));
        save.setEnabled(true);
    }

    private void cancelEdit() {
        if (isNew()) {
            back();
            return;
        }
        editing = false;
        reload();
    }

    private void delete() {
        Ui.confirm(Ui.L("confirmDelete"), () -> {
            if (Ui.run(() -> Ui.api().delete("/api/r/" + resource + "/" + ApiClient.enc(id)), Ui.L("deleted"))) back();
        });
    }

    private void back() {
        if (meta.parent() != null && record != null && Fmt.raw(record.get(meta.parent().field())) != null) {
            UI.getCurrent().navigate("r/" + meta.parent().resource() + "/" + record.get(meta.parent().field()).asText());
            return;
        }
        UI.getCurrent().navigate("r/" + resource);
    }
}
