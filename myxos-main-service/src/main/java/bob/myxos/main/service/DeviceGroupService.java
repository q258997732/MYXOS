package bob.myxos.main.service;

import bob.myxos.domain.entity.DeviceGroup;

import java.util.List;

/**
 * 设备分组业务接口
 */
public interface DeviceGroupService {

    /**
     * 查询所有未删除的分组（用于构建树形结构）
     *
     * @return 分组列表（按 ID 升序）
     */
    List<DeviceGroup> listAll();

    /**
     * 创建分组
     *
     * @param name     分组名称
     * @param parentId 父分组 ID（0 表示根）
     * @param remark   备注
     * @return 已创建的分组
     */
    DeviceGroup createGroup(String name, Long parentId, String remark);

    /**
     * 更新分组
     *
     * @param id       分组 ID
     * @param name     分组名称
     * @param parentId 父分组 ID
     * @param remark   备注
     * @return 更新后的分组
     */
    DeviceGroup updateGroup(Long id, String name, Long parentId, String remark);

    /**
     * 删除分组（逻辑删除，需先校验无子分组与设备）
     *
     * @param id 分组 ID
     */
    void deleteGroup(Long id);
}
