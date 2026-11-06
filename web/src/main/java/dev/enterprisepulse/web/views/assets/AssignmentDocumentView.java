package dev.enterprisepulse.web.views.assets;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import dev.enterprisepulse.web.core.Fmt;
import dev.enterprisepulse.web.core.Session;
import dev.enterprisepulse.web.ui.Ui;
import dev.enterprisepulse.web.views.MainLayout;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Printable hand-over form (zimmet tutanağı); the holder acknowledges receipt here. */
@Route(value = "assets/assignments/:id", layout = MainLayout.class)
public class AssignmentDocumentView extends Div implements BeforeEnterObserver, HasDynamicTitle {
    private String id;
    private String title = "";

    @Override
    public String getPageTitle() {
        return title + " · EnterprisePulse";
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (!Session.current().isAuthenticated()) return;
        id = event.getRouteParameters().get("id").orElse("");
        load();
    }

    private void load() {
        removeAll();
        JsonNode[] doc = new JsonNode[1];
        if (!Ui.run(() -> doc[0] = Ui.api().get("/api/assets/assignments/" + id))) {
            add(Ui.alert(Ui.L("notFound"), "warning"));
            return;
        }
        render(doc[0]);
    }

    private static Div row(String caption, String value) {
        Div d = new Div(new Span(caption), new Span(value));
        d.addClassName("ep-doc-row");
        return d;
    }

    private static Div section(String title, Component... parts) {
        Div d = new Div(new H2(title));
        d.add(parts);
        d.addClassName("ep-doc-section");
        return d;
    }

