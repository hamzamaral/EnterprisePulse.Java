package dev.enterprisepulse.web.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.Fmt;

/** A workflow instance: status, approve/reject/withdraw, step timeline and action history. */
public class WorkflowView extends Div {

    public WorkflowView(ObjectNode instance, Runnable onChanged) {
        addClassName("ep-workflow");
        String status = instance.path("status").asText();
        String id = instance.path("id").asText();

        Div left = new Div();
        left.addClassName("ep-workflow-left");
        Span title = new Span(instance.path("title").asText());
        title.addClassName("ep-strong");
        left.add(new Div(title), new Div(new Span(instance.path("definitionName").asText())));
        Div meta = new Div(Ui.badge(Ui.meta().enumLabel("WorkflowStatus", status, Ui.locale()), Fmt.statusColor(status)),
                Ui.muted(" " + Ui.L("startedBy") + ": " + instance.path("startedByName").asText() + " · " + Fmt.dateTime(instance.get("startedAt"))));
        meta.addClassName("ep-row");
        left.add(meta);

        Div buttons = new Div();
        buttons.addClassName("ep-row");
        if (instance.path("canAct").asBoolean()) {
            Button approve = new Button(Ui.L("approve"), VaadinIcon.CHECK.create(), e -> PromptDialog.ask(Ui.L("approve"), Ui.L("comment"), false, null,
                    Ui.L("approve"), "success", c -> act(id, "approve", c, Ui.T("Onaylandı", "Approved"), onChanged)));
            approve.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            Button reject = new Button(Ui.L("reject"), VaadinIcon.CLOSE.create(), e -> PromptDialog.ask(Ui.L("reject"), Ui.L("rejectReason"), true, null,
                    Ui.L("reject"), "error", c -> act(id, "reject", c, Ui.T("Reddedildi", "Rejected"), onChanged)));
            reject.addThemeVariants(ButtonVariant.LUMO_ERROR);
            buttons.add(approve, reject);
        }
        if (status.equals("PENDING") && instance.path("startedById").asText().equals(Ui.session().userId())) {
            Button withdraw = new Button(Ui.L("withdraw"), VaadinIcon.ARROW_BACKWARD.create(), e -> PromptDialog.ask(Ui.L("withdraw"), Ui.L("comment"), false,
                    null, Ui.L("withdraw"), "contrast", c -> act(id, "cancel", c, Ui.L("saved"), onChanged)));
            withdraw.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            buttons.add(withdraw);
        }
        left.add(buttons);

        Div timeline = new Div();
        timeline.addClassName("ep-timeline");
        int current = instance.path("currentStepOrder").asInt();
        for (JsonNode step : instance.path("steps")) {
            int order = step.path("order").asInt();
            String color = status.equals("APPROVED") || order < current ? "success"
                    : order == current && status.equals("PENDING") ? "warning"
                    : order == current && status.equals("REJECTED") ? "error" : "contrast";
            Div item = new Div();
            item.addClassNames("ep-timeline-item", color);
            Span name = new Span(Ui.L("step") + " " + order + ": " + step.path("name").asText());
            name.addClassName("ep-strong");
            item.add(new Div(name), new Div(Ui.muted(step.path("approverName").asText(""))));
            timeline.add(item);
        }
        left.add(timeline);

        Div right = new Div();
        right.addClassName("ep-workflow-right");
        right.add(new H4(Ui.L("history")));
        Div table = new Div();
        table.addClassName("ep-simple-table");
        for (JsonNode a : instance.path("actions")) {
            String type = a.path("type").asText();
            String color = switch (type) {
                case "APPROVE" -> "success";
                case "REJECT" -> "error";
                case "CANCEL" -> "warning";
                default -> "info";
            };
            Div row = new Div(new Span(Fmt.dateTime(a.get("createdAt"))),
                    Ui.badge(Ui.meta().enumLabel("WorkflowActionType", type, Ui.locale()), color),
                    new Span(a.path("userName").asText("")), new Span(a.path("comment").asText("")));
            row.addClassName("ep-simple-row");
            table.add(row);
        }
        right.add(table);
        add(left, right);
    }

    private static void act(String id, String action, String comment, String success, Runnable onChanged) {
        if (Ui.run(() -> Ui.api().post("/api/workflow/instances/" + id + "/" + action, ApiClient.obj("comment", comment)), success)) onChanged.run();
    }
}
