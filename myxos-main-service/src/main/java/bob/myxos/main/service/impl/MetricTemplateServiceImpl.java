package bob.myxos.main.service.impl;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.domain.entity.MetricTemplate;
import bob.myxos.domain.entity.MetricTemplateItem;
import bob.myxos.domain.mapper.MetricBindingMapper;
import bob.myxos.domain.mapper.MetricCatalogMapper;
import bob.myxos.domain.mapper.MetricTemplateItemMapper;
import bob.myxos.domain.mapper.MetricTemplateMapper;
import bob.myxos.main.dto.MetricTemplateReq;
import bob.myxos.main.metric.MetricDefinitionRegistry;
import bob.myxos.main.service.MetricTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MetricTemplateServiceImpl implements MetricTemplateService {
    private final MetricCatalogMapper metricCatalogMapper;
    private final MetricTemplateMapper metricTemplateMapper;
    private final MetricTemplateItemMapper metricTemplateItemMapper;
    private final MetricBindingMapper metricBindingMapper;

    @Override
    public List<MetricCatalog> listCatalogs(String targetType) {
        LambdaQueryWrapper<MetricCatalog> wrapper = new LambdaQueryWrapper<MetricCatalog>()
                .eq(MetricCatalog::getDeleted, 0).orderByAsc(MetricCatalog::getCode);
        if (targetType != null && !targetType.trim().isEmpty()) wrapper.eq(MetricCatalog::getTargetType, targetType);
        return metricCatalogMapper.selectList(wrapper);
    }
    @Override public List<MetricTemplate> list() { return metricTemplateMapper.selectList(new LambdaQueryWrapper<MetricTemplate>().eq(MetricTemplate::getDeleted, 0).orderByAsc(MetricTemplate::getName)); }
    @Override public MetricTemplate detail(Long id) { MetricTemplate t = metricTemplateMapper.selectById(id); if (t == null || Integer.valueOf(1).equals(t.getDeleted())) throw new BizException("指标模板不存在"); return t; }
    @Override public List<MetricTemplateItem> listItems(Long id) { detail(id); return metricTemplateItemMapper.selectList(new LambdaQueryWrapper<MetricTemplateItem>().eq(MetricTemplateItem::getTemplateId, id).eq(MetricTemplateItem::getDeleted, 0)); }
    @Override @Transactional(rollbackFor = Exception.class)
    public MetricTemplate create(MetricTemplateReq req) { MetricTemplate t = new MetricTemplate(); copy(req, t); metricTemplateMapper.insert(t); saveItems(t.getId(), req); return t; }
    @Override @Transactional(rollbackFor = Exception.class)
    public MetricTemplate update(Long id, MetricTemplateReq req) { MetricTemplate t = detail(id); copy(req, t); metricTemplateMapper.updateById(t); metricTemplateItemMapper.delete(new LambdaQueryWrapper<MetricTemplateItem>().eq(MetricTemplateItem::getTemplateId, id)); saveItems(id, req); return t; }
    @Override @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) { detail(id); metricTemplateMapper.deleteById(id); metricTemplateItemMapper.delete(new LambdaQueryWrapper<MetricTemplateItem>().eq(MetricTemplateItem::getTemplateId, id)); }

    public MetricBinding resolveEffectiveBinding(Long deviceId, String androidName, String metricCode, List<MetricBinding> bindings) {
        if (bindings == null) return null;
        String name = androidName == null ? "" : androidName;
        MetricBinding instance = find(bindings, deviceId, name, metricCode);
        return instance != null ? instance : find(bindings, deviceId, "", metricCode);
    }
    private MetricBinding find(List<MetricBinding> bindings, Long id, String name, String code) {
        for (MetricBinding b : bindings) if (id.equals(b.getDeviceId()) && name.equals(b.getAndroidName()) && code.equals(b.getMetricCode()) && !Integer.valueOf(1).equals(b.getDeleted())) return b;
        return null;
    }
    private void copy(MetricTemplateReq req, MetricTemplate t) { t.setName(req.getName().trim()); t.setTargetType(req.getTargetType()); t.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled()); }
    private void saveItems(Long templateId, MetricTemplateReq req) {
        for (MetricTemplateReq.Item item : req.getItems()) {
            MetricCatalog catalog = metricCatalogMapper.selectById(item.getMetricCatalogId());
            if (catalog == null || Integer.valueOf(1).equals(catalog.getDeleted()) || !req.getTargetType().equals(catalog.getTargetType())) throw new BizException("模板指标与目标类型不兼容: " + item.getMetricCatalogId());
            if (MetricDefinitionRegistry.APP_PROCESS_STATE.equals(catalog.getCode())) {
                throw new BizException("应用进程状态指标必须在安卓实例上绑定具体应用包名");
            }
            MetricTemplateItem entity = new MetricTemplateItem(); entity.setTemplateId(templateId); entity.setMetricCatalogId(item.getMetricCatalogId()); entity.setEnabled(item.getEnabled() == null ? 1 : item.getEnabled()); entity.setDefaultIntervalSec(item.getDefaultIntervalSec()); entity.setEnumOptions(item.getEnumOptions()); metricTemplateItemMapper.insert(entity);
        }
    }
}
