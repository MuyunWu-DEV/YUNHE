package com.yunhe.website.common.validation;

/**
 * 「发起变更（revise）」校验分组。
 *
 * <p>用于区分「仅在发起变更时生效」的约束（如变更原因必填）与默认约束：
 * 普通编辑走默认分组（{@code @Valid}），发起变更走
 * {@code @Validated({Default.class, ReviseGroup.class})}，二者互不影响。</p>
 */
public interface ReviseGroup {
}
