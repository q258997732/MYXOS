package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.MetricSnapshot;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 指标快照 Mapper
 */
@Mapper
public interface MetricSnapshotMapper extends BaseMapper<MetricSnapshot> {

    /**
     * 查询某设备某指标自 startTime 以来的所有采样（按采集时间倒序）
     *
     * @param deviceId   设备 ID
     * @param metricType 指标类型
     * @param startTime  起始时间（含）
     * @return 采样列表
     */
    @Select("SELECT * FROM metric_snapshot WHERE device_id = #{deviceId} AND metric_type = #{metricType} " +
            "AND collected_at >= #{startTime} AND deleted = 0 ORDER BY collected_at DESC")
    List<MetricSnapshot> selectRecentByDeviceAndType(@Param("deviceId") Long deviceId,
                                                     @Param("metricType") String metricType,
                                                     @Param("startTime") LocalDateTime startTime);

    /**
     * 查询某设备某指标最近 limit 条采样（按采集时间倒序）
     *
     * @param deviceId   设备 ID
     * @param metricType 指标类型
     * @param limit      条数上限
     * @return 采样列表
     */
    @Select("SELECT * FROM metric_snapshot WHERE device_id = #{deviceId} AND metric_type = #{metricType} " +
            "AND deleted = 0 ORDER BY collected_at DESC LIMIT #{limit}")
    List<MetricSnapshot> selectLatestByDeviceAndType(@Param("deviceId") Long deviceId,
                                                     @Param("metricType") String metricType,
                                                     @Param("limit") Integer limit);

    /**
     * 查询某设备某指标且 extra 精确匹配的最近 limit 条采样（按采集时间倒序）
     * <p>
     * 用于 ANDROID_STATUS 等多实例指标：extra 形如 {"name":"容器名"}，
     * 按实例过滤后再做连续次数判定，避免不同实例的采样混在一起
     *
     * @param deviceId   设备 ID
     * @param metricType 指标类型
     * @param extra      extra 字段精确值
     * @param limit      条数上限
     * @return 采样列表
     */
    @Select("SELECT * FROM metric_snapshot WHERE device_id = #{deviceId} AND metric_type = #{metricType} " +
            "AND extra = #{extra} AND deleted = 0 ORDER BY collected_at DESC LIMIT #{limit}")
    List<MetricSnapshot> selectLatestByDeviceTypeAndExtra(@Param("deviceId") Long deviceId,
                                                          @Param("metricType") String metricType,
                                                          @Param("extra") String extra,
                                                          @Param("limit") Integer limit);

    /**
     * 查询某设备某指标且 extra 精确匹配、自 startTime 以来的所有采样（按采集时间倒序）
     * <p>
     * 用于 ANDROID_STATUS 等多实例指标按实例做持续时长判定
     *
     * @param deviceId   设备 ID
     * @param metricType 指标类型
     * @param extra      extra 字段精确值
     * @param startTime  起始时间（含）
     * @return 采样列表
     */
    @Select("SELECT * FROM metric_snapshot WHERE device_id = #{deviceId} AND metric_type = #{metricType} " +
            "AND extra = #{extra} AND collected_at >= #{startTime} AND deleted = 0 ORDER BY collected_at DESC")
    List<MetricSnapshot> selectRecentByDeviceTypeAndExtra(@Param("deviceId") Long deviceId,
                                                          @Param("metricType") String metricType,
                                                          @Param("extra") String extra,
                                                          @Param("startTime") LocalDateTime startTime);

