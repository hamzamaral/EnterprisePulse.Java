package dev.enterprisepulse.web.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import java.util.function.Consumer;

/** Asks for an optional comment (or just a confirmation); calls back with the text ("" when no input is asked). */
public final class PromptDialog {
    private PromptDialog() {}

    public static void ask(String title, String label, boolean required, String message, String okText, String theme, Consumer<String> onOk) {
        Dialog d = new Dialog();
        d.setHeaderTitle(title);
        d.setWidth("min(520px, 96vw)");
        if (message != null) d.add(new Paragraph(message));
        TextArea input = new TextArea(label);
        input.setWidthFull();
        input.setMinHeight("100px");
        input.setValueChangeMode(ValueChangeMode.EAGER);
        if (label != null) {
            d.add(input);
            input.focus();
        }
        Button ok = new Button(okText != null ? okText : Ui.L("save"));
        ok.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if ("success".equals(theme)) ok.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        if ("error".equals(theme)) ok.addThemeVariants(ButtonVariant.LUMO_ERROR);
        if ("contrast".equals(theme)) ok.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        ok.setEnabled(!(label != null && required));
        input.addValueChangeListener(e -> ok.setEnabled(!required || !e.getValue().isBlank()));
        ok.addClickListener(e -> {
            d.close();
            onOk.accept(label != null ? input.getValue() : "");
        });
        d.getFooter().add(new Button(Ui.L("cancel"), e -> d.close()), ok);
        d.open();
    }
}
