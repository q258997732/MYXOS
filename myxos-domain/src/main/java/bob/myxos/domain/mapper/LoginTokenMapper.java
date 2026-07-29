package bob.myxos.domain.mapper;

import bob.myxos.domain.entity.LoginToken;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录令牌 Mapper
 */
@Mapper
public interface LoginTokenMapper extends BaseMapper<LoginToken> {
}
