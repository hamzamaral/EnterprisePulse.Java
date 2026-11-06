package dev.enterprisepulse.web.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.enterprisepulse.web.core.Meta.ResourceMeta;
import dev.enterprisepulse.web.views.platform.CompanyCreateDialog;
import dev.enterprisepulse.web.views.platform.UserCreateDialog;
import dev.enterprisepulse.web.views.platform.UserRolesTab;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Per-resource tweaks over the generic metadata screens; business phases register theirs here. */
public final class ResourceViews {
    private ResourceViews() {}

    public record ActionContext(ObjectNode record, Runnable reload) {
        public String id() {
            return record.path("id").asText();
        }
    }

    public record TabContext(ObjectNode record, ResourceMeta meta, Runnable onChanged) {}

    public record Tab(String title, String titleEn, String permission, Function<TabContext, Component> factory) {}

    /** A button on a record (submit, approve, post, print...). Default run: POST endpoint with {comment}. */
    public static final class Action {
        public final String label;
        public final String labelEn;
        public VaadinIcon icon = VaadinIcon.PLAY;
        public String theme = "primary";
        public String permission;
        public Predicate<ObjectNode> visible = r -> true;
        public String endpoint;
        public String prompt;
        public boolean promptRequired;
        public String confirm;
        public boolean download;
        public Consumer<ActionContext> run;

        public Action(String label, String labelEn) {
            this.label = label;
            this.labelEn = labelEn;
        }

        public String text(String locale) {
            return "en".equals(locale) && labelEn != null && !labelEn.isEmpty() ? labelEn : label;
        }

        public Action icon(VaadinIcon v) { icon = v; return this; }
        public Action theme(String v) { theme = v; return this; }
        public Action permission(String v) { permission = v; return this; }
        public Action visible(Predicate<ObjectNode> v) { visible = v; return this; }
        public Action endpoint(String v) { endpoint = v; return this; }
        public Action prompt(String v, boolean required) { prompt = v; promptRequired = required; return this; }
        public Action confirm(String v) { confirm = v; return this; }
        public Action download() { download = true; return this; }
        public Action run(Consumer<ActionContext> v) { run = v; return this; }

        public static Predicate<ObjectNode> statusIn(String... statuses) {
            Set<String> set = Set.of(statuses);
            return r -> set.contains(r.path("status").asText());
        }
    }

    public static final class View {
        public List<String> columns;
        public Set<String> hiddenFields = Set.of();
        public Set<String> readOnlyFields = Set.of();
        public List<String> filterFields;
        public boolean hideCreate;
        public boolean hideEdit;
        public boolean hideDelete;
        public String createHref;
        /** Opens a custom create dialog; the argument is called after a successful save. */
        public Consumer<Runnable> createDialog;
        public final List<Action> actions = new ArrayList<>();
        public final List<Tab> tabs = new ArrayList<>();
        public Set<String> hiddenChildren = Set.of();
        public String workflowEntity;
    }

    private static final Map<String, View> MAP = new HashMap<>();
    private static final View EMPTY = new View();

    public static View of(String resource) {
        return MAP.getOrDefault(resource, EMPTY);
    }

    public static View configure(String resource, Consumer<View> configure) {
        View view = MAP.computeIfAbsent(resource, k -> new View());
        configure.accept(view);
        return view;
    }

    static {
        platform();
    }

    private static void platform() {
        configure("users", v -> {
            v.columns = List.of("firstName", "lastName", "email", "phone", "isActive", "isSuperAdmin", "lastLoginAt");
            v.createDialog = UserCreateDialog::open;
            v.hiddenFields = Set.of("failedLoginCount", "lockedUntil", "twoFactorEnabled", "avatarUrl");
            v.tabs.add(new Tab("Roller", "Roles", "users:update", UserRolesTab::new));
            v.hiddenChildren = Set.of("user-roles");
        });
        configure("roles", v -> v.createHref = "roles");
        configure("companies", v -> v.createDialog = CompanyCreateDialog::open);
        configure("audit-logs", v -> {
            v.columns = List.of("createdAt", "userEmail", "action", "entityType", "entityId", "ip");
            v.filterFields = List.of("action", "entityType", "userEmail");
        });
        configure("workflow-instances", v -> {
            v.columns = List.of("title", "definitionId", "entityType", "status", "currentStepOrder", "startedById", "startedAt");
            v.actions.add(new Action("Süreci aç", "Open").icon(VaadinIcon.EXTERNAL_LINK)
                    .run(c -> UI.getCurrent().navigate("workflow/instances/" + c.id())));
        });
        configure("number-sequences", v -> {
            v.hideCreate = true;
            v.hideDelete = true;
        });
    }
}
