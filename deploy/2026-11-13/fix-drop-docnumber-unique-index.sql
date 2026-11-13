-- ============================================================================
-- 修复脚本：单据号改为 YHXYYYYMMNNN 后，原 (年,序) 复合唯一约束已失效
-- ----------------------------------------------------------------------------
-- 背景：单据号改造把 seq 从「按年自增」改为「按月自增」，但 4 个实体仍存
--       年度 invoice_year + 月度 seq，而 DB 复合唯一约束建在 (年,序) 上，
--       导致同年不同月都出现 seq=1 -> 报 Duplicate entry '2026-1' 撞键。
-- 处理：删除这 4 个已失效的复合唯一索引。唯一性由每表已有的「完整单号」
--       列唯一约束兜底（invoice_number / invoice_no / order_no / packing_no）。
-- 幂等：使用 IF EXISTS，重复执行不会报错。
-- 注意：Hibernate ddl-auto:update 不会自动删除已有索引，必须手动执行一次。
--       新库（重新建表）因注解已删不会再建，无需本脚本。
-- ============================================================================

USE yunhe;

DROP INDEX IF EXISTS uk_invoice_year_seq ON crm_proforma_invoice;
DROP INDEX IF EXISTS uk_ci_year_seq       ON crm_commercial_invoice;
DROP INDEX IF EXISTS uk_pl_year_seq       ON crm_packing_list;
DROP INDEX IF EXISTS uk_order_year_seq    ON crm_sales_order;
