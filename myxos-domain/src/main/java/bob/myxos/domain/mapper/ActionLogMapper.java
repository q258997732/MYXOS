package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.ActionLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动作日志 Mapper
 */
@Mapper
public interface ActionLogMapper extends BaseMapper<ActionLog> {
}
