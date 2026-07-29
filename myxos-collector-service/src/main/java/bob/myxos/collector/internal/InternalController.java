package bob.myxos.collector.internal;

import bob.myxos.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 采集服务内部接口
 * <p>
 * 路径以 /internal 开头，由 {@link InternalTokenFilter} 校验 X-Internal-Token 请求头。
 */
@RestController
@RequestMapping("/internal")
public class InternalController {

    /**
     * 健康检查
     *
     * @return 固定字符串
     */
    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("collector-service is running");
    }
}
