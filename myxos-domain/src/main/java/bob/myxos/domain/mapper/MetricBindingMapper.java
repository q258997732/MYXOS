package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.MetricBinding;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MetricBindingMapper extends BaseMapper<MetricBinding> {

    @Select("SELECT * FROM metric_binding WHERE enabled = 1 AND deleted = 0 "
            + "AND (next_collect_at IS NULL OR next_collect_at <= #{now}) "
            + "ORDER BY next_collect_at ASC, id ASC LIMIT #{limit}")
    List<MetricBinding> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    @Update("UPDATE metric_binding SET last_collected_at = #{completedAt}, next_collect_at = #{nextCollectAt} "
            + "WHERE id = #{id} AND deleted = 0")
    int markCollected(@Param("id") Long id, @Param("completedAt") LocalDateTime completedAt,
                      @Param("nextCollectAt") LocalDateTime nextCollectAt);

    @Update("UPDATE metric_binding SET last_collected_at = #{completedAt}, next_collect_at = #{nextCollectAt} "
            + "WHERE id = #{id} AND deleted = 0")
    int markSkipped(@Param("id") Long id, @Param("completedAt") LocalDateTime completedAt,
                    @Param("nextCollectAt") LocalDateTime nextCollectAt);
}
