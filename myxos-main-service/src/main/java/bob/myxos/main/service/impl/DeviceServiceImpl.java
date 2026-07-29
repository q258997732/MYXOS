package bob.myxos.main.service.impl;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.DeviceGroup;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.mapper.DeviceGroupMapper;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.OpTaskMapper;
import bob.myxos.main.dto.DeviceCreateReq;
import bob.myxos.main.dto.DeviceListResp;
import bob.myxos.main.dto.DeviceUpdateReq;
import bob.myxos.main.service.DeviceService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 设备业务实现
 */
@Service
@RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {

    private final DeviceMapper deviceMapper;
    private final DeviceGroupMapper deviceGroupMapper;
    private final OpTaskMapper opTaskMapper;

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
    public Device updateDevice(Long id, DeviceUpdateReq req) {
        Device existing = getDetail(id);
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
    public Long submitOpTask(Long id, String operationCode, String params) {
        getDetail(id);
        OpTask task = buildOpTask(id, operationCode, params);
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
}
