package bob.myxos.main.service.impl;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.DeviceGroup;
import bob.myxos.domain.mapper.DeviceGroupMapper;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.main.service.DeviceGroupService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 设备分组业务实现
 */
@Service
@Primary
@RequiredArgsConstructor
public class DeviceGroupServiceImpl implements DeviceGroupService {

    private final DeviceGroupMapper deviceGroupMapper;
    private final DeviceMapper deviceMapper;

    @Override
    @Transactional(readOnly = true)
    public List<DeviceGroup> listAll() {
        return deviceGroupMapper.selectList(
                new LambdaQueryWrapper<DeviceGroup>()
                        .eq(DeviceGroup::getDeleted, 0)
                        .orderByAsc(DeviceGroup::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceGroup createGroup(String name, Long parentId, String remark) {
        Long realParentId = parentId == null ? 0L : parentId;
        // 新增：同一父节点下名称不能重复
        Long count = deviceGroupMapper.selectCount(
                new LambdaQueryWrapper<DeviceGroup>()
                        .eq(DeviceGroup::getName, name)
                        .eq(DeviceGroup::getParentId, realParentId)
                        .eq(DeviceGroup::getDeleted, 0));
        if (count != null && count > 0) {
            throw new BizException("分组名称已存在");
        }
        validateParentGroup(realParentId, null);
        DeviceGroup group = new DeviceGroup();
        group.setName(name);
        group.setParentId(realParentId);
        group.setRemark(remark);
        deviceGroupMapper.insert(group);
        return group;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceGroup updateGroup(Long id, String name, Long parentId, String remark) {
        DeviceGroup existing = deviceGroupMapper.selectById(id);
        if (existing == null || (existing.getDeleted() != null && existing.getDeleted() == 1)) {
            throw new BizException("分组不存在");
        }
        if (parentId != null) {
            if (parentId.equals(id)) {
                throw new BizException("父分组不能是自己");
            }
            validateParentGroup(parentId, id);
            existing.setParentId(parentId);
        }
        if (name != null) {
            existing.setName(name);
        }
        if (remark != null) {
            existing.setRemark(remark);
        }
        deviceGroupMapper.updateById(existing);
        return existing;
    }

    /**
     * 校验父分组是否存在且不会形成循环引用
     *
     * @param parentId  待校验的父分组 ID
     * @param currentId 当前分组 ID（更新时传入，创建时传 null）
     */
    private void validateParentGroup(Long parentId, Long currentId) {
        if (parentId == 0L) {
            return;
        }
        DeviceGroup parent = deviceGroupMapper.selectById(parentId);
        if (parent == null || (parent.getDeleted() != null && parent.getDeleted() == 1)) {
            throw new BizException("父分组不存在");
        }
        if (currentId == null) {
            return;
        }
        // 检查是否将当前分组挂到自己的子孙节点下（循环引用）
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor != 0L) {
            if (cursor.equals(currentId)) {
                throw new BizException("父分组不能是当前分组的子分组");
            }
            if (!visited.add(cursor)) {
                break; // 已有脏数据成环，避免死循环
            }
            DeviceGroup group = deviceGroupMapper.selectById(cursor);
            cursor = group == null ? null : group.getParentId();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGroup(Long id) {
        DeviceGroup existing = deviceGroupMapper.selectById(id);
        if (existing == null || (existing.getDeleted() != null && existing.getDeleted() == 1)) {
            throw new BizException("分组不存在");
        }
        Long childCount = deviceGroupMapper.selectCount(
                new LambdaQueryWrapper<DeviceGroup>()
                        .eq(DeviceGroup::getParentId, id)
                        .eq(DeviceGroup::getDeleted, 0));
        if (childCount != null && childCount > 0) {
            throw new BizException("请先删除子分组");
        }
        Long deviceCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getGroupId, id)
                        .eq(Device::getDeleted, 0));
        if (deviceCount != null && deviceCount > 0) {
            throw new BizException("请先移除分组下的设备");
        }
        deviceGroupMapper.deleteById(id);
    }
}