    /**
     * 查询指定设备集合出现过的安卓实例 extra 去重列表
     * <p>
     * extra 形如 {"name":"容器名"}，用于阈值规则"实例名称"多选下拉的数据源
     *
     * @param deviceIds 设备 ID 集合
     * @return 去重后的 extra 列表
     */
    @Select({"<script>",
            "SELECT DISTINCT extra FROM metric_snapshot",
            "WHERE metric_type = 'ANDROID_STATUS' AND deleted = 0 AND extra IS NOT NULL",
            "AND device_id IN",
            "<foreach collection='deviceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    List<String> selectDistinctAndroidExtras(@Param("deviceIds") List<Long> deviceIds);

    /**
     * 查询某设备每种指标类型的最新一条记录
     *
     * @param deviceId 设备 ID
     * @return 最新指标快照列表
     */
    @Select("SELECT s.* FROM metric_snapshot s " +
            "INNER JOIN (" +
            "  SELECT metric_type, MAX(collected_at) AS max_collected_at " +
            "  FROM metric_snapshot WHERE device_id = #{deviceId} AND deleted = 0 GROUP BY metric_type" +
            ") latest ON s.metric_type = latest.metric_type AND s.collected_at = latest.max_collected_at " +
            "WHERE s.device_id = #{deviceId} AND s.deleted = 0")
    List<MetricSnapshot> selectLatestPerTypeByDevice(@Param("deviceId") Long deviceId);

    /** 按指标编码、目标类型和安卓实例分别查询最新快照。 */
    @Select("SELECT s.* FROM metric_snapshot s INNER JOIN (" +
            "SELECT metric_code, target_type, android_name, MAX(collected_at) AS max_collected_at " +
            "FROM metric_snapshot WHERE device_id = #{deviceId} AND deleted = 0 " +
            "GROUP BY metric_code, target_type, android_name" +
            ") latest ON s.metric_code = latest.metric_code " +
            "AND s.target_type = latest.target_type AND s.android_name = latest.android_name " +
            "AND s.collected_at = latest.max_collected_at " +
            "WHERE s.device_id = #{deviceId} AND s.deleted = 0")
    List<MetricSnapshot> selectLatestPerMetricTargetByDevice(@Param("deviceId") Long deviceId);

    @Select("SELECT * FROM metric_snapshot WHERE device_id = #{deviceId} AND metric_code = #{metricCode} " +
            "AND target_type = #{targetType} AND android_name = #{androidName} AND collected_at >= #{startTime} " +
            "AND deleted = 0 ORDER BY collected_at DESC")
    List<MetricSnapshot> selectRecentByDeviceMetricCodeTargetAndAndroidName(@Param("deviceId") Long deviceId,
                                                                              @Param("metricCode") String metricCode,
                                                                              @Param("targetType") String targetType,
                                                                              @Param("androidName") String androidName,
                                                                              @Param("startTime") LocalDateTime startTime);

    @Select("SELECT * FROM metric_snapshot WHERE device_id = #{deviceId} AND metric_code = #{metricCode} " +
            "AND target_type = #{targetType} AND android_name = #{androidName} AND deleted = 0 " +
            "ORDER BY collected_at DESC LIMIT #{limit}")
    List<MetricSnapshot> selectLatestByDeviceMetricCodeTargetAndAndroidName(@Param("deviceId") Long deviceId,
                                                                              @Param("metricCode") String metricCode,
                                                                              @Param("targetType") String targetType,
                                                                              @Param("androidName") String androidName,
                                                                              @Param("limit") Integer limit);

    @Select("SELECT * FROM metric_snapshot WHERE device_id = #{deviceId} AND metric_code = #{metricCode} "
            + "AND android_name = #{androidName} AND deleted = 0 ORDER BY collected_at DESC LIMIT 1")
    MetricSnapshot selectLatestByDeviceMetricCodeAndAndroidName(@Param("deviceId") Long deviceId,
                                                                 @Param("metricCode") String metricCode,
                                                                 @Param("androidName") String androidName);

    /**
     * 批量删除截止时间之前的指标快照
     *
     * @param deadline 截止时间
     * @return 删除行数
     */
    @Delete("DELETE FROM metric_snapshot WHERE collected_at < #{deadline} AND deleted = 0 LIMIT 5000")
    int deleteByCollectedAtBefore(@Param("deadline") LocalDateTime deadline);
}
