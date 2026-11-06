package dev.enterprisepulse.web;

import dev.enterprisepulse.assets.AssetCountService;
import dev.enterprisepulse.assets.AssetService;
import dev.enterprisepulse.assets.AssignmentService;
import dev.enterprisepulse.common.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets")
@Tag(name = "Assets")
public class AssetController {
    private final AssetService assets;
    private final AssignmentService assignments;
    private final AssetCountService counts;

    public AssetController(AssetService assets, AssignmentService assignments, AssetCountService counts) {
        this.assets = assets;
        this.assignments = assignments;
        this.counts = counts;
    }

    private static void require(String code) {
        CurrentUser.get().require(code);
    }

    @GetMapping("/assignments/mine")
    public List<AssignmentService.AssignmentDto> mine() {
        return assignments.mine();
    }

    @GetMapping("/assignments/{id}")
    public AssignmentService.AssignmentDocumentDto document(@PathVariable UUID id) {
        return assignments.document(id);
    }

    @PostMapping("/assignments/{id}/acknowledge")
    public AssignmentService.AssignmentDocumentDto acknowledge(@PathVariable UUID id) {
        return assignments.acknowledge(id);
    }

    @PostMapping("/counts")
    public AssetCountService.DetailDto createCount(@RequestBody AssetCountService.CreateRequest body) {
        require("assets:count");
        return counts.create(body);
    }

    @GetMapping("/counts/{id}")
    public AssetCountService.DetailDto count(@PathVariable UUID id) {
        require("assets:read");
        return counts.detail(id);
    }

    @PostMapping("/counts/{id}/scan")
    public AssetCountService.ScanResultDto scan(@PathVariable UUID id, @RequestBody AssetCountService.ScanRequest body) {
        require("assets:count");
        return counts.scan(id, body);
    }

    @PatchMapping("/counts/{id}/lines/{lineId}")
    public AssetCountService.LineDto updateLine(@PathVariable UUID id, @PathVariable UUID lineId, @RequestBody AssetCountService.UpdateLineRequest body) {
        require("assets:count");
        return counts.updateLine(id, lineId, body);
    }

    @DeleteMapping("/counts/{id}/lines/{lineId}")
    public ResponseEntity<Void> removeLine(@PathVariable UUID id, @PathVariable UUID lineId) {
        require("assets:count");
        counts.removeLine(id, lineId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/counts/{id}/complete")
    public AssetCountService.DetailDto complete(@PathVariable UUID id, @RequestBody(required = false) AssetCountService.CompleteRequest body) {
        require("assets:count");
        return counts.complete(id, body);
    }

    @PostMapping("/counts/{id}/cancel")
    public AssetCountService.DetailDto cancel(@PathVariable UUID id) {
        require("assets:count");
        return counts.cancel(id);
    }

    @GetMapping("/{id}")
    public AssetService.AssetDetailDto detail(@PathVariable UUID id) {
        require("assets:read");
        return assets.detail(id);
    }

    @GetMapping("/{id}/label")
    public ResponseEntity<byte[]> label(@PathVariable UUID id) {
        require("assets:read");
        AssetService.Label label = assets.label(id);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(label.fileName()).build().toString())
                .body(label.png());
    }

    @PostMapping("/{id}/retire")
    public AssetService.AssetDetailDto retire(@PathVariable UUID id, @RequestBody AssetService.RetireRequest body) {
        require("assets:update");
        return assets.retire(id, body);
    }

    @GetMapping("/{id}/maintenances")
    public List<AssetService.MaintenanceDto> maintenances(@PathVariable UUID id) {
        require("assets:read");
        return assets.maintenances(id);
    }

    @PostMapping("/{id}/maintenances")
    public AssetService.MaintenanceDto createMaintenance(@PathVariable UUID id, @RequestBody AssetService.CreateMaintenanceRequest body) {
        require("assets:update");
        return assets.createMaintenance(id, body);
    }

    @PostMapping("/{id}/maintenances/{maintenanceId}/complete")
    public AssetService.MaintenanceDto completeMaintenance(@PathVariable UUID id, @PathVariable UUID maintenanceId,
                                                           @RequestBody AssetService.CompleteMaintenanceRequest body) {
        require("assets:update");
        return assets.completeMaintenance(id, maintenanceId, body);
    }

    @GetMapping("/{id}/assignments")
    public List<AssignmentService.AssignmentDto> history(@PathVariable UUID id) {
        require("assets:read");
        return assignments.forAsset(id);
    }

    @PostMapping("/{id}/assign")
    public AssignmentService.AssignmentDto assign(@PathVariable UUID id, @RequestBody AssignmentService.AssignRequest body) {
        require("assets:assign");
        return assignments.assign(id, body);
    }

    @PostMapping("/{id}/return")
    public AssignmentService.AssignmentDto returnAsset(@PathVariable UUID id, @RequestBody AssignmentService.ReturnRequest body) {
        require("assets:assign");
        return assignments.returnAsset(id, body);
    }

    @PostMapping("/{id}/transfer")
    public AssignmentService.AssignmentDto transfer(@PathVariable UUID id, @RequestBody AssignmentService.AssignRequest body) {
        require("assets:assign");
        return assignments.transfer(id, body);
    }
}
