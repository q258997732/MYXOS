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
 * 指标目录实体。
 */
@Data
@TableName("metric_catalog")
public class MetricCatalog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String targetType;
    private String valueType;
    private String category;
    private String unit;
    private String commandKey;
    private Integer thresholdEnabled;
    private Integer defaultIntervalSec;

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
