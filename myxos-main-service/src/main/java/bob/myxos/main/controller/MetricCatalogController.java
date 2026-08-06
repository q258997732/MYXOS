package bob.myxos.main.controller;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.main.dto.MetricCatalogUpdateReq;
import bob.myxos.main.service.MetricCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
@RestController @RequestMapping("/api/metric-catalogs") @RequiredArgsConstructor
public class MetricCatalogController {
    private final MetricCatalogService metricCatalogService;
    @GetMapping @PreAuthorize("hasRole('ADMIN')") public Result<List<MetricCatalog>> list(@RequestParam(required = false) String targetType) { return Result.ok(metricCatalogService.list(targetType)); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public Result<MetricCatalog> update(@PathVariable Long id, @Valid @RequestBody MetricCatalogUpdateReq req) { return Result.ok(metricCatalogService.update(id, req)); }
}
