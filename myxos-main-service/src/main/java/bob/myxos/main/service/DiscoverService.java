package bob.myxos.main.service;

import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.main.dto.DiscoverReq;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 设备发现服务
 */
public interface DiscoverService {

    /**
     * 提交网段扫描任务
     *
     * @param req 扫描请求
     * @return 已创建的发现任务
     */
    DiscoverTask submit(DiscoverReq req);

    /**
     * 分页查询发现任务
     *
     * @param page 当前页
     * @param size 每页大小
     * @return 分页结果
     */
    Page<DiscoverTask> list(Long page, Long size);

    /**
     * 删除指定发现任务（逻辑删除）
     *
     * @param id 任务 ID
     */
    void delete(Long id);

    /**
     * 清空所有已完成的发现任务（逻辑删除）
     */
    void clear();
}
