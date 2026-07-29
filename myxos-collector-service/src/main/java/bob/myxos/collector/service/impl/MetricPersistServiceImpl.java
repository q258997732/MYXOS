package bob.myxos.collector.service.impl;

import bob.myxos.collector.service.MetricPersistService;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.mapper.MetricSnapshotMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 指标快照持久化服务实现
 */
@Service
public class MetricPersistServiceImpl extends ServiceImpl<MetricSnapshotMapper, MetricSnapshot>
        implements MetricPersistService {

    /** 单批插入大小 */
    private static final int BATCH_SIZE = 500;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBatchSnapshots(List<MetricSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return;
        }
        saveBatch(snapshots, BATCH_SIZE);
    }
}
