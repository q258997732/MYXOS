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
 * JWT 令牌吊销记录实体
 */
@Data
@TableName("login_token")
public class LoginToken {

    /** 主键 ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 令牌唯一标识（JWT ID） */
    private String tokenId;

    /** 签发时间 */
    private LocalDateTime issuedAt;

    /** 过期时间 */
    private LocalDateTime expiresAt;

    /** 是否已吊销：0 未吊销，1 已吊销 */
    private Integer revoked;

    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private String whoCreated;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime whenCreated;

    /** 修改人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String whoModified;

    /** 修改时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime whenModified;

    /** 逻辑删除标记 */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
