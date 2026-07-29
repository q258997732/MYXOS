package bob.myxos.collector.execute;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 动作执行器注册表
 * <p>
 * 持有所有 {@link ActionExecutor} 实现，按 actionType 分发。
 */
@Component
@RequiredArgsConstructor
public class ActionExecutorRegistry {

    private final List<ActionExecutor> executors;

    /**
     * 按动作类型查找执行器
     *
     * @param actionType 动作类型
     * @return 匹配的执行器，找不到返回 {@link Optional#empty()}
     */
    public Optional<ActionExecutor> getExecutor(String actionType) {
        if (actionType == null || executors == null) {
            return Optional.empty();
        }
        return executors.stream().filter(e -> e.supports(actionType)).findFirst();
    }
}
