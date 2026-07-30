package bob.myxos.main.service.impl;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.SysConfig;
import bob.myxos.domain.mapper.SysConfigMapper;
import bob.myxos.main.service.SysConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 系统配置服务实现
 */
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    @Override
    public List<SysConfig> list() {
        return sysConfigMapper.selectList(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getDeleted, 0)
                        .orderByAsc(SysConfig::getConfigKey));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateValue(String key, String value) {
        SysConfig existing = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, key)
                        .eq(SysConfig::getDeleted, 0));
        if (existing == null) {
            throw new BizException("配置不存在：" + key);
        }

        SysConfig update = new SysConfig();
        update.setId(existing.getId());
        update.setConfigValue(value);
        sysConfigMapper.updateById(update);
    }
}
