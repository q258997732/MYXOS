package bob.myxos.mytos;

import bob.myxos.common.enums.OperationCode;
import bob.myxos.mytos.dto.InfoResp;
import bob.myxos.mytos.dto.MytosBaseResp;
import bob.myxos.mytos.dto.VersionResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * MYTOS 设备 HTTP API 客户端
 * 封装对单台设备的所有 HTTP 调用
 * <p>
 * 非 Spring Bean，由 {@link MytosClientFactory} 创建。
 * 每个实例绑定一台设备（IP + 端口）。
 */
public class MytosClient {

    /** 成功状态码 */
    private static final int CODE_SUCCESS = 200;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    /**
     * 构造 MYTOS 客户端
     *
     * @param httpClient   OkHttp 客户端
     * @param objectMapper JSON 序列化器
     * @param baseUrl      设备基础地址（如 http://192.168.30.2:9082）
     */
    public MytosClient(OkHttpClient httpClient, ObjectMapper objectMapper, String baseUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper 不能为空");
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("baseUrl 不能为空");
        }
        if (HttpUrl.parse(baseUrl) == null) {
            throw new IllegalArgumentException("非法的 baseUrl: " + baseUrl);
        }
        this.baseUrl = baseUrl;
    }

    // ==================== 基础信息接口 ====================

    /**
     * 获取设备信息
     * GET /info
     *
     * @return 设备信息响应
     */
    public InfoResp info() {
        return doGet("/info", null, InfoResp.class);
    }

    /**
     * 查询设备版本
     * GET /queryversion
     *
     * @return 版本响应（版本号在 msg 字段）
     */
    public VersionResp queryVersion() {
        return doGet("/queryversion", null, VersionResp.class);
    }

    // ==================== 已实现操作 ====================

    /**
     * 重启设备
     * GET /reboot
     *
     * @return 基础响应
     */
    public MytosBaseResp reboot() {
        return doGet("/reboot", null, MytosBaseResp.class);
    }

    /**
     * 打开 ADB root 权限
     * GET /adb?cmd=2
     *
     * @return 基础响应
     */
    public MytosBaseResp adbOn() {
        HttpUrl url = newUrlBuilder("/adb")
                .addQueryParameter("cmd", "2")
                .build();
        return doGet("/adb", url, MytosBaseResp.class);
    }

    /**
     * 关闭 ADB root 权限
     * GET /adb?cmd=3
     *
     * @return 基础响应
     */
    public MytosBaseResp adbOff() {
        HttpUrl url = newUrlBuilder("/adb")
                .addQueryParameter("cmd", "3")
                .build();
        return doGet("/adb", url, MytosBaseResp.class);
    }

    /**
     * 打开指定应用的后台保活
     * GET /background?cmd=2&package=xxx
     *
     * @param packageName 应用包名
     * @return 基础响应
     */
    public MytosBaseResp keepaliveOn(String packageName) {
        requireNonBlank(packageName, "packageName 不能为空");
        HttpUrl url = newUrlBuilder("/background")
                .addQueryParameter("cmd", "2")
                .addQueryParameter("package", packageName)
                .build();
        return doGet("/background", url, MytosBaseResp.class);
    }

    /**
     * 关闭指定应用的后台保活
     * GET /background?cmd=3&package=xxx
     *
     * @param packageName 应用包名
     * @return 基础响应
     */
    public MytosBaseResp keepaliveOff(String packageName) {
        requireNonBlank(packageName, "packageName 不能为空");
        HttpUrl url = newUrlBuilder("/background")
                .addQueryParameter("cmd", "3")
                .addQueryParameter("package", packageName)
                .build();
        return doGet("/background", url, MytosBaseResp.class);
    }

    /**
     * 设置设备剪贴板内容
     * GET /clipboard?cmd=2&text=xxx
     *
     * @param text 要设置的文本内容
     * @return 基础响应
     */
    public MytosBaseResp setClipboard(String text) {
        requireNonBlank(text, "text 不能为空");
        HttpUrl url = newUrlBuilder("/clipboard")
                .addQueryParameter("cmd", "2")
                .addQueryParameter("text", text)
                .build();
        return doGet("/clipboard", url, MytosBaseResp.class);
    }

    // ==================== 待实现操作（按 OperationCode 预留） ====================

    /**
     * 清除代理（待实现）
     */
    public MytosBaseResp clearProxy() {
        throw new MytosException("操作 CLEAR_PROXY 暂未实现");
    }

    /**
     * 设置代理（待实现）
     */
    public MytosBaseResp setProxy() {
        throw new MytosException("操作 SET_PROXY 暂未实现");
    }

    /**
     * 上传文件（待实现）
     */
    public MytosBaseResp uploadFile() {
        throw new MytosException("操作 UPLOAD_FILE 暂未实现");
    }

    /**
     * 刷新定位（待实现）
     */
    public MytosBaseResp refreshLoc() {
        throw new MytosException("操作 REFRESH_LOC 暂未实现");
    }

    /**
     * 设置指纹（待实现）
     */
    public MytosBaseResp setFingerprint() {
        throw new MytosException("操作 SET_FINGERPRINT 暂未实现");
    }

    /**
     * 设置语言（待实现）
     */
    public MytosBaseResp setLanguage() {
        throw new MytosException("操作 SET_LANGUAGE 暂未实现");
    }

    /**
     * 设置代理过滤（待实现）
     */
    public MytosBaseResp setProxyFilter() {
        throw new MytosException("操作 SET_PROXY_FILTER 暂未实现");
    }

    // ==================== 统一执行入口 ====================

    /**
     * 统一执行入口：根据操作码与参数调用对应方法
     * 便于上层（如 OpTaskRunner）通过枚举分发执行
     *
     * @param code   操作码
     * @param params 操作参数（可空）
     * @return 基础响应
     */
    public MytosBaseResp execute(OperationCode code, Map<String, Object> params) {
        if (code == null) {
            throw new IllegalArgumentException("操作码不能为空");
        }
        switch (code) {
            case REBOOT:
                return reboot();
            case ADB_ON:
                return adbOn();
            case ADB_OFF:
                return adbOff();
            case KEEPALIVE_ON:
                return keepaliveOn(getStringParam(params, "packageName"));
            case KEEPALIVE_OFF:
                return keepaliveOff(getStringParam(params, "packageName"));
            case SET_CLIPBOARD:
                return setClipboard(getStringParam(params, "text"));
            case CLEAR_PROXY:
                return clearProxy();
            case SET_PROXY:
                return setProxy();
            case UPLOAD_FILE:
                return uploadFile();
            case REFRESH_LOC:
                return refreshLoc();
            case SET_FINGERPRINT:
                return setFingerprint();
            case SET_LANGUAGE:
                return setLanguage();
            case SET_PROXY_FILTER:
                return setProxyFilter();
            default:
                throw new MytosException("未知操作码: " + code);
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 执行 GET 请求并解析响应
     *
     * @param path      请求路径（当 url 为 null 时使用）
     * @param url       完整请求 URL（含查询参数），可为 null
     * @param respClass 响应类型
     * @param <T>       响应泛型
     * @return 解析后的响应对象
     */
    private <T extends MytosBaseResp> T doGet(String path, HttpUrl url, Class<T> respClass) {
        HttpUrl target = url != null ? url : HttpUrl.parse(baseUrl + path);
        if (target == null) {
            throw new MytosException("非法的请求地址: " + baseUrl + path);
        }
        Request request = new Request.Builder().url(target).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new MytosException(response.code(),
                        "设备 HTTP 调用失败，状态码: " + response.code());
            }
            ResponseBody body = response.body();
            String bodyStr = body != null ? body.string() : "{}";
            T resp = objectMapper.readValue(bodyStr, respClass);
            checkDeviceCode(resp);
            return resp;
        } catch (IOException e) {
            throw new MytosException("设备 HTTP 调用异常: " + e.getMessage(), e);
        }
    }

    /**
     * 校验设备返回的业务状态码
     *
     * @param resp 响应对象
     */
    private void checkDeviceCode(MytosBaseResp resp) {
        if (resp == null || resp.getCode() == null) {
            throw new MytosException("设备响应为空或缺少状态码");
        }
        if (resp.getCode() != CODE_SUCCESS) {
            String errMsg = resp.getError() != null ? resp.getError()
                    : (resp.getReason() != null ? resp.getReason() : "未知错误");
            throw new MytosException(resp.getCode(), "设备返回错误: " + errMsg);
        }
    }

    /**
     * 从参数 Map 中获取非空字符串参数
     */
    private String getStringParam(Map<String, Object> params, String key) {
        if (params == null) {
            throw new IllegalArgumentException("参数不能为空，缺少: " + key);
        }
        Object value = params.get(key);
        if (value == null || value.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("缺少必需参数: " + key);
        }
        return value.toString();
    }

    /**
     * 构建指定路径的 URL 生成器
     *
     * @param path 请求路径
     * @return HttpUrl.Builder
     * @throws MytosException 当 baseUrl + path 无法解析时抛出
     */
    private HttpUrl.Builder newUrlBuilder(String path) {
        HttpUrl parsed = HttpUrl.parse(baseUrl + path);
        if (parsed == null) {
            throw new MytosException("非法的请求地址: " + baseUrl + path);
        }
        return parsed.newBuilder();
    }

    /**
     * 校验字符串非空
     */
    private void requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}
