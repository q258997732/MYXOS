package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.AlarmEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * 告警事件 Mapper
 */
@Mapper
public interface AlarmEventMapper extends BaseMapper<AlarmEvent> {

    /**
     * 查询指定规则与设备当前最新的一条 FIRING 告警
     *
     * @param ruleId   规则 ID
     * @param deviceId 设备 ID
     * @return FIRING 告警，不存在返回 null
     */
    @Select("SELECT * FROM alarm_event WHERE rule_id = #{ruleId} AND device_id = #{deviceId} " +
            "AND status = 'FIRING' AND deleted = 0 ORDER BY fired_at DESC LIMIT 1")
    AlarmEvent selectFiringByRuleAndDevice(@Param("ruleId") Long ruleId, @Param("deviceId") Long deviceId);

    /**
     * 批量删除截止时间之前的告警事件
     *
     * @param deadline 截止时间
     * @return 删除行数
     */
    @Delete("DELETE FROM alarm_event WHERE fired_at < #{deadline} AND deleted = 0 LIMIT 5000")
    int deleteByFiredAtBefore(@Param("deadline") LocalDateTime deadline);
}
