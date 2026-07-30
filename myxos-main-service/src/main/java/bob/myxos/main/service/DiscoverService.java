package bob.myxos.main.service;

import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.main.dto.DiscoverReq;

/**
 * 设备发现服务
 */
public interface DiscoverService {

    /**
     * 提交网段扫描任务
     *
     * @param req 扫描请求
     * @return 已创建的发现任务
     */
    DiscoverTask submit(DiscoverReq req);
}
