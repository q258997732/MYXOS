package bob.myxos.main.service;

import bob.myxos.domain.entity.ActionLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 动作日志业务接口
 */
public interface ActionLogService {

    /**
     * 分页查询动作日志
     *
     * @param actionType 动作类型（可选：LOG / OPERATION / SYSTEM）
     * @param logLevel   日志级别（可选：DEBUG / INFO / WARN / ERROR）
     * @param page       当前页
     * @param size       每页大小
     * @return 分页结果
     */
    Page<ActionLog> list(String actionType, String logLevel, Long page, Long size);
}
