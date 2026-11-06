package dev.enterprisepulse.web.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.component.combobox.ComboBox;
import dev.enterprisepulse.web.core.ApiClient;
import dev.enterprisepulse.web.core.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/** Searchable picker over /api/r/{resource}/lookup. */
public class RefPicker extends ComboBox<RefPicker.Item> {

    public static final class Item {
        public final String id;
        public final String label;

        public Item(String id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Item i && Objects.equals(i.id, id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }

    private final String resource;
    private Map<String, String> filters = Map.of();

    public RefPicker(String resource, String label) {
        super(label);
        this.resource = resource;
        setItemLabelGenerator(i -> i.label);
        setClearButtonVisible(true);
        setItems(query -> {
            int offset = query.getOffset();
            int limit = query.getLimit();
            return offset > 0 ? Stream.empty() : search(query.getFilter().orElse("")).stream().limit(limit);
        });
    }

    public void setFilters(Map<String, String> filters) {
        this.filters = filters == null ? Map.of() : filters;
    }

    public static List<Item> lookup(String resource, String query) {
        List<Item> items = new ArrayList<>();
        JsonNode res = Ui.api().get("/api/r/" + resource + "/lookup?" + query);
        if (res != null) for (JsonNode n : res) items.add(new Item(n.path("id").asText(), n.path("label").asText()));
        return items;
    }

    private List<Item> search(String text) {
        StringBuilder q = new StringBuilder("take=50&search=" + ApiClient.enc(text));
        filters.forEach((k, v) -> q.append('&').append(k).append('=').append(ApiClient.enc(v)));
        try {
            return lookup(resource, q.toString());
        } catch (ApiException ex) {
            return List.of();
        }
    }

    public String getRefId() {
        Item v = getValue();
        return v == null ? null : v.id;
    }

    /** Sets the selection by id; the label is looked up when not given. */
    public void setRefId(String id, String knownLabel) {
        if (id == null || id.isEmpty()) {
            clear();
            return;
        }
        String label = knownLabel;
        if (label == null) {
            try {
                List<Item> found = lookup(resource, "ids=" + ApiClient.enc(id));
                label = found.isEmpty() ? id : found.get(0).label;
            } catch (ApiException ex) {
                label = id;
            }
        }
        setValue(new Item(id, label));
    }
}
