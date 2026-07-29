package bob.myxos.main.service;

import bob.myxos.domain.entity.AlarmEvent;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 告警事件业务接口
 */
public interface AlarmService {

    /**
     * 分页查询告警事件
     *
     * @param status   状态（可选：FIRING / RESOLVED）
     * @param deviceId 设备 ID（可选）
     * @param page     当前页
     * @param size     每页大小
     * @return 分页结果
     */
    Page<AlarmEvent> list(String status, Long deviceId, Long page, Long size);

    /**
     * 手动恢复告警
     *
     * @param id 告警 ID
     */
    void resolve(Long id);
}
