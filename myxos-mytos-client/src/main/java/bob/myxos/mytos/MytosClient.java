package bob.myxos.mytos;

import bob.myxos.common.enums.OperationCode;
import bob.myxos.mytos.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * MYTOS 设备 HTTP API 客户端
 * 封装对单台设备的所有 HTTP 调用
 * <p>
 * 非 Spring Bean，由 {@link MytosClientFactory} 创建。
 * 每个实例绑定一台设备（IP + 端口）。
 * <p>
 * 接口范围以 docs/superpowers/plans/2026-08-03-mytos-api-mapping.md 最终确认的首期接口为准。
 */
public class MytosClient {

    /** 成功状态码 */
    private static final int CODE_SUCCESS = 200;

    /** Shell 命令最大长度 */
    private static final int SHELL_COMMAND_MAX_LENGTH = 500;

    /** Shell 命令读取超时时间（秒）：部分命令（如 pm list packages）执行较慢，单独放宽 */
    private static final int SHELL_READ_TIMEOUT_SEC = 60;

    /** IP 地址格式校验（IPv4 / IPv6，仅用于防止路径注入） */
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d?\\d)$|"
                    + "^\\[[0-9a-fA-F:]+\\]$|^[0-9a-fA-F:]+$");

    /** 禁止执行的 Shell 命令模式（不区分大小写） */
    private static final Pattern DANGEROUS_SHELL_PATTERN = Pattern.compile(
            "\\b(rm\\s+-rf|reboot|shutdown|dd\\s+if=|mkfs|format|su\\b|mount\\s+-o\\s+remount)\\b",
            Pattern.CASE_INSENSITIVE);

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

    // ==================== 主机层面接口 ====================

    /**
     * 主机存活/健康检查
     * POST /host_api/v1/healthcheck/{hostIp}
     *
     * @param hostIp 主机 IP
     * @return 健康检查响应
     */
    public HealthResp healthcheck(String hostIp) {
        requireValidIp(hostIp, "hostIp");
        return doPost("/host_api/v1/healthcheck/" + hostIp, HealthResp.class);
    }

    /**
     * 获取指定主机系统信息
     * GET /host_api/v1/get_systeminfo/{hostIp}
     *
     * @param hostIp 主机 IP
     * @return 系统信息响应
     */
    public HostSystemInfoResp getSystemInfo(String hostIp) {
        requireValidIp(hostIp, "hostIp");
        return doGet("/host_api/v1/get_systeminfo/" + hostIp, HostSystemInfoResp.class);
    }

    /**
     * 获取 3588 本机系统信息
     * GET /host_api/v1/systeminfo
     *
     * @return 系统信息响应
     */
    public HostSystemInfoResp systeminfo() {
        return doGet("/host_api/v1/systeminfo", HostSystemInfoResp.class);
    }

    /**
     * 获取主机硬件配置
     * GET /host_api/v1/get_hardware_cfg
     *
     * @return 硬件配置响应
     */
    public HardwareCfgResp getHardwareCfg() {
        return doGet("/host_api/v1/get_hardware_cfg", HardwareCfgResp.class);
    }

    /**
     * 获取主机版本
     * GET /dc_api/v1/get_host_ver/{ip}
     *
     * @param ip 主机 IP
     * @return 版本响应
     */
    public HostVerResp getHostVer(String ip) {
        requireValidIp(ip, "ip");
        return doGet("/dc_api/v1/get_host_ver/" + ip, HostVerResp.class);
    }

    /**
     * 获取网络对象明细
     * GET /dc_api/v1/get_network_detail/{ip}
     *
     * @param ip 主机 IP
     * @return 网络明细响应
     */
    public NetworkDetailResp getNetworkDetail(String ip) {
        requireValidIp(ip, "ip");
        return doGet("/dc_api/v1/get_network_detail/" + ip, NetworkDetailResp.class);
    }

    /**
     * 获取局域网在线 MYTOS 设备列表
     * GET /host_api/v1/query_myt
     *
     * @return 在线设备列表响应
     */
    public MytDeviceListResp queryMyt() {
        return doGet("/host_api/v1/query_myt", MytDeviceListResp.class);
    }

    /**
     * 重启主机
     * GET /host_api/v1/reboot_host/{hostIp}
     *
     * @param hostIp 主机 IP
     * @return 基础响应
     */
    public MytosBaseResp rebootHost(String hostIp) {
        requireValidIp(hostIp, "hostIp");
        return doGet("/host_api/v1/reboot_host/" + hostIp, MytosBaseResp.class);
    }

    // ==================== 容器实例生命周期接口 ====================

    /**
     * 获取安卓容器列表（明细）
     * GET /dc_api/v1/list/{ip}
     *
     * @param ip 主机 IP
     * @return 容器列表响应
     */
    public AndroidListResp listAndroid(String ip) {
        requireValidIp(ip, "ip");
        return doGet("/dc_api/v1/list/" + ip, AndroidListResp.class);
    }

    /**
     * 获取指定安卓实例详情
     * GET /dc_api/v1/get_android_detail/{ip}/{name}
     *
     * @param ip   主机 IP
     * @param name 容器名称
     * @return 实例详情响应
     */
    public AndroidDetailResp getAndroidDetail(String ip, String name) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(name, "name 不能为空");
        return doGet("/dc_api/v1/get_android_detail/" + ip + "/" + name, AndroidDetailResp.class);
    }

    /**
     * 获取安卓实例启动状态
     * GET /and_api/v1/get_android_boot_status/{ip}/{name}
     *
     * @param ip   主机 IP
     * @param name 容器名称
     * @return 启动状态响应
     */
    public BootStatusResp getAndroidBootStatus(String ip, String name) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(name, "name 不能为空");
        return doGet("/and_api/v1/get_android_boot_status/" + ip + "/" + name, BootStatusResp.class);
    }

    /**
     * 运行安卓容器
     * GET /dc_api/v1/run/{ip}/{name}
     *
     * @param ip   主机 IP
     * @param name 容器名称
     * @return 基础响应
     */
    public MytosBaseResp runAndroid(String ip, String name) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(name, "name 不能为空");
        return doGet("/dc_api/v1/run/" + ip + "/" + name, MytosBaseResp.class);
    }

    /**
     * 停止安卓容器
     * GET /dc_api/v1/stop/{ip}/{name}
     *
     * @param ip   主机 IP
     * @param name 容器名称
     * @return 基础响应
     */
    public MytosBaseResp stopAndroid(String ip, String name) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(name, "name 不能为空");
        return doGet("/dc_api/v1/stop/" + ip + "/" + name, MytosBaseResp.class);
    }

    /**
     * 重启安卓容器
     * GET /dc_api/v1/reboot/{ip}/{name}
     *
     * @param ip   主机 IP
     * @param name 容器名称
     * @return 基础响应
     */
    public MytosBaseResp rebootAndroid(String ip, String name) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(name, "name 不能为空");
        return doGet("/dc_api/v1/reboot/" + ip + "/" + name, MytosBaseResp.class);
    }

    /**
     * 重置安卓容器
     * GET /dc_api/v1/reset/{ip}/{name}
     *
     * @param ip   主机 IP
     * @param name 容器名称
     * @return 基础响应
     */
    public MytosBaseResp resetAndroid(String ip, String name) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(name, "name 不能为空");
        return doGet("/dc_api/v1/reset/" + ip + "/" + name, MytosBaseResp.class);
    }

    /**
     * 重命名安卓容器
     * GET /dc_api/v1/rename/{ip}/{oldName}/{newName}
     *
     * @param ip      主机 IP
     * @param oldName 原容器名称
     * @param newName 新容器名称
     * @return 基础响应
     */
    public MytosBaseResp renameAndroid(String ip, String oldName, String newName) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(oldName, "oldName 不能为空");
        requireSafePathSegment(newName, "newName 不能为空");
        return doGet("/dc_api/v1/rename/" + ip + "/" + oldName + "/" + newName, MytosBaseResp.class);
    }

    // ==================== 手动操作面板接口 ====================

    /**
     * 设备截图（临时查看）
     * GET /and_api/v1/screenshots/{ip}/{name}/{level}
     *
     * @param ip    主机 IP
     * @param name  容器名称
     * @param level 截图等级
     * @return 截图响应
     */
    public ScreenshotResp screenshot(String ip, String name, String level) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(name, "name 不能为空");
        requireSafePathSegment(level, "level 不能为空");
        return doGet("/and_api/v1/screenshots/" + ip + "/" + name + "/" + level, ScreenshotResp.class);
    }

    /**
     * 执行 Adb 命令
     * POST /and_api/v1/shell/{ip}/{name}
     *
     * @param ip      主机 IP
     * @param name    容器名称
     * @param command shell 命令
     * @return shell 执行结果响应
     */
    public ShellResp shell(String ip, String name, String command) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(name, "name 不能为空");
        requireNonBlank(command, "command 不能为空");
        validateShellCommand(command);
        String body;
        try {
            body = objectMapper.writeValueAsString(Collections.singletonMap("cmd", command));
        } catch (Exception e) {
            throw new MytosException("shell 命令序列化失败: " + e.getMessage(), e);
        }
        return doPostBody(shellHttpClient(), "/and_api/v1/shell/" + ip + "/" + name, body,
                MediaType.parse("application/json; charset=utf-8"), ShellResp.class);
    }

    /**
     * 构建放宽读超时的 OkHttp 客户端（共享连接池与调度器）
     * 用于执行耗时较长的 shell 命令，避免默认 10 秒读超时导致失败
     */
    private OkHttpClient shellHttpClient() {
        return httpClient.newBuilder()
                .readTimeout(SHELL_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 校验 shell 命令：限制长度并拦截常见高危操作
     */
    private void validateShellCommand(String command) {
        if (command.length() > SHELL_COMMAND_MAX_LENGTH) {
            throw new IllegalArgumentException("shell 命令长度超过限制: " + SHELL_COMMAND_MAX_LENGTH);
        }
        if (DANGEROUS_SHELL_PATTERN.matcher(command).find()) {
            throw new IllegalArgumentException("shell 命令包含被禁止的高危操作");
        }
    }

    /**
     * 设置剪贴板内容
     * GET /and_api/v1/clipboard_set/{ip}/{name}?text=xxx
     *
     * @param ip   主机 IP
     * @param name 容器名称
     * @param text 文本内容
     * @return 基础响应
     */
    public MytosBaseResp clipboardSet(String ip, String name, String text) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(name, "name 不能为空");
        requireNonBlank(text, "text 不能为空");
        HttpUrl url = newUrlBuilder("/and_api/v1/clipboard_set/" + ip + "/" + name)
                .addQueryParameter("text", text)
                .build();
        return doGet(url, MytosBaseResp.class);
    }

    /**
     * 获取剪贴板内容
     * GET /and_api/v1/clipboard_get/{ip}/{name}
     *
     * @param ip   主机 IP
     * @param name 容器名称
     * @return 剪贴板响应
     */
    public ClipboardResp clipboardGet(String ip, String name) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(name, "name 不能为空");
        return doGet("/and_api/v1/clipboard_get/" + ip + "/" + name, ClipboardResp.class);
    }

    /**
     * 设置系统语言
     * GET /and_api/v1/set_Language/{ip}/{name}/{country}/{language}
     *
     * @param ip       主机 IP
     * @param name     容器名称
     * @param country  国家代码
     * @param language 语言代码
     * @return 基础响应
     */
    public MytosBaseResp setLanguage(String ip, String name, String country, String language) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(name, "name 不能为空");
        requireSafePathSegment(country, "country 不能为空");
        requireSafePathSegment(language, "language 不能为空");
        return doGet("/and_api/v1/set_Language/" + ip + "/" + name + "/" + country + "/" + language, MytosBaseResp.class);
    }

    /**
     * IP 智能定位
     * GET /and_api/v1/set_ipLocation/{ip}/{name}/{language}
     *
     * @param ip       主机 IP
     * @param name     容器名称
     * @param language 语言代码
     * @return 基础响应
     */
    public MytosBaseResp setIpLocation(String ip, String name, String language) {
        requireValidIp(ip, "ip");
        requireSafePathSegment(name, "name 不能为空");
        requireSafePathSegment(language, "language 不能为空");
        return doGet("/and_api/v1/set_ipLocation/" + ip + "/" + name + "/" + language, MytosBaseResp.class);
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
        String ip = getDeviceIp();
        switch (code) {
            case REBOOT_HOST:
                return rebootHost(ip);
            case RUN_ANDROID:
                return runAndroid(ip, getStringParam(params, "name"));
            case STOP_ANDROID:
                return stopAndroid(ip, getStringParam(params, "name"));
            case REBOOT_ANDROID:
                return rebootAndroid(ip, getStringParam(params, "name"));
            case RESET_ANDROID:
                return resetAndroid(ip, getStringParam(params, "name"));
            case RENAME_ANDROID:
                return renameAndroid(ip, getStringParam(params, "name"), getStringParam(params, "newName"));
            case SET_CLIPBOARD:
                return clipboardSet(ip, getStringParam(params, "name"), getStringParam(params, "text"));
            case GET_CLIPBOARD:
                return clipboardGet(ip, getStringParam(params, "name"));
            case SET_LANGUAGE:
                return setLanguage(ip, getStringParam(params, "name"),
                        getStringParam(params, "country"), getStringParam(params, "language"));
            case REFRESH_LOCATION:
                return setIpLocation(ip, getStringParam(params, "name"), getStringParam(params, "language"));
            case SCREENSHOT:
                return screenshot(ip, getStringParam(params, "name"), getStringParam(params, "level"));
            case SHELL_ADB:
                return shell(ip, getStringParam(params, "name"), getStringParam(params, "command"));
            default:
                throw new MytosException("未知操作码: " + code);
        }
    }

    /**
     * 获取当前客户端绑定的设备 IP
     */
    private String getDeviceIp() {
        HttpUrl parsed = HttpUrl.parse(baseUrl);
        if (parsed == null) {
            throw new IllegalStateException("无法解析 baseUrl: " + baseUrl);
        }
        return parsed.host();
    }

    // ==================== 私有 HTTP 辅助方法 ====================

    private <T extends MytosBaseResp> T doGet(String path, Class<T> respClass) {
        HttpUrl url = HttpUrl.parse(baseUrl + path);
        if (url == null) {
            throw new MytosException("非法的请求地址: " + baseUrl + path);
        }
        return doGet(url, respClass);
    }

    private <T extends MytosBaseResp> T doGet(HttpUrl url, Class<T> respClass) {
        Request request = new Request.Builder().url(url).get().build();
        return execute(request, respClass);
    }

    private <T extends MytosBaseResp> T doPost(String path, Class<T> respClass) {
        HttpUrl url = HttpUrl.parse(baseUrl + path);
        if (url == null) {
            throw new MytosException("非法的请求地址: " + baseUrl + path);
        }
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create("", MediaType.parse("application/json; charset=utf-8")))
                .build();
        return execute(request, respClass);
    }

    private <T extends MytosBaseResp> T doPostBody(String path, String body, MediaType mediaType, Class<T> respClass) {
        return doPostBody(httpClient, path, body, mediaType, respClass);
    }

    private <T extends MytosBaseResp> T doPostBody(OkHttpClient client, String path, String body,
                                                   MediaType mediaType, Class<T> respClass) {
        HttpUrl url = HttpUrl.parse(baseUrl + path);
        if (url == null) {
            throw new MytosException("非法的请求地址: " + baseUrl + path);
        }
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body, mediaType))
                .build();
        return execute(client, request, respClass);
    }

    private <T extends MytosBaseResp> T execute(Request request, Class<T> respClass) {
        return execute(httpClient, request, respClass);
    }

    private <T extends MytosBaseResp> T execute(OkHttpClient client, Request request, Class<T> respClass) {
        try (Response response = client.newCall(request).execute()) {
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

    private void checkDeviceCode(MytosBaseResp resp) {
        if (resp == null || resp.getCode() == null) {
            throw new MytosException("设备响应为空或缺少状态码");
        }
        if (resp.getCode() != CODE_SUCCESS) {
            String errMsg = resp.getError() != null ? resp.getError()
                    : (resp.getReason() != null ? resp.getReason()
                    : (resp.getMsg() != null ? resp.getMsg() : "未知错误"));
            throw new MytosException(resp.getCode(), "设备返回错误: " + errMsg);
        }
    }

    private HttpUrl.Builder newUrlBuilder(String path) {
        HttpUrl parsed = HttpUrl.parse(baseUrl + path);
        if (parsed == null) {
            throw new MytosException("非法的请求地址: " + baseUrl + path);
        }
        return parsed.newBuilder();
    }

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

    private void requireNonBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * 校验 IP 地址格式，防止路径注入或构造异常 URL
     */
    private void requireValidIp(String value, String paramName) {
        requireNonBlank(value, paramName + " 不能为空");
        if (!IP_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("IP 格式非法: " + value);
        }
    }

    /**
     * 校验路径片段：非空且仅包含安全字符，防止路径注入或构造异常 URL
     */
    private void requireSafePathSegment(String value, String message) {
        requireNonBlank(value, message);
        if (!value.matches("^[A-Za-z0-9_.-]{1,64}$")) {
            throw new IllegalArgumentException(message + " 包含非法字符");
        }
    }
}
