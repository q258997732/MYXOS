package bob.myxos.main.service.impl;

import bob.myxos.common.exception.BizException;
import bob.myxos.common.util.IpUtils;
import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.domain.mapper.DiscoverTaskMapper;
import bob.myxos.main.dto.DiscoverReq;
import bob.myxos.main.service.DiscoverService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备发现服务实现
 */
@Service
@RequiredArgsConstructor
public class DiscoverServiceImpl implements DiscoverService {

    private final DiscoverTaskMapper discoverTaskMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DiscoverTask submit(DiscoverReq req) {
        // 预先校验 CIDR 格式，避免写入非法任务
        List<String> ips = IpUtils.expandCidr(req.getCidr());

        DiscoverTask task = new DiscoverTask();
        task.setCidr(req.getCidr());
        task.setPortFrom(req.getPortFrom());
        task.setPortTo(req.getPortTo());
        task.setStatus("PENDING");
        task.setFoundCount(0);
        task.setTotalIpCount(ips.size());
        task.setScannedIpCount(0);
        task.setMessage("待扫描 IP 数：" + ips.size());
        discoverTaskMapper.insert(task);
        return task;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DiscoverTask> list(Long page, Long size) {
        LambdaQueryWrapper<DiscoverTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DiscoverTask::getDeleted, 0);
        wrapper.orderByDesc(DiscoverTask::getStartedAt);
        return discoverTaskMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public DiscoverTask getTaskDetail(Long id) {
        DiscoverTask task = discoverTaskMapper.selectById(id);
        if (task == null || (task.getDeleted() != null && task.getDeleted() == 1)) {
            throw new BizException("发现任务不存在");
        }
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        discoverTaskMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clear() {
        LambdaQueryWrapper<DiscoverTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DiscoverTask::getStatus, "DONE", "FAILED");
        wrapper.eq(DiscoverTask::getDeleted, 0);
        discoverTaskMapper.delete(wrapper);
    }
}