    private void render(JsonNode doc) {
        JsonNode a = doc.path("assignment"), s = doc.path("asset"), p = doc.path("assignee");
        boolean employee = "EMPLOYEE".equals(a.path("assigneeType").asText());
        title = a.path("number").asText();
        UI.getCurrent().getPage().setTitle(getPageTitle());

        Div toolbar = new Div(new Button(Ui.L("back"), VaadinIcon.ARROW_LEFT.create(), e -> back(a, employee)),
                Ui.badge(AssetUi.enumLabel("AssignmentStatus", a.get("status")), Fmt.statusColor(a.path("status").asText())));
        if (Session.current().has("assets:read")) toolbar.add(new Anchor("r/assets/" + a.path("assetId").asText(), a.path("assetTag").asText()));
        toolbar.add(Ui.grow());
        if (doc.path("canAcknowledge").asBoolean()) {
            Button ack = new Button(Ui.T("Teslim aldım, onaylıyorum", "I acknowledge receipt"), VaadinIcon.CHECK_CIRCLE.create());
            ack.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            ack.addClickListener(e -> {
                ack.setEnabled(false);
                if (Ui.run(() -> Ui.api().post("/api/assets/assignments/" + id + "/acknowledge"), Ui.T("Tutanak onaylandı", "Form acknowledged"))) load();
                else ack.setEnabled(true);
            });
            toolbar.add(ack);
        }
        toolbar.add(new Button(Ui.T("Yazdır / PDF", "Print / PDF"), VaadinIcon.PRINT.create(), e -> UI.getCurrent().getPage().executeJs("window.print()")));
        toolbar.addClassNames("ep-doc-toolbar", "ep-no-print");

        Div companyName = new Div(new Span(doc.path("companyName").asText()));
        companyName.addClassName("ep-doc-company");
        Div company = new Div(companyName);
        if (doc.hasNonNull("companyAddress")) company.add(Ui.muted(doc.get("companyAddress").asText()));
        Span number = new Span(a.path("number").asText());
        number.addClassName("ep-mono");
        Div meta = new Div(new Div(number), new Div(new Span(AssetUi.day(a.get("assignedAt")))));
        meta.getStyle().set("text-align", "right");
        Div head = new Div(company, meta);
        head.addClassName("ep-doc-head");

        Div recipient = section(Ui.T("Teslim alan", "Recipient"),
                row(Ui.T("Zimmet türü", "Assignee type"), AssetUi.enumLabel("AssigneeType", a.get("assigneeType"))),
                row(Ui.T("Adı / Unvanı", "Name"), p.path("name").asText()));
        if (employee) {
            recipient.add(row(Ui.T("Sicil no", "Employee no"), AssetUi.text(p.get("employeeNo"))),
                    row(Ui.T("Departman", "Department"), AssetUi.text(p.get("departmentName"))),
                    row(Ui.T("Pozisyon", "Position"), AssetUi.text(p.get("positionName"))));
        }

        String brandModel = Stream.of(s.path("brand").asText(""), s.path("model").asText("")).filter(x -> !x.isEmpty()).collect(Collectors.joining(" "));
        Div table = new Div();
        table.addClassName("ep-doc-table");
        table.getElement().setProperty("innerHTML", "<table><thead><tr><th>" + esc(Ui.T("Demirbaş no", "Asset tag")) + "</th><th>" + esc(Ui.T("Demirbaş", "Asset"))
                + "</th><th>" + esc(Ui.T("Marka / model", "Brand / model")) + "</th><th>" + esc(Ui.T("Seri no", "Serial no")) + "</th><th>"
                + esc(Ui.T("Durum", "Condition")) + "</th></tr></thead><tbody><tr><td class=\"ep-mono\">" + esc(s.path("assetTag").asText()) + "</td><td>"
                + esc(s.path("name").asText()) + "<div class=\"ep-muted\">" + esc(s.path("categoryName").asText()) + "</div></td><td>"
                + esc(brandModel.isEmpty() ? "—" : brandModel) + "</td><td>" + esc(AssetUi.text(s.get("serialNumber"))) + "</td><td>"
                + esc(AssetUi.enumLabel("AssetCondition", a.get("conditionOut"))) + "</td></tr></tbody></table>");
        Div asset = section(Ui.T("Demirbaş bilgileri", "Asset"), table, row(Ui.T("Beklenen iade", "Expected return"), AssetUi.day(a.get("expectedReturnDate"))));
        if (a.hasNonNull("notes")) asset.add(row(Ui.T("Notlar", "Notes"), a.get("notes").asText()));

        Paragraph statement = new Paragraph(Ui.T(
                "Yukarıda bilgileri yazılı demirbaşı eksiksiz ve çalışır durumda teslim aldım. Demirbaşı özenle ve yalnızca iş amaçlı kullanacağımı, kayıp veya hasar durumunu derhal bildireceğimi, iş ilişkimin sona ermesi ya da talep edilmesi halinde aynı durumda iade edeceğimi kabul ederim.",
                "I have received the asset described above complete and in working order. I agree to use it with care and for business purposes only, to report any loss or damage immediately, and to return it in the same condition when my employment ends or when requested."));
        statement.addClassName("ep-doc-statement");

        Div article = new Div(head, new H1(Ui.T("Demirbaş Zimmet Teslim Tutanağı", "Asset Hand-over Form")), recipient, asset, statement);
        article.addClassName("ep-doc");
        if ("RETURNED".equals(a.path("status").asText())) {
            Div ret = section(Ui.T("İade bilgileri", "Return"), row(Ui.T("İade tarihi", "Returned on"), AssetUi.day(a.get("returnedAt"))),
                    row(Ui.T("Teslim alınan durum", "Condition on return"), AssetUi.enumLabel("AssetCondition", a.get("conditionIn"))),
                    row(Ui.T("İadeyi teslim alan", "Received by"), AssetUi.text(a.get("returnedByName"))));
            if (a.hasNonNull("returnNotes")) ret.add(row(Ui.T("Notlar", "Notes"), a.get("returnNotes").asText()));
            article.add(ret);
        }
        Div stamp = Ui.grow();
        if (a.hasNonNull("acknowledgedAt")) {
            Span st = new Span(VaadinIcon.CHECK_CIRCLE.create(), new Span(" " + Ui.T("Elektronik olarak onaylandı · ", "Acknowledged electronically · ")
                    + AssetUi.stamp(a.get("acknowledgedAt"))));
            st.addClassName("ep-doc-stamp");
            stamp.add(st);
        }
        Div signatures = new Div(sign(Ui.T("Teslim eden", "Delivered by"), a.path("assignedByName").asText(), Ui.grow()),
                sign(Ui.T("Teslim alan", "Received by"), employee ? p.path("name").asText() : "—", stamp));
        signatures.addClassName("ep-doc-signatures");
        article.add(signatures);
        add(toolbar, article);
    }

    private static Div sign(String caption, String name, Div middle) {
        Span b = new Span(caption);
        b.getStyle().set("font-weight", "600");
        Div d = new Div(b, new Span(name), middle, Ui.muted(Ui.T("İmza", "Signature")));
        d.addClassName("ep-doc-sign");
        return d;
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void back(JsonNode a, boolean employee) {
        String mine = Session.current().user().path("employeeId").asText("");
        boolean isMine = employee && a.path("assigneeId").asText().equals(mine);
        if (Session.current().has("assets:read") && !isMine) UI.getCurrent().navigate("r/assets/" + a.path("assetId").asText());
        else UI.getCurrent().navigate("assets/my");
    }
}
