package bob.myxos.main.service;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.common.enums.OperationCode;
import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.DeviceGroup;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.mapper.ActionLogMapper;
import bob.myxos.domain.mapper.AlarmEventMapper;
import bob.myxos.domain.mapper.DeviceGroupMapper;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.MetricSnapshotMapper;
import bob.myxos.domain.mapper.OpTaskMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import bob.myxos.main.dto.DeviceCreateReq;
import bob.myxos.main.dto.DeviceListResp;
import bob.myxos.main.dto.DeviceUpdateReq;
import bob.myxos.main.service.impl.DeviceServiceImpl;
import bob.myxos.mytos.MytosClientFactory;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeviceService 单元测试
 * 使用 Mockito 隔离数据库依赖，验证设备创建、查询、更新、删除、操作下发逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceService 测试")
class DeviceServiceTest {

    @Mock
    private DeviceMapper deviceMapper;

    @Mock
    private DeviceGroupMapper deviceGroupMapper;

    @Mock
    private OpTaskMapper opTaskMapper;

    @Mock
    private MetricSnapshotMapper metricSnapshotMapper;

    @Mock
    private AlarmEventMapper alarmEventMapper;

    @Mock
    private ActionLogMapper actionLogMapper;

    @Mock
    private ThresholdRuleMapper thresholdRuleMapper;

    @Mock
    private MytosClientFactory mytosClientFactory;

    @Mock
    private ObjectMapper objectMapper;

    private DeviceServiceImpl deviceService;

    @BeforeEach
    void setUp() {
        deviceService = new DeviceServiceImpl(deviceMapper, deviceGroupMapper, opTaskMapper,
                metricSnapshotMapper, alarmEventMapper, actionLogMapper, thresholdRuleMapper,
                mytosClientFactory, objectMapper);
    }

    /** 构造合法的创建请求 */
    private DeviceCreateReq buildCreateReq() {
        DeviceCreateReq req = new DeviceCreateReq();
        req.setName("测试设备");
        req.setIp("192.168.30.2");
        req.setPort(9082);
        req.setMode("BRIDGE");
        req.setGroupId(1L);
        req.setRemark("备注");
        return req;
    }

    @Test
    @DisplayName("创建设备成功：状态为 UNKNOWN，来源为 MANUAL")
    void createDeviceSuccess() {
        // Arrange
        DeviceGroup group = new DeviceGroup();
        group.setId(1L);
        group.setDeleted(0);
        when(deviceGroupMapper.selectById(1L)).thenReturn(group);
        when(deviceMapper.insert(any(Device.class))).thenAnswer(inv -> {
            Device d = inv.getArgument(0);
            d.setId(100L);
            return 1;
        });

        // Act
        Device saved = deviceService.createDevice(buildCreateReq());

        // Assert
        assertNotNull(saved);
        assertEquals(100L, saved.getId());
        assertEquals(DeviceStatus.UNKNOWN.name(), saved.getStatus());
        assertEquals("MANUAL", saved.getSource());
        assertEquals("192.168.30.2", saved.getIp());
        assertEquals(9082, saved.getPort());
    }

