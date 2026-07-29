package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.MetricSnapshot;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 指标快照 Mapper
 */
@Mapper
public interface MetricSnapshotMapper extends BaseMapper<MetricSnapshot> {
}
