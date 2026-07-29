package bob.myxos.common.util;

import bob.myxos.common.exception.BizException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 工具类
 * 基于 Jackson 提供对象与 JSON 字符串的互转能力
 */
public final class JsonUtils {

    /** 共享的 ObjectMapper 实例（线程安全） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonUtils() {
    }

    /**
     * 对象序列化为 JSON 字符串
     *
     * @param obj 待序列化对象
     * @return JSON 字符串
     * @throws BizException 序列化失败时抛出
     */
    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BizException("JSON 序列化失败");
        }
    }

    /**
     * JSON 字符串反序列化为指定类型对象
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标类型泛型
     * @return 反序列化后的对象
     * @throws BizException 反序列化失败时抛出
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new BizException("JSON 反序列化失败");
        }
    }

    /**
     * JSON 字符串反序列化为复杂泛型对象
     *
     * @param json          JSON 字符串
     * @param typeReference 类型引用
     * @param <T>           目标类型泛型
     * @return 反序列化后的对象
     * @throws BizException 反序列化失败时抛出
     */
    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        try {
            return MAPPER.readValue(json, typeReference);
        } catch (Exception e) {
            throw new BizException("JSON 反序列化失败");
        }
    }
}
