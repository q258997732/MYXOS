package bob.myxos.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备指标绑定实体。
 */
@Data
@TableName("metric_binding")
public class MetricBinding {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long deviceId;
    private String androidName;
    private String targetType;
    private String metricCode;
    private String appPackage;
    private Integer enabled;
    private Integer intervalSec;
    private LocalDateTime lastCollectedAt;
    private LocalDateTime nextCollectAt;

    @TableField(fill = FieldFill.INSERT)
    private String whoCreated;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime whenCreated;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String whoModified;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime whenModified;
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
