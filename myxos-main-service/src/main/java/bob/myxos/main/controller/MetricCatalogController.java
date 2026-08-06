package bob.myxos.main.controller;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.main.service.MetricTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
@RestController @RequestMapping("/api/metric-catalogs") @RequiredArgsConstructor
public class MetricCatalogController {
    private final MetricTemplateService metricTemplateService;
    @GetMapping @PreAuthorize("hasRole('ADMIN')") public Result<List<MetricCatalog>> list(@RequestParam(required = false) String targetType) { return Result.ok(metricTemplateService.listCatalogs(targetType)); }
}
