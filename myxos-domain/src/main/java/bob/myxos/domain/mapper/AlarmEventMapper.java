package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.AlarmEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警事件 Mapper
 */
@Mapper
public interface AlarmEventMapper extends BaseMapper<AlarmEvent> {
}
