package dev.enterprisepulse.web.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import java.util.function.Supplier;

/** Builds its content on first attach, so unselected tabs don't call the API. */
public class Lazy extends Div {
    private final Supplier<Component> content;
    private boolean built;

    public Lazy(Supplier<Component> content) {
        this.content = content;
    }

    @Override
    protected void onAttach(AttachEvent event) {
        if (built) return;
        built = true;
        add(content.get());
    }
}
