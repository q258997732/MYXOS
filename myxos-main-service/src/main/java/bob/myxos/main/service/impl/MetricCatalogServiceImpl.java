package bob.myxos.main.service.impl;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.domain.mapper.MetricCatalogMapper;
import bob.myxos.main.dto.MetricCatalogUpdateReq;
import bob.myxos.main.service.MetricCatalogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MetricCatalogServiceImpl implements MetricCatalogService {
    private final MetricCatalogMapper metricCatalogMapper;

    @Override
    public List<MetricCatalog> list(String targetType) {
        LambdaQueryWrapper<MetricCatalog> query = new LambdaQueryWrapper<MetricCatalog>()
                .eq(MetricCatalog::getDeleted, 0).orderByAsc(MetricCatalog::getTargetType)
                .orderByAsc(MetricCatalog::getCategory).orderByAsc(MetricCatalog::getCode);
        if (targetType != null && !targetType.trim().isEmpty()) query.eq(MetricCatalog::getTargetType, targetType);
        return metricCatalogMapper.selectList(query);
    }

    @Override
    public MetricCatalog update(Long id, MetricCatalogUpdateReq req) {
        MetricCatalog catalog = metricCatalogMapper.selectById(id);
        if (catalog == null || Integer.valueOf(1).equals(catalog.getDeleted())) throw new BizException("指标目录项不存在");
        catalog.setDefaultIntervalSec(req.getDefaultIntervalSec());
        metricCatalogMapper.updateById(catalog);
        return catalog;
    }
}
