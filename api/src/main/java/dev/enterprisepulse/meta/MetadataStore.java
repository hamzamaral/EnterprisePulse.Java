package dev.enterprisepulse.meta;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.enterprisepulse.common.AppException;
import dev.enterprisepulse.meta.Meta.Document;
import dev.enterprisepulse.meta.Meta.FieldMeta;
import dev.enterprisepulse.meta.Meta.ResourceMeta;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class MetadataStore {
    private static final String ENTITY_PACKAGE = "dev.enterprisepulse.data.generated.";

    private final Document document;
    private final String rawJson;
    private final Map<String, ResourceMeta> resources = new LinkedHashMap<>();
    private final Map<Class<?>, ResourceMeta> byType = new LinkedHashMap<>();
    private final Map<String, List<String>> registry;

    public MetadataStore() {
        try (InputStream in = MetadataStore.class.getResourceAsStream("/metadata.json")) {
            if (in == null) throw new IllegalStateException("metadata.json is missing");
            rawJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            document = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).readValue(rawJson, Document.class);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (ResourceMeta r : document.resources) {
            try {
                r.type = Class.forName(ENTITY_PACKAGE + r.model);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Entity type " + r.model + " not found", e);
            }
            for (FieldMeta f : r.fields) f.field = javaField(r.type, f.name);
            r.index();
            resources.put(r.resource, r);
            byType.put(r.type, r);
        }
        registry = document.permissions.stream()
                .collect(Collectors.toMap(p -> p.module, p -> p.actions.stream().map(a -> a.action).toList(), (a, b) -> a, LinkedHashMap::new));
    }

    private static Field javaField(Class<?> type, String name) {
        for (String candidate : List.of(name, name + "_")) {
            try {
                return type.getField(candidate);
            } catch (NoSuchFieldException ignored) {
                // try the keyword-escaped name
            }
        }
        throw new IllegalStateException(type.getSimpleName() + "." + name + " not found");
    }

    public Document document() {
        return document;
    }

    public String rawJson() {
        return rawJson;
    }

    public Collection<ResourceMeta> resources() {
        return resources.values();
    }

    public boolean has(String resource) {
        return resources.containsKey(resource);
    }

    public ResourceMeta get(String resource) {
        ResourceMeta r = resources.get(resource);
        if (r == null) throw AppException.notFound("Bilinmeyen kaynak: " + resource);
        return r;
    }

    public ResourceMeta of(Class<?> type) {
        return byType.get(type);
    }

    public Map<String, List<String>> registry() {
        return registry;
    }

    public List<String> allPermissionCodes() {
        return document.permissions.stream().flatMap(p -> p.actions.stream().map(a -> a.code)).toList();
    }
}
