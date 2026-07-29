package bob.myxos.domain.audit;

/**
 * 当前登录用户持有器
 * 使用 ThreadLocal 保存当前线程的登录用户名，
 * 在未登录或系统内部调用场景下返回 "system"
 */
public final class LoginUserHolder {

    /** 保存当前线程登录用户名的 ThreadLocal */
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    /** 默认系统用户名 */
    private static final String DEFAULT_USER = "system";

    private LoginUserHolder() {
    }

    /**
     * 设置当前线程的登录用户名
     *
     * @param username 登录用户名
     */
    public static void set(String username) {
        USERNAME.set(username);
    }

    /**
     * 获取当前线程的登录用户名，未登录时返回 "system"
     *
     * @return 登录用户名
     */
    public static String get() {
        String name = USERNAME.get();
        return name == null ? DEFAULT_USER : name;
    }

    /**
     * 清除当前线程的登录用户名，防止线程复用导致的数据污染
     */
    public static void clear() {
        USERNAME.remove();
    }
}
