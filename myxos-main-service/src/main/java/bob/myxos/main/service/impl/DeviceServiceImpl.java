package bob.myxos.main.service.impl;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.common.enums.ScopeType;
import bob.myxos.common.exception.BizException;
import bob.myxos.common.util.AndroidStatusParser;
import bob.myxos.domain.entity.ActionLog;
import bob.myxos.domain.entity.AlarmEvent;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.DeviceGroup;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.domain.entity.MetricTemplate;
import bob.myxos.domain.entity.MetricTemplateItem;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.ActionLogMapper;
import bob.myxos.domain.mapper.AlarmEventMapper;
import bob.myxos.domain.mapper.DeviceGroupMapper;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.MetricSnapshotMapper;
import bob.myxos.domain.mapper.MetricBindingMapper;
import bob.myxos.domain.mapper.MetricCatalogMapper;
import bob.myxos.domain.mapper.MetricTemplateItemMapper;
import bob.myxos.domain.mapper.MetricTemplateMapper;
import bob.myxos.domain.mapper.OpTaskMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import bob.myxos.main.dto.AndroidInstanceVO;
import bob.myxos.main.dto.DeviceCreateReq;
import bob.myxos.main.dto.DeviceListResp;
import bob.myxos.main.dto.DeviceUpdateReq;
import bob.myxos.main.dto.MetricBindingReq;
import bob.myxos.main.service.DeviceService;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.AndroidListResp;
import bob.myxos.mytos.dto.AndroidDetailResp;
import bob.myxos.mytos.dto.BootStatusResp;
import bob.myxos.mytos.dto.ClipboardResp;
import bob.myxos.mytos.dto.ScreenshotResp;
import bob.myxos.mytos.dto.ShellResp;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备业务实现
 */
@Slf4j
@Service
public class DeviceServiceImpl implements DeviceService {

    private final DeviceMapper deviceMapper;
    private final DeviceGroupMapper deviceGroupMapper;
    private final OpTaskMapper opTaskMapper;
    private final MetricSnapshotMapper metricSnapshotMapper;
    private final AlarmEventMapper alarmEventMapper;
    private final ActionLogMapper actionLogMapper;
    private final ThresholdRuleMapper thresholdRuleMapper;
    private final MytosClientFactory clientFactory;
    private final ObjectMapper objectMapper;
    private final MetricBindingMapper metricBindingMapper;
    private final MetricCatalogMapper metricCatalogMapper;
    private final MetricTemplateMapper metricTemplateMapper;
    private final MetricTemplateItemMapper metricTemplateItemMapper;

    public DeviceServiceImpl(DeviceMapper deviceMapper, DeviceGroupMapper deviceGroupMapper, OpTaskMapper opTaskMapper,
                             MetricSnapshotMapper metricSnapshotMapper, AlarmEventMapper alarmEventMapper,
                             ActionLogMapper actionLogMapper, ThresholdRuleMapper thresholdRuleMapper,
                             MytosClientFactory clientFactory, ObjectMapper objectMapper) {
        this(deviceMapper, deviceGroupMapper, opTaskMapper, metricSnapshotMapper, alarmEventMapper,
                actionLogMapper, thresholdRuleMapper, clientFactory, objectMapper, null, null, null, null);
    }

