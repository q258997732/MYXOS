package bob.myxos.main.service;

import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.domain.entity.MetricTemplate;
import bob.myxos.domain.entity.MetricTemplateItem;
import bob.myxos.main.dto.MetricTemplateReq;
import java.util.List;

public interface MetricTemplateService {
    List<MetricCatalog> listCatalogs(String targetType);
    List<MetricTemplate> list();
    MetricTemplate detail(Long id);
    List<MetricTemplateItem> listItems(Long id);
    MetricTemplate create(MetricTemplateReq req);
    MetricTemplate update(Long id, MetricTemplateReq req);
    void delete(Long id);
}
