package bob.myxos.main.service.impl;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.ActionLog;
import bob.myxos.domain.entity.AlarmEvent;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.DeviceGroup;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.mapper.ActionLogMapper;
import bob.myxos.domain.mapper.AlarmEventMapper;
import bob.myxos.domain.mapper.DeviceGroupMapper;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.MetricSnapshotMapper;
import bob.myxos.domain.mapper.OpTaskMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import bob.myxos.main.dto.AndroidInstanceVO;
import bob.myxos.main.dto.DeviceCreateReq;
import bob.myxos.main.dto.DeviceListResp;
import bob.myxos.main.dto.DeviceUpdateReq;
import bob.myxos.main.service.DeviceService;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.AndroidListResp;
import bob.myxos.mytos.dto.BootStatusResp;
import bob.myxos.mytos.dto.ScreenshotResp;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
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
@RequiredArgsConstructor
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Device createDevice(DeviceCreateReq req) {
        // 校验 IP+端口 唯一
        Long count = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getIp, req.getIp())
                        .eq(Device::getPort, req.getPort())
                        .eq(Device::getDeleted, 0));
        if (count != null && count > 0) {
            throw new BizException("该 IP 和端口已存在");
        }
        validateGroupId(req.getGroupId());
        Device device = new Device();
        device.setName(req.getName());
        device.setIp(req.getIp());
        device.setPort(req.getPort());
        device.setMode(req.getMode());
        device.setGroupId(req.getGroupId());
        device.setRemark(req.getRemark());
        device.setStatus(DeviceStatus.UNKNOWN.name());
        device.setSource("MANUAL");
        deviceMapper.insert(device);
        return device;
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
    public void deleteDevice(Long id) {
        getDetail(id);
        deviceMapper.deleteById(id);
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
        String status = fetchAndroidStatus(client, ip, name);
        vo.setStatus(status);
        vo.setStatusLabel(androidStatusLabel(status));
        return vo;
    }

    private String fetchAndroidStatus(MytosClient client, String ip, String name) {
        try {
            BootStatusResp resp = client.getAndroidBootStatus(ip, name);
            if (resp == null || resp.getCode() == null || resp.getCode() != 200 || resp.getData() == null) {
                return "UNKNOWN";
            }
            String raw = resp.getData().isTextual() ? resp.getData().asText().trim().toLowerCase()
                    : resp.getData().toString().toLowerCase();
            if (raw.contains("run") || raw.contains("booted") || raw.contains("online")) {
                return "RUNNING";
            }
            if (raw.contains("stop") || raw.contains("offline") || raw.contains("down")) {
                return "STOPPED";
            }
            return "UNKNOWN";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private String androidStatusLabel(String status) {
        if ("RUNNING".equals(status)) {
            return "运行中";
        }
        if ("STOPPED".equals(status)) {
            return "已停止";
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
}
