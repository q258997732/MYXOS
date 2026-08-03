package bob.myxos.main.controller;

import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.SysConfig;
import bob.myxos.main.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统配置控制器
 */
@RestController
@RequestMapping("/api/sys-config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService sysConfigService;

    /**
     * 查询所有配置
     *
     * @return 配置列表
     */
    @GetMapping
    public Result<List<SysConfig>> list() {
        return Result.ok(sysConfigService.list());
    }

    /**
     * 更新单个配置值
     *
     * @param key   配置键
     * @param value 配置值
     * @return 空响应
     */
    @PutMapping("/{key}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@PathVariable String key, @RequestBody Map<String, String> body) {
        sysConfigService.updateValue(key, body.get("value"));
        return Result.ok();
    }
}
