package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.OpTask;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 操作任务 Mapper
 */
@Mapper
public interface OpTaskMapper extends BaseMapper<OpTask> {

    /**
     * CAS 抢占 PENDING 任务：仅当任务当前为 PENDING 时更新为 RUNNING。
     * 返回 1 表示抢占成功，0 表示任务已被其他执行器抢占或状态已变化。
     *
     * @param id 任务 ID
     * @return 影响行数
     */
    @Update("UPDATE op_task SET status = 'RUNNING', started_at = NOW(), who_modified = 'collector', when_modified = NOW() " +
            "WHERE id = #{id} AND status = 'PENDING' AND deleted = 0")
    int claimPending(@Param("id") Long id);
}