    @Autowired
    public DeviceServiceImpl(DeviceMapper deviceMapper, DeviceGroupMapper deviceGroupMapper, OpTaskMapper opTaskMapper,
                             MetricSnapshotMapper metricSnapshotMapper, AlarmEventMapper alarmEventMapper,
                             ActionLogMapper actionLogMapper, ThresholdRuleMapper thresholdRuleMapper,
                             MytosClientFactory clientFactory, ObjectMapper objectMapper,
                             MetricBindingMapper metricBindingMapper, MetricCatalogMapper metricCatalogMapper,
                             MetricTemplateMapper metricTemplateMapper, MetricTemplateItemMapper metricTemplateItemMapper) {
        this.deviceMapper = deviceMapper;
        this.deviceGroupMapper = deviceGroupMapper;
        this.opTaskMapper = opTaskMapper;
        this.metricSnapshotMapper = metricSnapshotMapper;
        this.alarmEventMapper = alarmEventMapper;
        this.actionLogMapper = actionLogMapper;
        this.thresholdRuleMapper = thresholdRuleMapper;
        this.clientFactory = clientFactory;
        this.objectMapper = objectMapper;
        this.metricBindingMapper = metricBindingMapper;
        this.metricCatalogMapper = metricCatalogMapper;
        this.metricTemplateMapper = metricTemplateMapper;
        this.metricTemplateItemMapper = metricTemplateItemMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Device createDevice(DeviceCreateReq req) {
        validateGroupId(req.getGroupId());
        Device device = new Device();
        String name = req.getName();
        if (name == null || name.trim().isEmpty()) {
            name = req.getIp() + ":" + req.getPort();
        }
        device.setName(name);
        device.setIp(req.getIp());
        device.setPort(req.getPort());
        device.setMode(req.getMode());
        device.setGroupId(req.getGroupId());
        device.setRemark(req.getRemark());
        device.setStatus(DeviceStatus.UNKNOWN.name());
        device.setSource("MANUAL");
        try {
            deviceMapper.insert(device);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BizException("该 IP 和端口已存在");
        }
        writeActionLog(device, "手动添加设备：" + device.getName() + "(" + device.getIp() + ":" + device.getPort() + ")");
        return device;
    }

    private void writeActionLog(Device device, String message) {
        ActionLog log = new ActionLog();
        log.setDeviceId(device.getId());
        log.setActionType("SYSTEM");
        log.setLogLevel("INFO");
        log.setMessage(message);
        log.setCreatedAt(LocalDateTime.now());
        actionLogMapper.insert(log);
    }

    private void validateGroupId(Long groupId) {
        if (groupId == null || groupId == 0L) {
            return;
        }
        DeviceGroup group = deviceGroupMapper.selectById(groupId);
        if (group == null || (group.getDeleted() != null && group.getDeleted() == 1)) {
            throw new BizException("设备分组不存在");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DeviceListResp> listDevices(Long groupId, String status, String keyword, Long page, Long size) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Device::getDeleted, 0);
        if (groupId != null) {
            wrapper.eq(Device::getGroupId, groupId);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Device::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Device::getName, keyword).or().like(Device::getIp, keyword));
        }
        wrapper.orderByDesc(Device::getWhenCreated);

        Page<Device> devicePage = deviceMapper.selectPage(new Page<>(page, size), wrapper);

        // 组装告警数量
        List<Device> records = devicePage.getRecords();
        Map<Long, Long> alarmCountMap;
        if (records == null || records.isEmpty()) {
            alarmCountMap = Collections.emptyMap();
        } else {
            List<Long> ids = records.stream().map(Device::getId).collect(Collectors.toList());
            List<Map<String, Object>> rows = deviceMapper.countFiringAlarmsByDeviceIds(ids);
            alarmCountMap = new HashMap<>(rows.size() * 2);
            for (Map<String, Object> row : rows) {
                Object idObj = row.get("deviceId");
                Object cntObj = row.get("alarmCount");
                if (idObj == null || cntObj == null) {
                    continue;
                }
                Long devId = ((Number) idObj).longValue();
                Long cnt = ((Number) cntObj).longValue();
                alarmCountMap.put(devId, cnt);
            }
        }

