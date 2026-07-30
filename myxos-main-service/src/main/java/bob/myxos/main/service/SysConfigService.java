package bob.myxos.main.service;

import bob.myxos.domain.entity.SysConfig;

import java.util.List;

/**
 * 系统配置服务
 */
public interface SysConfigService {

    /**
     * 查询所有有效配置
     *
     * @return 配置列表
     */
    List<SysConfig> list();

    /**
     * 更新单个配置值
     *
     * @param key   配置键
     * @param value 配置值
     */
    void updateValue(String key, String value);
}
