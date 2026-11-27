# 数据库设计概览

完整项目使用 MySQL 8，数据库按业务域管理 31 张表。本仓库公开完整的脱敏 DDL 和必要的增量迁移脚本，不公开生产业务数据、账号和运行配置。

- 完整结构：[`code-snapshots/database/schema.sql`](../code-snapshots/database/schema.sql)
- 主动签收与报损迁移：[`2026-11-26_manual_claim_auto_sign.sql`](../code-snapshots/database/migrations/2026-11-26_manual_claim_auto_sign.sql)

| 业务域 | 表设计关注点 |
| --- | --- |
| 组织与权限 | 后台用户、角色、菜单、角色菜单关系 |
| 基础档案 | 客户、供应商、品类、菜品、地址 |
| 采购库存 | 采购单、入库单、库存汇总、库存批次、损耗与盘点 |
| 销售财务 | 销售单、销售明细快照、应收应付、收付款记录 |
| 分拣配送 | 分拣单、配送状态、送达时间、签收时间和签收来源 |
| 拼单 | 拼单主表、成员、活动、每日价格、运费和审计 |
| 售后 | 主动签收报损、自动签收、报损时限、补发、取消订单、退款与惩罚 |
| 报表 | 日结、月报和顾客月度对账查询 |

设计原则：

- 订单明细保存商品与价格快照，避免基础档案修改影响历史。
- 库存汇总和批次库存分离，兼顾查询效率与 FIFO 成本。
- 业务单据保留取消、退款和审计记录，不直接覆盖历史。
- 所有金额字段使用 `DECIMAL`，Java 使用 `BigDecimal`。
- `dlv_order.delivered_time` 记录送达时刻，`sign_type` 区分 `MANUAL` 与 `AUTO`。
- `sal_claim_setting.claim_window_hours` 按商户保存 1 至 72 小时的报损窗口，无配置时默认 14 小时。
