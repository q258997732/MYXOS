package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.Device;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 设备 Mapper
 */
@Mapper
public interface DeviceMapper extends BaseMapper<Device> {

    /**
     * 查询指定设备当前 FIRING 状态的告警数量
     *
     * @param deviceIds 设备 ID 列表
     * @return 设备 ID 与告警数量的键值对列表
     */
    @Select("<script>" +
            "SELECT a.device_id AS deviceId, COUNT(*) AS alarmCount " +
            "FROM alarm_event a " +
            "WHERE a.status = 'FIRING' AND a.deleted = 0 " +
            "AND a.device_id IN " +
            "<foreach collection='deviceIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY a.device_id" +
            "</script>")
    List<Map<String, Object>> countFiringAlarmsByDeviceIds(@Param("deviceIds") List<Long> deviceIds);
}
