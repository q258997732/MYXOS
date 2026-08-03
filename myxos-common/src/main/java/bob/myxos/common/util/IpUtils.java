package bob.myxos.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * IP 工具类
 * 提供 CIDR 网段展开能力，用于网段发现任务
 */
public final class IpUtils {

    /** 最小允许的前缀长度（即最大网段为 /22，包含 1022 个可用 IP） */
    private static final int MIN_PREFIX = 22;

    /** IPv4 总位数 */
    private static final int IPV4_BITS = 32;

    private IpUtils() {
    }

    /**
     * 展开 CIDR 网段为可用 IP 列表
     * 排除网络地址与广播地址
     *
     * @param cidr CIDR 表达式，例如 192.168.30.0/24
     * @return 可用 IP 列表
     * @throws IllegalArgumentException 当 CIDR 格式非法或网段大于 /22 时抛出
     */
    public static List<String> expandCidr(String cidr) {
        if (cidr == null || !cidr.contains("/")) {
            throw new IllegalArgumentException("CIDR 格式错误，示例：192.168.30.0/24");
        }
        String[] parts = cidr.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("CIDR 格式错误，示例：192.168.30.0/24");
        }
        String baseIp = parts[0];
        int prefix;
        try {
            prefix = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("CIDR 前缀必须是数字：" + parts[1]);
        }
        if (prefix < MIN_PREFIX) {
            throw new IllegalArgumentException("出于安全考虑，仅支持 /22 及以上网段");
        }
        if (prefix > IPV4_BITS) {
            throw new IllegalArgumentException("CIDR 前缀不能超过 32");
        }
        int hostBits = IPV4_BITS - prefix;
        // 使用 long 避免 /0 时位移溢出（虽然前面已限制 /22，这里做防御性处理）
        long count = 1L << hostBits;
        int mask = (int) (0xFFFFFFFFL << hostBits);
        int base = ipv4ToInt(baseIp) & mask;
        // /32 单 IP、/31 点对点链路需要特殊处理，不能简单排除网络与广播地址
        int start = (prefix >= IPV4_BITS - 1) ? 0 : 1;
        int end = (int) ((prefix == IPV4_BITS) ? 1 : count - 1);
        List<String> result = new ArrayList<>((int) Math.max(0, end - start));
        for (long i = start; i < end; i++) {
            result.add(intToIpv4(base | (int) i));
        }
        return result;
    }

    /**
     * 将点分十进制 IPv4 字符串转换为 int
     *
     * @param ip IPv4 字符串
     * @return int 表示
     */
    private static int ipv4ToInt(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("IPv4 格式错误：" + ip);
        }
        int value = 0;
        for (String part : parts) {
            int segment;
            try {
                segment = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("IPv4 段必须是数字：" + part);
            }
            if (segment < 0 || segment > 255) {
                throw new IllegalArgumentException("IPv4 段必须在 0-255 之间：" + part);
            }
            value = (value << 8) | segment;
        }
        return value;
    }

    /**
     * 将 int 转换为点分十进制 IPv4 字符串
     *
     * @param value int 表示
     * @return IPv4 字符串
     */
    private static String intToIpv4(int value) {
        return String.format("%d.%d.%d.%d",
                (value >> 24) & 0xFF,
                (value >> 16) & 0xFF,
                (value >> 8) & 0xFF,
                value & 0xFF);
    }
}
