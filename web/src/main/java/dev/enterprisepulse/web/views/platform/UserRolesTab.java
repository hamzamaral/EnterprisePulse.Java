package dev.enterprisepulse.web.views.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.ResourceViews.TabContext;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.RefPicker;
import dev.enterprisepulse.web.ui.Ui;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Roles and default branch of a user in the active company. */
public class UserRolesTab extends Div {

    public UserRolesTab(TabContext ctx) {
        addClassName("ep-stack");
        getStyle().set("max-width", "560px");
        String userId = ctx.record().path("id").asText();
        List<RefPicker.Item> roleItems = UserCreateDialog.roles();
        List<RefPicker.Item> branchItems = UserCreateDialog.branches();
        MultiSelectComboBox<RefPicker.Item> roles = new MultiSelectComboBox<>(Ui.L("roles"), roleItems);
        roles.setItemLabelGenerator(i -> i.label);
        ComboBox<RefPicker.Item> branch = new ComboBox<>(Ui.L("branch"), branchItems);
        branch.setItemLabelGenerator(i -> i.label);
        branch.setClearButtonVisible(true);
        Ui.run(() -> {
            Set<String> assigned = new HashSet<>();
            for (JsonNode id : Ui.api().get("/api/users/" + userId + "/roles")) assigned.add(id.asText());
            roles.setValue(roleItems.stream().filter(r -> assigned.contains(r.id)).toList());
            JsonNode m = Ui.api().get("/api/r/user-companies?userId=" + userId + "&companyId=" + Session.current().companyId).path("data");
            String branchId = m.size() > 0 ? m.get(0).path("defaultBranchId").asText(null) : null;
            branchItems.stream().filter(b -> b.id.equals(branchId)).findFirst().ifPresent(branch::setValue);
        });
        Button save = new Button(Ui.L("save"), e -> Ui.run(() -> Ui.api().put("/api/users/" + userId + "/roles", ApiClient.obj(
                "roleIds", roles.getValue().stream().map(i -> i.id).toList(),
                "branchId", branch.getValue() == null ? null : branch.getValue().id)), Ui.L("saved")));
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Div buttons = new Div(save);
        buttons.addClassName("ep-row");
        if (Session.current().has("users:delete")) {
            Button remove = new Button(Ui.T("Şirketten çıkar", "Remove from company"), e -> Ui.confirm(
                    Ui.T("Kullanıcı bu şirketten çıkarılsın mı?", "Remove the user from this company?"), () -> {
                        if (Ui.run(() -> Ui.api().delete("/api/users/" + userId + "/membership"), Ui.L("saved"))) UI.getCurrent().navigate("r/users");
                    }));
            remove.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            buttons.add(remove);
        }
        add(new Paragraph(Ui.T("Bu şirketteki rolleri ve varsayılan şube", "Roles and default branch in this company")), roles, branch, buttons);
        roles.setWidthFull();
        branch.setWidthFull();
    }
}
