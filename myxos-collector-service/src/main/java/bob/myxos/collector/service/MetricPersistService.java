package bob.myxos.collector.service;

import bob.myxos.domain.entity.MetricSnapshot;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 指标快照持久化服务
 */
public interface MetricPersistService extends IService<MetricSnapshot> {

    /**
     * 批量保存指标快照
     *
     * @param snapshots 待保存的指标快照列表
     */
    void saveBatchSnapshots(List<MetricSnapshot> snapshots);
}
