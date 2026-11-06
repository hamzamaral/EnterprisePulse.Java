package dev.enterprisepulse.web.views.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Meta;
import dev.enterprisepulse.web.core.Meta.PermAction;
import dev.enterprisepulse.web.core.Meta.PermModule;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Roles list and the module × action permission matrix. */
@Route(value = "roles", layout = MainLayout.class)
@PageTitle("Roller · EnterprisePulse")
public class RolesView extends Div implements BeforeEnterObserver {

    private static final class Role {
        String id;
        String code = "";
        String name = "";
        String description;
        boolean system;
        Set<String> permissions = new LinkedHashSet<>();
        int userCount;
    }

    private List<Role> roles = new ArrayList<>();
    private Role selected;
    private final Div list = new Div();
    private final Div editor = new Div();

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated() || Meta.get() == null) return;
        removeAll();
        Div header = Ui.pageHeader(Ui.grow(Ui.title(Ui.T("Roller ve İzinler", "Roles & permissions"))));
        if (Session.current().has("roles:create")) {
            Button add = new Button(Ui.L("new"), VaadinIcon.PLUS.create(), e -> {
                selected = new Role();
                renderList();
                renderEditor();
            });
            add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            header.add(add);
        }
        list.addClassNames("ep-card", "ep-role-list");
        editor.addClassNames("ep-card", "ep-role-editor");
        Div body = new Div(list, editor);
        body.addClassName("ep-split");
        add(header, body);
        load(null);
    }

    private void load(String select) {
        Ui.run(() -> {
            List<Role> result = new ArrayList<>();
            for (JsonNode r : Ui.api().get("/api/roles/details")) {
                Role role = new Role();
                role.id = r.path("id").asText();
                role.code = r.path("code").asText();
                role.name = r.path("name").asText();
                role.description = r.path("description").asText(null);
                role.system = r.path("isSystem").asBoolean();
                r.path("permissions").forEach(p -> role.permissions.add(p.asText()));
                role.userCount = r.path("userCount").asInt();
                result.add(role);
            }
            roles = result;
        });
        String code = select != null ? select : selected != null ? selected.code : null;
        selected = roles.stream().filter(r -> r.code.equals(code)).findFirst().orElse(roles.isEmpty() ? null : roles.get(0));
        renderList();
        renderEditor();
    }

    private void renderList() {
        list.removeAll();
        for (Role role : roles) {
            Span name = new Span(role.name);
            name.addClassName("ep-strong");
            Div item = new Div((role.system ? VaadinIcon.LOCK : VaadinIcon.SHIELD).create(),
                    new Div(new Div(name), Ui.muted(role.code + " · " + role.userCount + " " + Ui.T("kullanıcı", "users") + " · "
                            + role.permissions.size() + " " + Ui.T("izin", "perms"))));
            item.addClassName("ep-list-item");
            if (role == selected) item.addClassName("selected");
            item.getElement().addEventListener("click", e -> {
                selected = role;
                renderList();
                renderEditor();
            });
            list.add(item);
        }
    }

    private boolean readOnly() {
        return selected == null || selected.system || !Session.current().has(selected.id == null ? "roles:create" : "roles:update");
    }

    private void renderEditor() {
        editor.removeAll();
        if (selected == null) return;
        Role role = selected;
        boolean ro = readOnly();
        TextField code = new TextField(Ui.T("Kod", "Code"), role.code, e -> role.code = e.getValue());
        code.setEnabled(!role.system && role.id == null);
        TextField name = new TextField(Ui.T("Ad", "Name"), role.name, e -> role.name = e.getValue());
        name.setEnabled(!role.system);
        TextField description = new TextField(Ui.T("Açıklama", "Description"), role.description == null ? "" : role.description, e -> role.description = e.getValue());
        description.setEnabled(!role.system);
        FormLayout form = new FormLayout(code, name, description);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("700px", 3));
        editor.add(form);
        if (role.system) {
            editor.add(Ui.alert(Ui.T("Sistem rolü tüm izinlere sahiptir ve değiştirilemez.", "The system role holds every permission and cannot be changed."), "info"));
        }
        Div matrix = new Div();
        matrix.addClassName("ep-matrix");
        Map<String, List<Checkbox>> boxes = new HashMap<>();
        for (PermModule module : Meta.get().permissions) {
            Checkbox all = new Checkbox(Ui.T(module.label()));
            all.addClassName("ep-strong");
            all.setEnabled(!ro);
            List<Checkbox> actions = new ArrayList<>();
            Div actionsBox = new Div();
            actionsBox.addClassName("ep-row-wrap");
            for (PermAction a : module.actions()) {
                Checkbox cb = new Checkbox(Ui.T(a.label()), role.permissions.contains(a.code()));
                cb.setEnabled(!ro);
                cb.addValueChangeListener(e -> {
                    if (e.getValue()) role.permissions.add(a.code());
                    else role.permissions.remove(a.code());
                    if (e.isFromClient()) syncModule(all, actions);
                });
                actions.add(cb);
                actionsBox.add(cb);
            }
            syncModule(all, actions);
            all.addValueChangeListener(e -> {
                if (!e.isFromClient()) return;
                for (Checkbox cb : actions) cb.setValue(e.getValue());
                all.setIndeterminate(false);
            });
            boxes.put(module.module(), actions);
            Div row = new Div(all, actionsBox);
            row.addClassName("ep-matrix-row");
            matrix.add(row);
        }
        editor.add(matrix);
        Div buttons = new Div();
        buttons.addClassName("ep-row-end");
        if (role.id != null && !role.system && Session.current().has("roles:delete")) {
            Button delete = new Button(Ui.L("delete"), VaadinIcon.TRASH.create(), e -> Ui.confirm(Ui.L("confirmDelete"), () -> {
                if (Ui.run(() -> Ui.api().delete("/api/roles/details/" + role.id), Ui.L("deleted"))) {
                    selected = null;
                    load(null);
                }
            }));
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            buttons.add(delete);
        }
        Button save = new Button(Ui.L("save"), VaadinIcon.CHECK.create(), e -> save(role));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.setEnabled(!ro);
        buttons.add(save);
        editor.add(buttons);
    }

    private static void syncModule(Checkbox all, List<Checkbox> actions) {
        long on = actions.stream().filter(Checkbox::getValue).count();
        all.setValue(on == actions.size() && on > 0);
        all.setIndeterminate(on > 0 && on < actions.size());
    }

    private void save(Role role) {
        var body = ApiClient.obj("code", role.code, "name", role.name, "description", role.description, "permissions", new ArrayList<>(role.permissions));
        Ui.run(() -> {
            if (role.id == null) Ui.api().post("/api/roles/details", body);
            else Ui.api().put("/api/roles/details/" + role.id, body);
            load(role.code);
        }, Ui.L("saved"));
    }
}
