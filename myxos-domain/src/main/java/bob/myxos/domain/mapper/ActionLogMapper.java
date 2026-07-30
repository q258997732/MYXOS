package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.ActionLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 动作日志 Mapper
 */
@Mapper
public interface ActionLogMapper extends BaseMapper<ActionLog> {

    /**
     * 批量删除截止时间之前的动作日志
     *
     * @param deadline 截止时间
     * @return 删除行数
     */
    @Delete("DELETE FROM action_log WHERE created_at < #{deadline} AND deleted = 0 LIMIT 5000")
    int deleteByCreatedAtBefore(@Param("deadline") LocalDateTime deadline);
}
