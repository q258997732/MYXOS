package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.OpTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作任务 Mapper
 */
@Mapper
public interface OpTaskMapper extends BaseMapper<OpTask> {
}
