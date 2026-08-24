package com.yunhe.website.common.web;

import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 分页工具：统一分页参数边界校验与排序规则。
 *
 * <p>列表接口直接传入前端 {@code page}/{@code size} 即可，无需各自重复
 * {@code PageRequest.of(...)}，也避免 {@code size} 无上限、{@code page} 为负导致的异常或大查询。</p>
 */
public final class PageUtil {

    /** 默认每页条数 */
    private static final int DEFAULT_SIZE = 10;
    /** 每页最大条数（防止一次拉取过多数据） */
    private static final int MAX_SIZE = 100;

    private PageUtil() {
    }

    /**
     * 构建按主键倒序的分页请求。
     *
     * @param page 页码（从 0 开始，负数按 0 处理）
     * @param size 每页条数（小于等于 0 用默认值，超过上限按上限处理）
     */
    public static PageRequest of(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return PageRequest.of(safePage, safeSize, Sort.by("id").descending());
    }

    /**
     * 计算分页页码列表（当前页前后各 2 页），用于分页条渲染。
     *
     * @param current    当前页码（从 0 开始）
     * @param totalPages 总页数
     */
    public static List<Integer> pageNumbers(int current, int totalPages) {
        List<Integer> numbers = new ArrayList<>();
        int start = Math.max(0, current - 2);
        int end = Math.min(totalPages - 1, current + 2);
        for (int i = start; i <= end; i++) {
            numbers.add(i);
        }
        return numbers;
    }
}
