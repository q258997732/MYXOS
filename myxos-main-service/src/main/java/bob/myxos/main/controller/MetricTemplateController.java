package bob.myxos.main.controller;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.MetricTemplate;
import bob.myxos.domain.entity.MetricTemplateItem;
import bob.myxos.main.dto.MetricTemplateReq;
import bob.myxos.main.service.MetricTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.*;
@RestController @RequestMapping("/api/metric-templates") @RequiredArgsConstructor
public class MetricTemplateController {
    private final MetricTemplateService metricTemplateService;
    @GetMapping public Result<List<MetricTemplate>> list() { return Result.ok(metricTemplateService.list()); }
    @GetMapping("/{id}") public Result<Map<String,Object>> detail(@PathVariable Long id) { Map<String,Object> d=new HashMap<String,Object>(); d.put("template", metricTemplateService.detail(id)); d.put("items", metricTemplateService.listItems(id)); return Result.ok(d); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public Result<MetricTemplate> create(@Valid @RequestBody MetricTemplateReq req) { return Result.ok(metricTemplateService.create(req)); }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public Result<MetricTemplate> update(@PathVariable Long id,@Valid @RequestBody MetricTemplateReq req) { return Result.ok(metricTemplateService.update(id,req)); }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')") public Result<Void> delete(@PathVariable Long id) { metricTemplateService.delete(id); return Result.ok(); }
}
