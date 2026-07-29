package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.ThresholdRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 阈值规则 Mapper
 */
@Mapper
public interface ThresholdRuleMapper extends BaseMapper<ThresholdRule> {
}