    @Test
    @DisplayName("创建设备失败：IP+端口已存在时抛出 BizException")
    void createDeviceFailsWhenDuplicate() {
        // Arrange
        DeviceGroup group = new DeviceGroup();
        group.setId(1L);
        group.setDeleted(0);
        when(deviceGroupMapper.selectById(1L)).thenReturn(group);
        when(deviceMapper.insert(any(Device.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("Duplicate entry"));

        // Act & Assert
        BizException ex = assertThrows(BizException.class, () -> deviceService.createDevice(buildCreateReq()));
        assertTrue(ex.getMessage().contains("已存在"));
        verify(deviceMapper).insert(any(Device.class));
    }

    @Test
    @DisplayName("分页查询设备：返回附带 FIRING 告警数量的 DTO")
    void listDevicesWithAlarmCount() {
        // Arrange
        Device d1 = new Device();
        d1.setId(1L);
        d1.setName("A");
        Device d2 = new Device();
        d2.setId(2L);
        d2.setName("B");
        Page<Device> page = new Page<>(1, 20);
        page.setRecords(new ArrayList<>(Arrays.asList(d1, d2)));
        page.setTotal(2);

        when(deviceMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        Map<String, Object> row = new HashMap<>();
        row.put("deviceId", 1L);
        row.put("alarmCount", 3L);
        when(deviceMapper.countFiringAlarmsByDeviceIds(anyList())).thenReturn(Collections.singletonList(row));

        // Act
        Page<DeviceListResp> result = deviceService.listDevices(null, null, null, 1L, 20L);

        // Assert
        assertEquals(2, result.getRecords().size());
        assertEquals(2L, result.getTotal());
        DeviceListResp r1 = result.getRecords().stream().filter(r -> r.getId().equals(1L)).findFirst().orElseThrow(AssertionError::new);
        assertEquals(3L, r1.getAlarmCount());
        DeviceListResp r2 = result.getRecords().stream().filter(r -> r.getId().equals(2L)).findFirst().orElseThrow(AssertionError::new);
        assertEquals(0L, r2.getAlarmCount());
    }

    @Test
    @DisplayName("分页查询设备：空结果时不调用告警统计")
    void listDevicesEmptySkipsAlarmCount() {
        // Arrange
        Page<Device> page = new Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(deviceMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        // Act
        Page<DeviceListResp> result = deviceService.listDevices(null, null, null, 1L, 20L);

        // Assert
        assertEquals(0, result.getRecords().size());
        verify(deviceMapper, never()).countFiringAlarmsByDeviceIds(anyList());
    }

    @Test
    @DisplayName("查询详情：设备不存在时抛出 BizException")
    void getDetailNotFound() {
        // Arrange
        when(deviceMapper.selectById(99L)).thenReturn(null);

        // Act & Assert
        BizException ex = assertThrows(BizException.class, () -> deviceService.getDetail(99L));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    @DisplayName("查询详情：设备已逻辑删除时抛出 BizException")
    void getDetailDeleted() {
        // Arrange
        Device d = new Device();
        d.setId(1L);
        d.setDeleted(1);
        when(deviceMapper.selectById(1L)).thenReturn(d);

        // Act & Assert
        assertThrows(BizException.class, () -> deviceService.getDetail(1L));
    }

    @Test
    @DisplayName("更新设备：仅修改非空字段")
    void updateDeviceSuccess() {
        // Arrange
        Device existing = new Device();
        existing.setId(1L);
        existing.setName("旧名");
        existing.setDeleted(0);
        when(deviceMapper.selectById(1L)).thenReturn(existing);
        when(deviceMapper.updateById(any(Device.class))).thenReturn(1);

        DeviceUpdateReq req = new DeviceUpdateReq();
        req.setName("新名");
        req.setStatus("DISABLED");

        // Act
        Device updated = deviceService.updateDevice(1L, req);

        // Assert
        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper).updateById(captor.capture());
        Device arg = captor.getValue();
        assertEquals(1L, arg.getId());
        assertEquals("新名", arg.getName());
        assertEquals("DISABLED", arg.getStatus());
        assertEquals("新名", updated.getName());
    }

    @Test
    @DisplayName("更新设备：设备不存在时抛出 BizException")
    void updateDeviceNotFound() {
        // Arrange
        when(deviceMapper.selectById(99L)).thenReturn(null);

        // Act & Assert
        assertThrows(BizException.class, () -> deviceService.updateDevice(99L, new DeviceUpdateReq()));
        verify(deviceMapper, never()).updateById(any(Device.class));
    }

    @Test
    @DisplayName("删除设备：调用逻辑删除")
    void deleteDeviceSuccess() {
        // Arrange
        Device existing = new Device();
        existing.setId(1L);
        existing.setDeleted(0);
        when(deviceMapper.selectById(1L)).thenReturn(existing);
        when(deviceMapper.deleteById(1L)).thenReturn(1);

        // Act
        deviceService.deleteDevice(1L);

        // Assert
        verify(deviceMapper).deleteById(1L);
    }

    @Test
    @DisplayName("删除设备：设备不存在时抛出 BizException")
    void deleteDeviceNotFound() {
        // Arrange
        when(deviceMapper.selectById(99L)).thenReturn(null);

        // Act & Assert
        assertThrows(BizException.class, () -> deviceService.deleteDevice(99L));
        verify(deviceMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("触发立即采集：写入一条 COLLECT 操作任务")
    void triggerCollectCreatesOpTask() {
        // Arrange
        Device existing = new Device();
        existing.setId(1L);
        existing.setDeleted(0);
        when(deviceMapper.selectById(1L)).thenReturn(existing);
        when(opTaskMapper.insert(any(OpTask.class))).thenReturn(1);

        // Act
        deviceService.triggerCollect(1L);

        // Assert
        ArgumentCaptor<OpTask> captor = ArgumentCaptor.forClass(OpTask.class);
        verify(opTaskMapper).insert(captor.capture());
        OpTask task = captor.getValue();
        assertEquals(1L, task.getDeviceId());
        assertEquals("COLLECT", task.getOperationCode());
        assertEquals("MANUAL", task.getSource());
        assertEquals("PENDING", task.getStatus());
    }

    @Test
    @DisplayName("下发手动操作：写入 op_task 并返回任务 ID")
    void submitOpTaskSuccess() {
        // Arrange
        Device existing = new Device();
        existing.setId(1L);
        existing.setDeleted(0);
        when(deviceMapper.selectById(1L)).thenReturn(existing);
        when(opTaskMapper.insert(any(OpTask.class))).thenAnswer(inv -> {
            OpTask t = inv.getArgument(0);
            t.setId(555L);
            return 1;
        });

        // Act
        Long taskId = deviceService.submitOpTask(1L, OperationCode.REBOOT_HOST.name(), null);

        // Assert
        assertEquals(555L, taskId);
        ArgumentCaptor<OpTask> captor = ArgumentCaptor.forClass(OpTask.class);
        verify(opTaskMapper).insert(captor.capture());
        OpTask task = captor.getValue();
        assertEquals(OperationCode.REBOOT_HOST.name(), task.getOperationCode());
        assertEquals(null, task.getParams());
        assertEquals("MANUAL", task.getSource());
        assertEquals("PENDING", task.getStatus());
        assertEquals(0, task.getRetryCount());
    }

    @Test
    @DisplayName("下发手动操作：携带参数时序列化为 JSON 字符串")
    void submitOpTaskWithParams() throws Exception {
        // Arrange
        Device existing = new Device();
        existing.setId(1L);
        existing.setDeleted(0);
        when(deviceMapper.selectById(1L)).thenReturn(existing);
        when(opTaskMapper.insert(any(OpTask.class))).thenAnswer(inv -> {
            OpTask t = inv.getArgument(0);
            t.setId(555L);
            return 1;
        });
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"name\":\"instance_01\"}");

        Map<String, Object> params = new HashMap<>();
        params.put("name", "instance_01");

        // Act
        Long taskId = deviceService.submitOpTask(1L, OperationCode.SET_CLIPBOARD.name(), params);

        // Assert
        assertEquals(555L, taskId);
        ArgumentCaptor<OpTask> captor = ArgumentCaptor.forClass(OpTask.class);
        verify(opTaskMapper).insert(captor.capture());
        OpTask task = captor.getValue();
        assertEquals("{\"name\":\"instance_01\"}", task.getParams());
    }

    @Test
    @DisplayName("下发手动操作：设备不存在时抛出 BizException")
    void submitOpTaskDeviceNotFound() {
        // Arrange
        when(deviceMapper.selectById(99L)).thenReturn(null);

        // Act & Assert
        assertThrows(BizException.class, () -> deviceService.submitOpTask(99L, OperationCode.REBOOT_HOST.name(), (Map<String, Object>) null));
        verify(opTaskMapper, never()).insert(any(OpTask.class));
    }
}
