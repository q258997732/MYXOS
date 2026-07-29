package bob.myxos.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IpUtils 单元测试
 * 验证 CIDR 展开的正确性以及边界条件
 */
class IpUtilsTest {

    @Test
    void 展开24位网段应返回254个可用IP() {
        // Arrange
        String cidr = "192.168.30.0/24";

        // Act
        List<String> ips = IpUtils.expandCidr(cidr);

        // Assert
        assertEquals(254, ips.size());
        assertEquals("192.168.30.1", ips.get(0));
        assertEquals("192.168.30.254", ips.get(ips.size() - 1));
        // 不应包含网络地址与广播地址
        assertFalse(ips.contains("192.168.30.0"));
        assertFalse(ips.contains("192.168.30.255"));
    }

    @Test
    void 主机位非零的CIDR应归一化到网段起始地址() {
        // Arrange
        String cidr = "192.168.30.5/24";

        // Act
        List<String> ips = IpUtils.expandCidr(cidr);

        // Assert
        assertEquals(254, ips.size());
        assertEquals("192.168.30.1", ips.get(0));
        assertEquals("192.168.30.254", ips.get(ips.size() - 1));
    }

    @Test
    void 展开22位网段应返回1022个可用IP() {
        // Arrange
        String cidr = "192.168.0.0/22";

        // Act
        List<String> ips = IpUtils.expandCidr(cidr);

        // Assert
        assertEquals(1022, ips.size());
        assertEquals("192.168.0.1", ips.get(0));
        assertEquals("192.168.3.254", ips.get(ips.size() - 1));
    }

    @Test
    void 展开32位主机网段应返回空列表() {
        // Arrange
        String cidr = "192.168.1.1/32";

        // Act
        List<String> ips = IpUtils.expandCidr(cidr);

        // Assert
        assertTrue(ips.isEmpty());
    }

    @Test
    void 网段小于22位应抛出异常() {
        // Arrange
        String cidr = "10.0.0.0/16";

        // Act & Assert
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> IpUtils.expandCidr(cidr));
        assertTrue(ex.getMessage().contains("/22"));
    }

    @Test
    void 空CIDR应抛出异常() {
        assertThrows(IllegalArgumentException.class, () -> IpUtils.expandCidr(null));
    }

    @Test
    void 缺少斜杠的CIDR应抛出异常() {
        assertThrows(IllegalArgumentException.class, () -> IpUtils.expandCidr("192.168.1.0"));
    }

    @Test
    void 非法IP段应抛出异常() {
        assertThrows(IllegalArgumentException.class, () -> IpUtils.expandCidr("999.1.1.0/24"));
    }

    @Test
    void 非法前缀应抛出异常() {
        assertThrows(IllegalArgumentException.class, () -> IpUtils.expandCidr("192.168.1.0/33"));
    }
}
