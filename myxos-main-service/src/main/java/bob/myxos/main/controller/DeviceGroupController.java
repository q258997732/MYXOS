package bob.myxos.main.controller;

import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.DeviceGroup;
import bob.myxos.main.dto.DeviceGroupReq;
import bob.myxos.main.service.DeviceGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备分组管理控制器
 */
@RestController
@RequestMapping("/api/device-groups")
@RequiredArgsConstructor
public class DeviceGroupController {

    private final DeviceGroupService groupService;

    /**
     * 查询分组树形列表
     *
     * @return 树形结构的分组列表
     */
    @GetMapping
    public Result<List<Map<String, Object>>> tree() {
        List<DeviceGroup> all = groupService.listAll();
        // 构建 id -> node 映射
        Map<Long, Map<String, Object>> nodeMap = new HashMap<>(all.size() * 2);
        for (DeviceGroup g : all) {
            Map<String, Object> node = new HashMap<>(8);
            node.put("id", g.getId());
            node.put("name", g.getName());
            node.put("parentId", g.getParentId());
            node.put("remark", g.getRemark());
            node.put("children", new ArrayList<Map<String, Object>>());
            nodeMap.put(g.getId(), node);
        }
        // 挂载到父节点
        List<Map<String, Object>> roots = new ArrayList<>();
        for (DeviceGroup g : all) {
            Map<String, Object> node = nodeMap.get(g.getId());
            Long parentId = g.getParentId();
            if (parentId == null || parentId == 0L || !nodeMap.containsKey(parentId)) {
                roots.add(node);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children =
                        (List<Map<String, Object>>) nodeMap.get(parentId).get("children");
                children.add(node);
            }
        }
        return Result.ok(roots);
    }

    /**
     * 创建分组
     *
     * @param req 创建请求
     * @return 已创建的分组
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<DeviceGroup> create(@Valid @RequestBody DeviceGroupReq req) {
        return Result.ok(groupService.createGroup(req.getName(), req.getParentId(), req.getRemark()));
    }

    /**
     * 更新分组
     *
     * @param id  分组 ID
     * @param req 更新请求
     * @return 更新后的分组
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<DeviceGroup> update(@PathVariable Long id, @Valid @RequestBody DeviceGroupReq req) {
        return Result.ok(groupService.updateGroup(id, req.getName(), req.getParentId(), req.getRemark()));
    }

    /**
     * 删除分组
     *
     * @param id 分组 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Void> delete(@PathVariable Long id) {
        groupService.deleteGroup(id);
        return Result.ok();
    }
}
