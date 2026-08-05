package bob.myxos.main.metric;

/**
 * 指标目录的受控定义。
 */
public final class MetricDefinition {

    private final String code;
    private final String name;
    private final String targetType;
    private final String valueType;
    private final String category;
    private final String unit;
    private final String commandKey;
    private final boolean thresholdEnabled;

    public MetricDefinition(String code, String name, String targetType, String valueType,
                            String category, String unit, String commandKey,
                            boolean thresholdEnabled) {
        this.code = code;
        this.name = name;
        this.targetType = targetType;
        this.valueType = valueType;
        this.category = category;
        this.unit = unit;
        this.commandKey = commandKey;
        this.thresholdEnabled = thresholdEnabled;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getValueType() {
        return valueType;
    }

    public String getCategory() {
        return category;
    }

    public String getUnit() {
        return unit;
    }

    public String getCommandKey() {
        return commandKey;
    }

    public boolean isThresholdEnabled() {
        return thresholdEnabled;
    }
}
