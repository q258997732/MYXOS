package bob.myxos.main.controller;

import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.main.dto.DiscoverReq;
import bob.myxos.main.service.DiscoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 设备发现控制器
 */
@RestController
@RequestMapping("/api/discover")
@RequiredArgsConstructor
public class DiscoverController {

    private final DiscoverService discoverService;

    /**
     * 提交 CIDR 网段扫描任务
     *
     * @param req 扫描请求
     * @return 已创建的发现任务
     */
    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<DiscoverTask> scan(@Valid @RequestBody DiscoverReq req) {
        return Result.ok(discoverService.submit(req));
    }
}
