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
 * 指标模板明细实体。
 */
@Data
@TableName("metric_template_item")
public class MetricTemplateItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private Long metricCatalogId;
    private Integer enabled;
    private Integer defaultIntervalSec;
    private String enumOptions;

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