        // 转换为 DTO
        Page<DeviceListResp> result = new Page<>(devicePage.getCurrent(), devicePage.getSize(), devicePage.getTotal());
        final Map<Long, Long> countMap = alarmCountMap;
        List<DeviceListResp> dtoList = records == null ? Collections.emptyList()
                : records.stream().map(d -> {
                    DeviceListResp resp = new DeviceListResp();
                    BeanUtils.copyProperties(d, resp);
                    resp.setAlarmCount(countMap.getOrDefault(d.getId(), 0L));
                    return resp;
                }).collect(Collectors.toList());
        result.setRecords(dtoList);
        return result;
    }

    @Override
    public Device getDetail(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null || (device.getDeleted() != null && device.getDeleted() == 1)) {
            throw new BizException("设备不存在");
        }
        return device;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Device updateDevice(Long id, DeviceUpdateReq req) {
        Device existing = getDetail(id);
        if (req.getGroupId() != null) {
            validateGroupId(req.getGroupId());
        }
        Device update = new Device();
        update.setId(id);
        if (req.getName() != null) {
            update.setName(req.getName());
            existing.setName(req.getName());
        }
        if (req.getGroupId() != null) {
            update.setGroupId(req.getGroupId());
            existing.setGroupId(req.getGroupId());
        }
        if (req.getStatus() != null) {
            update.setStatus(req.getStatus());
            existing.setStatus(req.getStatus());
        }
        if (req.getRemark() != null) {
            update.setRemark(req.getRemark());
            existing.setRemark(req.getRemark());
        }
        deviceMapper.updateById(update);
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDevice(Long id) {
        getDetail(id);
        deviceMapper.deleteById(id);
        // 级联清理设备关联数据（均为逻辑删除）：
        // 指标快照、告警事件、操作任务（含未执行的 PENDING 采集/操作任务）、动作日志
        metricSnapshotMapper.delete(new LambdaQueryWrapper<MetricSnapshot>()
                .eq(MetricSnapshot::getDeviceId, id));
        alarmEventMapper.delete(new LambdaQueryWrapper<AlarmEvent>()
                .eq(AlarmEvent::getDeviceId, id));
        opTaskMapper.delete(new LambdaQueryWrapper<OpTask>()
                .eq(OpTask::getDeviceId, id));
        actionLogMapper.delete(new LambdaQueryWrapper<ActionLog>()
                .eq(ActionLog::getDeviceId, id));
        removeDeviceFromThresholdRules(id);
        log.info("设备已删除并级联清理关联数据：deviceId={}", id);
    }

    /**
     * 从引用该设备的阈值规则中剔除设备：
     * scopeIds 列表移除该设备 ID；剔除后不再包含任何目标设备的 DEVICE 规则直接禁用，
     * 避免残留永不命中的规则
     *
     * @param deviceId 被删除的设备 ID
     */
    private void removeDeviceFromThresholdRules(Long deviceId) {
        List<ThresholdRule> rules = thresholdRuleMapper.selectList(new LambdaQueryWrapper<ThresholdRule>()
                .eq(ThresholdRule::getScopeType, "DEVICE"));
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (ThresholdRule rule : rules) {
            List<Long> ids = parseScopeIds(rule.getScopeIds());
            boolean referenced = ids.remove(deviceId);
            boolean scopeIdMatched = rule.getScopeId() != null && rule.getScopeId().equals(deviceId);
            if (!referenced && !scopeIdMatched) {
                continue;
            }
            Long newScopeId = scopeIdMatched ? (ids.isEmpty() ? null : ids.get(0)) : rule.getScopeId();
            String newScopeIds = ids.isEmpty() ? null
                    : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            boolean disable = newScopeId == null && newScopeIds == null;
            thresholdRuleMapper.update(null, new LambdaUpdateWrapper<ThresholdRule>()
                    .eq(ThresholdRule::getId, rule.getId())
                    .set(ThresholdRule::getScopeId, newScopeId)
                    .set(ThresholdRule::getScopeIds, newScopeIds)
                    .set(disable, ThresholdRule::getEnabled, 0));
            if (disable) {
                log.info("阈值规则因目标设备被删除而禁用：ruleId={}, deviceId={}", rule.getId(), deviceId);
            }
        }
    }

    /**
     * 解析逗号分隔的设备 ID 列表
     */
    private List<Long> parseScopeIds(String scopeIds) {
        List<Long> ids = new ArrayList<>();
        if (scopeIds == null || scopeIds.trim().isEmpty()) {
            return ids;
        }
        for (String part : scopeIds.split(",")) {
            try {
                ids.add(Long.parseLong(part.trim()));
            } catch (NumberFormatException ignored) {
                // 忽略非法片段
            }
        }
        return ids;
    }

    @Override
    public void triggerCollect(Long id) {
        getDetail(id);
        OpTask task = buildOpTask(id, "COLLECT", null);
        opTaskMapper.insert(task);
    }

    @Override
    public Long submitOpTask(Long id, String operationCode, Map<String, Object> params) {
        getDetail(id);
        OpTask task = buildOpTask(id, operationCode, serializeParams(params));
        opTaskMapper.insert(task);
        return task.getId();
    }

    /** 构造一条 PENDING 状态的手动操作任务 */
    private OpTask buildOpTask(Long deviceId, String operationCode, String params) {
        OpTask task = new OpTask();
        task.setDeviceId(deviceId);
        task.setOperationCode(operationCode);
        task.setParams(params);
        task.setSource("MANUAL");
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setMaxRetry(3);
        task.setScheduledAt(LocalDateTime.now());
        return task;
    }

    /** 将参数 Map 序列化为 JSON 字符串，失败时抛出业务异常 */
    private String serializeParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            throw new BizException("操作参数序列化失败：" + e.getMessage());
        }
    }

    @Override
    public String screenshot(Long id, String name, String level) {
        Device device = getDetail(id);
        MytosClient client = clientFactory.create(device.getIp(), device.getPort());
        ScreenshotResp resp = client.screenshot(device.getIp(), name, level);
        // 设备端将图片 Base64 放在 message/msg 字段，data 为对象（含 url）
        String image = resp.getMsg();
        if (image == null || image.isEmpty()) {
            if (resp.getData() != null && resp.getData().has("url")) {
                image = resp.getData().get("url").asText();
            }
        }
        return validateImageData(image);
    }

    /**
     * 校验截图返回数据：只允许已知图片 Base64 前缀或 http(s) URL
     */
    private String validateImageData(String image) {
        if (image == null || image.isEmpty()) {
            throw new BizException("截图数据为空");
        }
        if (image.startsWith("http://") || image.startsWith("https://")) {
            return image;
        }
        if (image.startsWith("/9j/") || image.startsWith("iVBORw0KGgo")) {
            return image;
        }
        throw new BizException("截图数据格式不合法");
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MetricSnapshot> listMetrics(Long id, Long page, Long size) {
        getDetail(id);
        LambdaQueryWrapper<MetricSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetricSnapshot::getDeviceId, id);
        wrapper.eq(MetricSnapshot::getDeleted, 0);
        wrapper.orderByDesc(MetricSnapshot::getCollectedAt);
        return metricSnapshotMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AlarmEvent> listAlarms(Long id, Long page, Long size) {
        getDetail(id);
        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmEvent::getDeviceId, id);
        wrapper.eq(AlarmEvent::getDeleted, 0);
        wrapper.orderByDesc(AlarmEvent::getFiredAt);
        Page<AlarmEvent> result = alarmEventMapper.selectPage(new Page<>(page, size), wrapper);
        fillAlarmRuleNames(result.getRecords());
        return result;
    }

    /** 批量填充告警事件的规则名称 */
    private void fillAlarmRuleNames(List<AlarmEvent> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ruleIds = records.stream()
                .map(AlarmEvent::getRuleId)
                .distinct()
                .collect(Collectors.toList());
        if (ruleIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameMap = thresholdRuleMapper.selectBatchIds(ruleIds).stream()
                .collect(Collectors.toMap(r -> r.getId(), r -> r.getName() == null ? "" : r.getName()));
        for (AlarmEvent event : records) {
            event.setRuleName(nameMap.getOrDefault(event.getRuleId(), ""));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ActionLog> listLogs(Long id, Long page, Long size) {
        getDetail(id);
        LambdaQueryWrapper<ActionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActionLog::getDeviceId, id);
        wrapper.eq(ActionLog::getDeleted, 0);
        wrapper.orderByDesc(ActionLog::getCreatedAt);
        return actionLogMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OpTask> listOpTasks(Long id, Long page, Long size) {
        getDetail(id);
        LambdaQueryWrapper<OpTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OpTask::getDeviceId, id);
        wrapper.eq(OpTask::getDeleted, 0);
        wrapper.orderByDesc(OpTask::getWhenCreated);
        return opTaskMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public List<AndroidInstanceVO> listAndroidInstances(Long id) {
        Device device = getDetail(id);
        try {
            MytosClient client = clientFactory.create(device.getIp(), device.getPort());
            AndroidListResp resp = client.listAndroid(device.getIp());
            if (resp == null) {
                return Collections.emptyList();
            }
            if (resp.getCode() == null || resp.getCode() != 200) {
                log.warn("获取安卓实例列表设备返回错误：deviceId={}, msg={}", id, resp.getMsg());
                return Collections.emptyList();
            }
            List<String> names = parseAndroidNames(resp.getData());
            return names.stream()
                    .map(name -> buildAndroidInstanceVO(client, device.getIp(), name))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("获取安卓实例列表失败：deviceId={}", id, e);
            return Collections.emptyList();
        }
    }

    private AndroidInstanceVO buildAndroidInstanceVO(MytosClient client, String ip, String name) {
        AndroidInstanceVO vo = new AndroidInstanceVO();
        vo.setName(name);
        vo.setIp(ip);

        AndroidDetail detail = fetchAndroidDetail(client, ip, name);
        if (detail != null) {
            vo.setImage(detail.getImage());
        }

        String status = fetchAndroidStatus(client, ip, name, detail);
        vo.setStatus(status);
        vo.setStatusLabel(androidStatusLabel(status));
        vo.setStatusDetail(buildStatusDetail(detail));
        return vo;
    }

    private AndroidDetail fetchAndroidDetail(MytosClient client, String ip, String name) {
        try {
            AndroidDetailResp resp = client.getAndroidDetail(ip, name);
            if (resp != null && resp.getCode() != null && resp.getCode() == 200 && resp.getData() != null) {
                AndroidDetail detail = new AndroidDetail();
                JsonNode data = resp.getData();
                if (data.has("image")) detail.setImage(data.get("image").asText(null));
                if (data.has("status")) detail.setStatus(data.get("status").asText(null));
                if (data.has("ip")) detail.setIp(data.get("ip").asText(null));
                return detail;
            }
        } catch (Exception e) {
            log.debug("获取安卓实例详情失败：{} {}", ip, name, e);
        }
        return null;
    }

    private String fetchAndroidStatus(MytosClient client, String ip, String name, AndroidDetail detail) {
        if (detail != null && detail.getStatus() != null) {
            String status = AndroidStatusParser.parse(detail.getStatus());
            if (!AndroidStatusParser.UNKNOWN.equals(status)) {
                return status;
            }
        }
        try {
            BootStatusResp resp = client.getAndroidBootStatus(ip, name);
            if (resp == null || resp.getCode() == null || resp.getCode() != 200 || resp.getData() == null) {
                return AndroidStatusParser.UNKNOWN;
            }
            String raw = resp.getData().isTextual() ? resp.getData().asText() : resp.getData().toString();
            return AndroidStatusParser.parse(raw);
        } catch (Exception e) {
            return AndroidStatusParser.UNKNOWN;
        }
    }

    private String buildStatusDetail(AndroidDetail detail) {
        if (detail == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        // 始终保留原始状态字段，即使解析为 UNKNOWN 也便于排查
        if (detail.getStatus() != null) {
            parts.add("原始状态: " + detail.getStatus());
        }
        if (detail.getIp() != null) {
            parts.add("IP: " + detail.getIp());
        }
        if (detail.getImage() != null) {
            parts.add("镜像: " + detail.getImage());
        }
        return parts.isEmpty() ? null : String.join(" | ", parts);
    }

    @Data
    private static class AndroidDetail {
        private String status;
        private String ip;
        private String image;
    }

    private String androidStatusLabel(String status) {
        if (AndroidStatusParser.RUNNING.equals(status)) {
            return "运行中";
        }
        if (AndroidStatusParser.STOPPED.equals(status)) {
            return "已停止";
        }
        if (AndroidStatusParser.TRANSITION.equals(status)) {
            return "过渡中";
        }
        return "未知";
    }

    @Override
    @Transactional(readOnly = true)
    public List<MetricSnapshot> listLatestMetrics(Long id) {
        getDetail(id);
        List<MetricSnapshot> snapshots = metricSnapshotMapper.selectLatestPerTypeByDevice(id);
        return snapshots == null ? Collections.emptyList() : snapshots;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MetricSnapshot> listMetricHistory(Long id, String metricType, Long page, Long size) {
        getDetail(id);
        LambdaQueryWrapper<MetricSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetricSnapshot::getDeviceId, id);
        wrapper.eq(MetricSnapshot::getMetricType, metricType);
        wrapper.eq(MetricSnapshot::getDeleted, 0);
        wrapper.orderByDesc(MetricSnapshot::getCollectedAt);
        return metricSnapshotMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listAndroidNames(String scopeType, Long scopeId, String scopeIds) {
        List<Long> deviceIds = resolveScopeDeviceIds(scopeType, scopeId, scopeIds);
        if (deviceIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> extras = metricSnapshotMapper.selectDistinctAndroidExtras(deviceIds);
        if (extras == null || extras.isEmpty()) {
            return Collections.emptyList();
        }
        return extras.stream()
                .map(this::parseAndroidNameFromExtra)
                .filter(name -> name != null && !name.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * 按作用范围解析目标设备 ID 集合：ALL 全部设备，GROUP 按分组，DEVICE 按多选 ID
     */
    private List<Long> resolveScopeDeviceIds(String scopeType, Long scopeId, String scopeIds) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Device::getId).eq(Device::getDeleted, 0);
        if (ScopeType.GROUP.name().equals(scopeType)) {
            if (scopeId == null) {
                return Collections.emptyList();
            }
            wrapper.eq(Device::getGroupId, scopeId);
        } else if (ScopeType.DEVICE.name().equals(scopeType)) {
            List<Long> ids = parseDeviceIds(scopeIds, scopeId);
            if (ids.isEmpty()) {
                return Collections.emptyList();
            }
            wrapper.in(Device::getId, ids);
        }
        return deviceMapper.selectList(wrapper).stream()
                .map(Device::getId)
                .collect(Collectors.toList());
    }

    /**
     * 解析多设备 ID：优先 scopeIds 逗号串，回退单个 scopeId
     */
    private List<Long> parseDeviceIds(String scopeIds, Long scopeId) {
        List<Long> ids = new ArrayList<>();
        if (scopeIds != null && !scopeIds.trim().isEmpty()) {
            for (String part : scopeIds.split(",")) {
                try {
                    ids.add(Long.parseLong(part.trim()));
                } catch (NumberFormatException ignored) {
                    // 忽略非法片段，继续解析其余 ID
                }
            }
        }
        if (ids.isEmpty() && scopeId != null) {
            ids.add(scopeId);
        }
        return ids;
    }

    /**
     * 从快照 extra（形如 {"name":"容器名"}）中解析安卓实例名
     */
    private String parseAndroidNameFromExtra(String extra) {
        if (extra == null || extra.isEmpty()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(extra);
            JsonNode nameNode = node.get("name");
            return nameNode != null && !nameNode.isNull() ? nameNode.asText() : null;
        } catch (Exception e) {
            log.debug("解析快照 extra 失败：{}", extra);
            return null;
        }
    }

    /**
     * 解析 MYTOS 返回的安卓实例数据为名称列表
     * 支持字符串数组或对象数组（取 name 字段）
     */
    private List<String> parseAndroidNames(JsonNode data) {
        if (data == null || !data.isArray()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>(data.size());
        for (JsonNode node : data) {
            if (node.isTextual()) {
                names.add(node.asText());
            } else if (node.isObject()) {
                JsonNode nameNode = node.get("name");
                if (nameNode != null && !nameNode.isNull()) {
                    names.add(nameNode.asText());
                }
            }
        }
        return names;
    }

    @Override
    public String executeShell(Long id, String name, String command) {
        Device device = getDetail(id);
        MytosClient client = clientFactory.create(device.getIp(), device.getPort());
        ShellResp resp = client.shell(device.getIp(), name, command);
        if (resp == null) {
            return "";
        }
        // 真实设备将命令输出放在 message/msg 字段，data 仅包含 shell_code
        String msg = resp.getMsg();
        if (msg != null && !msg.isEmpty()) {
            return msg;
        }
        JsonNode data = resp.getData();
        if (data == null) {
            return "";
        }
        if (data.has("output")) {
            return data.get("output").asText("");
        }
        if (data.has("result")) {
            return data.get("result").asText("");
        }
        return data.toString();
    }

    @Override
    public String getClipboard(Long id, String name) {
        Device device = getDetail(id);
        MytosClient client = clientFactory.create(device.getIp(), device.getPort());
        ClipboardResp resp = client.clipboardGet(device.getIp(), name);
        if (resp == null || resp.getData() == null) {
            return "";
        }
        return resp.getData();
    }

    @Override
    public List<MetricBinding> listMetricBindings(Long id, String androidName) {
        getDetail(id);
        String name = androidName == null ? "" : androidName;
        return metricBindingMapper.selectList(new LambdaQueryWrapper<MetricBinding>()
                .eq(MetricBinding::getDeviceId, id).eq(MetricBinding::getAndroidName, name)
                .eq(MetricBinding::getDeleted, 0).orderByAsc(MetricBinding::getMetricCode));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<MetricBinding> saveMetricBindings(Long id, String androidName, MetricBindingReq req) {
        getDetail(id);
        String name = androidName == null ? "" : androidName;
        boolean android = !name.isEmpty();
        if (android && !name.matches("^[A-Za-z0-9_.-]{1,128}$")) throw new BizException("安卓实例名称格式不合法");
        List<MetricBindingReq.Item> directItems = req.getItems() == null ? Collections.<MetricBindingReq.Item>emptyList() : req.getItems();
        for (MetricBindingReq.Item item : directItems) saveBinding(id, name, android ? "ANDROID_INSTANCE" : "HOST", item.getMetricCode(), item.getEnabled(), item.getIntervalSec());
        if (req.getTemplateIds() != null) for (Long templateId : req.getTemplateIds()) applyTemplate(id, name, android ? "ANDROID_INSTANCE" : "HOST", templateId);
        return listMetricBindings(id, name);
    }

    private void applyTemplate(Long deviceId, String androidName, String targetType, Long templateId) {
        MetricTemplate template = metricTemplateMapper.selectById(templateId);
        if (template == null || Integer.valueOf(1).equals(template.getDeleted()) || !targetType.equals(template.getTargetType())) throw new BizException("指标模板与目标类型不兼容: " + templateId);
        List<MetricTemplateItem> items = metricTemplateItemMapper.selectList(new LambdaQueryWrapper<MetricTemplateItem>().eq(MetricTemplateItem::getTemplateId, templateId).eq(MetricTemplateItem::getDeleted, 0));
        for (MetricTemplateItem item : items) {
            MetricCatalog catalog = metricCatalogMapper.selectById(item.getMetricCatalogId());
            if (catalog == null || !targetType.equals(catalog.getTargetType())) throw new BizException("模板包含不兼容指标");
            saveBinding(deviceId, androidName, targetType, catalog.getCode(), item.getEnabled(), item.getDefaultIntervalSec());
        }
    }

    private void saveBinding(Long deviceId, String androidName, String targetType, String metricCode, Integer enabled, Integer intervalSec) {
        if (metricCode == null || metricCode.trim().isEmpty()) throw new BizException("指标编码不能为空");
        if (intervalSec != null && (intervalSec < 15 || intervalSec > 86400)) throw new BizException("采集频率必须在15至86400秒之间");
        MetricCatalog catalog = metricCatalogMapper.selectOne(new LambdaQueryWrapper<MetricCatalog>().eq(MetricCatalog::getCode, metricCode).eq(MetricCatalog::getTargetType, targetType).eq(MetricCatalog::getDeleted, 0));
        if (catalog == null) throw new BizException("指标不存在或与目标类型不兼容: " + metricCode);
        MetricBinding exists = metricBindingMapper.selectOne(new LambdaQueryWrapper<MetricBinding>().eq(MetricBinding::getDeviceId, deviceId).eq(MetricBinding::getAndroidName, androidName).eq(MetricBinding::getMetricCode, metricCode).eq(MetricBinding::getDeleted, 0));
        if (exists != null) { exists.setEnabled(enabled == null ? exists.getEnabled() : enabled); exists.setIntervalSec(intervalSec == null ? exists.getIntervalSec() : intervalSec); metricBindingMapper.updateById(exists); return; }
        MetricBinding binding = new MetricBinding(); binding.setDeviceId(deviceId); binding.setAndroidName(androidName); binding.setTargetType(targetType); binding.setMetricCode(metricCode); binding.setEnabled(enabled == null ? 1 : enabled); binding.setIntervalSec(intervalSec); metricBindingMapper.insert(binding);
    }

    @Override
    public MetricBinding resolveEffectiveMetricBinding(Long id, String androidName, String metricCode) {
        List<MetricBinding> all = metricBindingMapper.selectList(new LambdaQueryWrapper<MetricBinding>().eq(MetricBinding::getDeviceId, id).eq(MetricBinding::getMetricCode, metricCode).eq(MetricBinding::getDeleted, 0));
        String name = androidName == null ? "" : androidName;
        for (MetricBinding binding : all) if (name.equals(binding.getAndroidName())) return binding;
        for (MetricBinding binding : all) if ("".equals(binding.getAndroidName())) return binding;
        return null;
    }
}
