package bob.myxos.main.service;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.DeviceGroup;
import bob.myxos.domain.mapper.DeviceGroupMapper;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.main.service.impl.DeviceGroupServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DeviceGroupService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeviceGroupService 测试")
class DeviceGroupServiceTest {

    @Mock
    private DeviceGroupMapper deviceGroupMapper;

    @Mock
    private DeviceMapper deviceMapper;

    private DeviceGroupServiceImpl groupService;

    @BeforeEach
    void setUp() {
        groupService = new DeviceGroupServiceImpl(deviceGroupMapper, deviceMapper);
    }

    @Test
    @DisplayName("查询所有分组：按 ID 升序返回")
    void listAllReturnsSorted() {
        // Arrange
        DeviceGroup g1 = new DeviceGroup();
        g1.setId(1L);
        DeviceGroup g2 = new DeviceGroup();
        g2.setId(2L);
        when(deviceGroupMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(g1, g2));

        // Act
        List<DeviceGroup> result = groupService.listAll();

        // Assert
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    @DisplayName("创建分组：parentId 为空时默认为 0（根分组）")
    void createGroupDefaultsParentToZero() {
        // Arrange
        when(deviceGroupMapper.insert(any(DeviceGroup.class))).thenAnswer(inv -> {
            DeviceGroup g = inv.getArgument(0);
            g.setId(10L);
            return 1;
        });

        // Act
        DeviceGroup saved = groupService.createGroup("根分组", null, "备注");

        // Assert
        assertEquals(10L, saved.getId());
        assertEquals(0L, saved.getParentId());
        assertEquals("根分组", saved.getName());
    }

    @Test
    @DisplayName("更新分组：分组不存在时抛出 BizException")
    void updateGroupNotFound() {
        // Arrange
        when(deviceGroupMapper.selectById(99L)).thenReturn(null);

        // Act & Assert
        assertThrows(BizException.class, () -> groupService.updateGroup(99L, "x", 0L, null));
        verify(deviceGroupMapper, never()).updateById(any(DeviceGroup.class));
    }

    @Test
    @DisplayName("更新分组：不能将父分组设置为自己")
    void updateGroupCannotSetSelfAsParent() {
        // Arrange
        DeviceGroup existing = new DeviceGroup();
        existing.setId(1L);
        existing.setDeleted(0);
        when(deviceGroupMapper.selectById(1L)).thenReturn(existing);

        // Act & Assert
        BizException ex = assertThrows(BizException.class,
                () -> groupService.updateGroup(1L, "x", 1L, null));
        assertTrue(ex.getMessage().contains("父分组"));
    }

    @Test
    @DisplayName("删除分组：存在子分组时抛出 BizException")
    void deleteGroupFailsWhenHasChildren() {
        // Arrange
        DeviceGroup existing = new DeviceGroup();
        existing.setId(1L);
        existing.setDeleted(0);
        when(deviceGroupMapper.selectById(1L)).thenReturn(existing);
        when(deviceGroupMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        // Act & Assert
        BizException ex = assertThrows(BizException.class, () -> groupService.deleteGroup(1L));
        assertTrue(ex.getMessage().contains("子分组"));
        verify(deviceGroupMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("删除分组：分组下存在设备时抛出 BizException")
    void deleteGroupFailsWhenHasDevices() {
        // Arrange
        DeviceGroup existing = new DeviceGroup();
        existing.setId(1L);
        existing.setDeleted(0);
        when(deviceGroupMapper.selectById(1L)).thenReturn(existing);
        // 第一次调用返回 0（无子分组），第二次返回 3（设备数）
        when(deviceGroupMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(deviceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        // Act & Assert
        BizException ex = assertThrows(BizException.class, () -> groupService.deleteGroup(1L));
        assertTrue(ex.getMessage().contains("设备"));
        verify(deviceGroupMapper, never()).deleteById(any(Long.class));
    }

    @Test
    @DisplayName("删除分组：无子分组且无设备时成功")
    void deleteGroupSuccess() {
        // Arrange
        DeviceGroup existing = new DeviceGroup();
        existing.setId(1L);
        existing.setDeleted(0);
        when(deviceGroupMapper.selectById(1L)).thenReturn(existing);
        when(deviceGroupMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(deviceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(deviceGroupMapper.deleteById(1L)).thenReturn(1);

        // Act
        groupService.deleteGroup(1L);

        // Assert
        verify(deviceGroupMapper).deleteById(1L);
    }

    @Test
    @DisplayName("更新分组：正常更新字段")
    void updateGroupSuccess() {
        // Arrange
        DeviceGroup existing = new DeviceGroup();
        existing.setId(1L);
        existing.setName("旧名");
        existing.setDeleted(0);
        when(deviceGroupMapper.selectById(1L)).thenReturn(existing);
        when(deviceGroupMapper.updateById(any(DeviceGroup.class))).thenReturn(1);

        // Act
        DeviceGroup updated = groupService.updateGroup(1L, "新名", 0L, "新备注");

        // Assert
        ArgumentCaptor<DeviceGroup> captor = ArgumentCaptor.forClass(DeviceGroup.class);
        verify(deviceGroupMapper).updateById(captor.capture());
        assertEquals("新名", captor.getValue().getName());
        assertNotNull(updated);
    }
}
