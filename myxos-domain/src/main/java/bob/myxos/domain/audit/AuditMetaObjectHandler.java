package bob.myxos.domain.audit;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 审计字段自动填充处理器
 * 在 insert 时填充 who_created / when_created / who_modified / when_modified / deleted
 * 在 update 时刷新 who_modified / when_modified
 */
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    /**
     * 插入时填充审计字段
     *
     * @param metaObject 实体元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        String username = LoginUserHolder.get();
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "whoCreated", String.class, username);
        this.strictInsertFill(metaObject, "whenCreated", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "whoModified", String.class, username);
        this.strictInsertFill(metaObject, "whenModified", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "deleted", Integer.class, 0);
    }

    /**
     * 更新时刷新修改人及修改时间
     *
     * @param metaObject 实体元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "whoModified", String.class, LoginUserHolder.get());
        this.strictUpdateFill(metaObject, "whenModified", LocalDateTime.class, LocalDateTime.now());
    }
}
