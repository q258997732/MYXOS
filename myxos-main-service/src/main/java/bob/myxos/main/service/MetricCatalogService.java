package bob.myxos.main.service;

import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.main.dto.MetricCatalogUpdateReq;

import java.util.List;

public interface MetricCatalogService {
    List<MetricCatalog> list(String targetType);
    MetricCatalog update(Long id, MetricCatalogUpdateReq req);
}
