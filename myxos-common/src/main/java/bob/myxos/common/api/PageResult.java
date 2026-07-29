package bob.myxos.common.api;

import lombok.Data;

import java.util.List;

/**
 * 分页结果封装
 *
 * @param <T> 记录类型
 */
@Data
public class PageResult<T> {

    /** 总记录数 */
    private Long total;

    /** 总页数 */
    private Long pages;

    /** 当前页码（从 1 开始） */
    private Long current;

    /** 每页大小 */
    private Long size;

    /** 当前页记录列表 */
    private List<T> records;
}
