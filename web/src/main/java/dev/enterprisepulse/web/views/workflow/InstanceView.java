package dev.enterprisepulse.web.views.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Meta;
import dev.enterprisepulse.web.core.Meta.ResourceMeta;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.ui.WorkflowView;
import dev.enterprisepulse.web.views.MainLayout;

@Route(value = "workflow/instances/:id", layout = MainLayout.class)
@PageTitle("Onay süreci · EnterprisePulse")
public class InstanceView extends Div implements BeforeEnterObserver {
    private String id;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated() || Meta.get() == null) return;
        id = event.getRouteParameters().get("id").orElse("");
        load();
    }

    private void load() {
        removeAll();
        JsonNode[] instance = new JsonNode[1];
        if (!Ui.run(() -> instance[0] = Ui.api().get("/api/workflow/instances/" + id))) return;
        Div header = Ui.pageHeader(Ui.iconButton(VaadinIcon.ARROW_LEFT, Ui.L("back"), () -> UI.getCurrent().navigate("workflow/inbox")),
                Ui.grow(Ui.title(Ui.L("approvalFlow"))));
        ResourceMeta r = Meta.get().byModel(instance[0].path("entityType").asText());
        if (r != null) {
            String link = "r/" + r.resource() + "/" + instance[0].path("entityId").asText();
            header.add(new Button(Ui.T("Kaydı aç", "Open record"), VaadinIcon.EXTERNAL_LINK.create(), e -> UI.getCurrent().navigate(link)));
        }
        add(header, Ui.card(new WorkflowView((ObjectNode) instance[0], this::load)));
    }
}
