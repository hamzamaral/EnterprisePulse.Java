package dev.enterprisepulse.web;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.enterprisepulse.common.CurrentUser;
import dev.enterprisepulse.data.Identified;
import dev.enterprisepulse.meta.MetadataStore;
import dev.enterprisepulse.resource.ExportService;
import dev.enterprisepulse.resource.ListQuery;
import dev.enterprisepulse.resource.ResourceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Generic metadata-driven CRUD for every resource: /api/r/{resource}. */
@RestController
@RequestMapping("/api/r/{resource}")
@Tag(name = "Resources")
public class ResourceController {
    private final ResourceService svc;
    private final ExportService export;
    private final MetadataStore meta;

    public ResourceController(ResourceService svc, ExportService export, MetadataStore meta) {
        this.svc = svc;
        this.export = export;
        this.meta = meta;
    }

    private void require(String resource, String action) {
        CurrentUser.get().require(meta.get(resource).permissionFor(action, meta.registry()));
    }

    @GetMapping
    public ResourceService.ListResult list(@PathVariable String resource, @RequestParam MultiValueMap<String, String> params) {
        require(resource, "read");
        return svc.list(resource, ListQuery.from(params));
    }

    @GetMapping("/lookup")
    public List<ResourceService.LookupItem> lookup(@PathVariable String resource, @RequestParam MultiValueMap<String, String> params) {
        ListQuery q = ListQuery.from(params);
        String ids = params.getFirst("ids");
        int take = 20;
        try {
            if (params.getFirst("take") != null) take = Math.max(1, Math.min(200, Integer.parseInt(params.getFirst("take"))));
        } catch (NumberFormatException ignored) {
            // default page size
        }
        List<String> idList = ids == null || ids.isBlank() ? null : Arrays.stream(ids.split(",")).filter(s -> !s.isBlank()).toList();
        return svc.lookup(resource, q.search, idList, take, q.filters);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@PathVariable String resource, @RequestParam MultiValueMap<String, String> params) {
        require(resource, "read");
        var all = svc.all(resource, ListQuery.from(params));
        var file = export.export(all.meta(), all.rows(), all.labels(), "pdf".equals(params.getFirst("format")) ? "pdf" : "xlsx", CurrentUser.get().locale);
        return file(file);
    }

    static ResponseEntity<byte[]> file(ExportService.ExportFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(file.content());
    }

    @GetMapping("/{id}")
    public ResourceService.OneResult get(@PathVariable String resource, @PathVariable String id) {
        require(resource, "read");
        return svc.get(resource, id);
    }

    @PostMapping
    public ResourceService.OneResult create(@PathVariable String resource, @RequestBody ObjectNode body) {
        require(resource, "create");
        Object entity = svc.create(resource, body);
        if (entity instanceof Identified e) return svc.get(resource, e.getId().toString());
        return new ResourceService.OneResult(entity, Map.of());
    }

    @PatchMapping("/{id}")
    public ResourceService.OneResult update(@PathVariable String resource, @PathVariable String id, @RequestBody ObjectNode body) {
        require(resource, "update");
        svc.update(resource, id, body);
        return svc.get(resource, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String resource, @PathVariable String id) {
        require(resource, "delete");
        svc.delete(resource, id);
        return ResponseEntity.noContent().build();
    }
}
