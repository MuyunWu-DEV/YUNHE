-- ============================================================
-- 删除单据号改造后遗留的冗余字段（年份 + 月度序号）
-- ============================================================
-- 背景：
--   单据号已改为完整字符串 YHPI202611001（含年月与序号），原拆分的
--   invoice_year / packing_year / order_year + seq 字段已无任何读取方
--   （全代码库无 getter 调用），且仅为已删除的 (年,序) 复合唯一约束服务。
--   现属冗余，删除以简化表结构。
--
-- 说明：
--   - Hibernate ddl-auto:update 不会自动删除已有列，须手动执行本脚本。
--   - 本脚本为一次性清理；若重复执行，已不存在的列会报
--     "Unknown column" 错误，属正常现象，可忽略。
--   - 仅删列、不动单据号列、不丢业务数据。
-- ============================================================

USE yunhe;

ALTER TABLE crm_proforma_invoice   DROP COLUMN invoice_year, DROP COLUMN seq;
ALTER TABLE crm_commercial_invoice DROP COLUMN invoice_year, DROP COLUMN seq;
ALTER TABLE crm_packing_list       DROP COLUMN packing_year, DROP COLUMN seq;
ALTER TABLE crm_sales_order        DROP COLUMN order_year,   DROP COLUMN seq;
