-- ============================================================
-- 清理 sys_sequence 中单据号改造（按年 -> 按月）残留的孤儿按年 key
-- ============================================================
-- 背景：
--   单据号改造前，SequenceStore 按「前缀-年份」生成 key（如 YHPI-2026），
--   改造后改为按「前缀-年月」生成 key（如 YHPI-202611，见 SequenceStore.next(prefix,year,month)）。
--   线上库若已有旧按年 key，新代码不再使用它（字符串不同，不冲突、无害），
--   但属于脏数据，可安全删除。
--
-- 注意：不要把这些旧 key「改名」为按月 key。旧 key 的值是全年累计（如 5），
--   而按月 key 的语义是「某月第几张」，二者不对应，改名会破坏序号逻辑。
--   正确做法是 DELETE 孤儿 key，新代码会按需重建 YHPI-202611（从 1 起）。
-- ============================================================

USE yunhe;

-- 第 1 步（可选，先确认）：查看会被清理的按年 key 及其当前值
SELECT seq_key, current_value
FROM sys_sequence
WHERE seq_key REGEXP '^(YHPI|YHSO|YHINV|YHPL)-[0-9]{4}$';

-- 第 2 步（执行清理）：删除上述孤儿按年 key
-- 正则精确匹配「前缀-4位年份」结尾，不会误删按月 key（YHPI-202611 为 6 位数字，不匹配 [0-9]{4}$）
-- 也不会误删其它无关 key（如 CUSTOMER / TICKET）
-- 幂等：可重复执行，无匹配行时不影响。
DELETE FROM sys_sequence
WHERE seq_key REGEXP '^(YHPI|YHSO|YHINV|YHPL)-[0-9]{4}$';
