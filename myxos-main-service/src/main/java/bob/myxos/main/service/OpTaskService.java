package bob.myxos.main.service;

import bob.myxos.domain.entity.OpTask;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 操作任务业务接口
 */
public interface OpTaskService {

    /**
     * 分页查询操作任务
     *
     * @param status 状态（可选：PENDING / RUNNING / SUCCESS / FAILED / TIMEOUT）
     * @param source 来源（可选：MANUAL / AUTO）
     * @param page   当前页
     * @param size   每页大小
     * @return 分页结果
     */
    Page<OpTask> list(String status, String source, Long page, Long size);
}
