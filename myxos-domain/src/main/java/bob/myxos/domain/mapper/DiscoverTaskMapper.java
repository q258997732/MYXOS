package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.DiscoverTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 网段发现任务 Mapper
 */
@Mapper
public interface DiscoverTaskMapper extends BaseMapper<DiscoverTask> {
}
