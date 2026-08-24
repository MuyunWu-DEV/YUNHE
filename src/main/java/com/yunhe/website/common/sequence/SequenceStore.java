package com.yunhe.website.common.sequence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 通用序号生成器：从数据库端按 key 原子自增，为各类业务编号提供并发安全的取号能力。
 *
 * <p>核心是 MySQL 的序号生成惯用法，两条语句在<b>同一连接</b>上执行：</p>
 * <pre>
 * INSERT INTO sys_sequence(seq_key, current_value) VALUES(?, LAST_INSERT_ID(1))
 * ON DUPLICATE KEY UPDATE current_value = LAST_INSERT_ID(current_value + 1);
 * SELECT LAST_INSERT_ID();
 * </pre>
 * <ul>
 *   <li>首次取号：插入 (key, 1)，{@code LAST_INSERT_ID(1)} 把本次连接的自增值设为 1；</li>
 *   <li>再次取号：命中唯一键，原子地 {@code current_value + 1}，并把新值写入 {@code LAST_INSERT_ID()}；</li>
 *   <li>{@code LAST_INSERT_ID()} 是连接级变量，天然线程/连接隔离，无需显式加锁。</li>
 * </ul>
 *
 * <p>整个 {@code INSERT} 语句是原子的（InnoDB 行锁），并发请求会得到互不重复的递增序号。
 * key 的取值完全由调用方决定，按年重置、按月、按业务类型等任意维度均可自由组合。</p>
 */
@Component
@RequiredArgsConstructor
public class SequenceStore {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 按任意 key 原子自增，返回新序号（从 1 开始）。
     *
     * @param key 业务自定义的序号键，如 {@code YHPI-2026}、{@code CUSTOMER}
     * @return 该 key 下的自增序号，如 1、2、3……
     */
    public int next(String key) {
        Integer seq = jdbcTemplate.execute((ConnectionCallback<Integer>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_sequence(seq_key, current_value) VALUES(?, LAST_INSERT_ID(1)) "
                            + "ON DUPLICATE KEY UPDATE current_value = LAST_INSERT_ID(current_value + 1)")) {
                ps.setString(1, key);
                ps.executeUpdate();
            }
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT LAST_INSERT_ID()")) {
                rs.next();
                return rs.getInt(1);
            }
        });
        return seq == null ? 1 : seq;
    }

    /**
     * 便捷重载：按「前缀-年份」自增，用于按年重置的年度编号（如单据号 {@code YHPI-2026}）。
     *
     * @param prefix 编号前缀，如 {@code YHPI}
     * @param year   年份
     * @return 该前缀在该年份内的自增序号
     */
    public int next(String prefix, int year) {
        return next(prefix + "-" + year);
    }
}
