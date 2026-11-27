-- =====================================================================
-- 菜贩管理系统 数据库结构脚本（当前 31 张表）
-- 数据库: caifan_db  字符集: utf8mb4  引擎: InnoDB
-- 说明: 主键统一 BIGINT（雪花 ID），金额 DECIMAL(12,2)，重量/数量 DECIMAL(12,3)
-- 精简与演进原则（初始 36 → 当前 23）：
--   1) 删除零代码引用的预留/未实现表：sys_merchant/sys_config/sys_log/biz_return(+item)/sys_dict_type/sys_dict_data
--   2) 同实体重复建模合并：sys_user_role→sys_user.role_id、base_supplier+base_customer→base_party
--   3) 主从明细压平为 JSON 字段：pur_order/pur_receipt/sal_order 的 items_json
--   4) 损耗+盘点合一：inv_adjust(adjust_type)
--   5) 收付款并入往来结算、日结改为实时聚合：砍 fin_payment / fin_day_close
-- =====================================================================

CREATE DATABASE IF NOT EXISTS caifan_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
ALTER DATABASE caifan_db CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE caifan_db;
SET NAMES utf8mb4;

-- ---------------- 系统管理 sys_ ----------------

CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL COMMENT '主键',
    merchant_id BIGINT       NOT NULL DEFAULT 0 COMMENT '所属商户，0 表示平台账号',
    username    VARCHAR(32)  NOT NULL COMMENT '登录名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
    real_name   VARCHAR(32)  NULL COMMENT '真实姓名',
    phone       VARCHAR(20)  NULL COMMENT '手机号',
    email       VARCHAR(64)  NULL COMMENT '邮箱',
    avatar      VARCHAR(255) NULL COMMENT '头像',
    role_id     BIGINT       NULL COMMENT '所属角色 id（业务上一用户一角色）',
    status      CHAR(1)      NOT NULL DEFAULT '0' COMMENT '状态 0正常 1停用',
    must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT '1 首次登录必须修改密码',
    remark      VARCHAR(255) NULL,
    create_by   BIGINT NULL, create_time DATETIME NULL,
    update_by   BIGINT NULL, update_time DATETIME NULL,
    deleted     TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_merchant_username (merchant_id, username),
    UNIQUE KEY uk_user_merchant_phone (merchant_id, phone),
    KEY idx_user_role (role_id)
) ENGINE=InnoDB COMMENT='用户表（含角色）';

CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT      NOT NULL,
    merchant_id BIGINT      NOT NULL DEFAULT 0 COMMENT '所属商户，0 表示平台级角色',
    role_name   VARCHAR(32) NOT NULL COMMENT '角色名称',
    role_key    VARCHAR(32) NOT NULL COMMENT '角色标识',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '排序',
    status      CHAR(1)     NOT NULL DEFAULT '0',
    remark      VARCHAR(255) NULL,
    create_by   BIGINT NULL, create_time DATETIME NULL,
    update_by   BIGINT NULL, update_time DATETIME NULL,
    deleted     TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_merchant_key (merchant_id, role_key)
) ENGINE=InnoDB COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sys_menu (
    id        BIGINT       NOT NULL,
    parent_id BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单 id，0 为根',
    menu_name VARCHAR(32)  NOT NULL COMMENT '菜单名称',
    path      VARCHAR(128) NULL COMMENT '路由路径',
    menu_type CHAR(1)      NOT NULL DEFAULT 'C' COMMENT 'M 目录 C 菜单 F 按钮',
    perms     VARCHAR(64)  NULL COMMENT '权限标识',
    icon      VARCHAR(32)  NULL COMMENT '图标',
    sort      INT          NOT NULL DEFAULT 0,
    visible   CHAR(1)      NOT NULL DEFAULT '0' COMMENT '0 显示 1 隐藏',
    status    CHAR(1)      NOT NULL DEFAULT '0',
    create_by BIGINT NULL, create_time DATETIME NULL,
    update_by BIGINT NULL, update_time DATETIME NULL,
    deleted   TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='菜单表';

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB COMMENT='角色-菜单关联表';

CREATE TABLE IF NOT EXISTS sys_serial (
    id        BIGINT      NOT NULL,
    biz_type  VARCHAR(8)  NOT NULL COMMENT '业务前缀，如 CG/RK/XS',
    biz_date  CHAR(8)     NOT NULL COMMENT 'yyyyMMdd',
    current   INT         NOT NULL DEFAULT 0 COMMENT '当日当前流水',
    PRIMARY KEY (id),
    UNIQUE KEY uk_serial (biz_type, biz_date)
) ENGINE=InnoDB COMMENT='业务单号流水表';

-- ---------------- 基础档案 base_ ----------------

CREATE TABLE IF NOT EXISTS base_category (
    id          BIGINT       NOT NULL,
    merchant_id BIGINT       NOT NULL DEFAULT 0,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    name        VARCHAR(64)  NOT NULL,
    sort        INT          NOT NULL DEFAULT 0,
    status      CHAR(1)      NOT NULL DEFAULT '0',
    remark      VARCHAR(255) NULL,
    create_by   BIGINT NULL, create_time DATETIME NULL,
    update_by   BIGINT NULL, update_time DATETIME NULL,
    deleted     TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_category_list (merchant_id, deleted, parent_id, sort)
) ENGINE=InnoDB COMMENT='商品品类表';

CREATE TABLE IF NOT EXISTS base_goods (
    id             BIGINT        NOT NULL,
    merchant_id    BIGINT        NOT NULL DEFAULT 0,
    category_id    BIGINT        NULL,
    supplier_id    BIGINT        NULL,
    name           VARCHAR(64)   NOT NULL,
    image_url      VARCHAR(255)  NULL COMMENT '菜品图片相对路径，存于项目 img/ 目录',
    code           VARCHAR(32)   NULL,
    unit           VARCHAR(16)   NULL COMMENT '计量单位',
    pricing_type   VARCHAR(16)   NOT NULL DEFAULT 'WEIGHT' COMMENT 'WEIGHT 称重 PIECE 计件',
    purchase_price DECIMAL(12,2) NULL COMMENT '参考进价',
    sale_price     DECIMAL(12,2) NULL COMMENT '零售价',
    wholesale_price DECIMAL(12,2) NULL COMMENT '采购商价',
    group_price    DECIMAL(12,2) NULL COMMENT '拼单价',
    origin         VARCHAR(64)   NULL COMMENT '产地',
    min_stock      DECIMAL(12,3) NULL COMMENT '预警下限',
    max_stock      DECIMAL(12,3) NULL COMMENT '预警上限',
    status         CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0 上架 1 下架',
    remark         VARCHAR(255)  NULL,
    create_by      BIGINT NULL, create_time DATETIME NULL,
    update_by      BIGINT NULL, update_time DATETIME NULL,
    deleted        TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_goods_code (merchant_id, code),
    KEY idx_goods_category (category_id),
    KEY idx_goods_merchant (merchant_id),
    KEY idx_goods_mall (merchant_id, deleted, status, id)
) ENGINE=InnoDB COMMENT='商品表；同商户内商品编码唯一';

CREATE TABLE IF NOT EXISTS base_file_asset (
    id          BIGINT        NOT NULL,
    merchant_id BIGINT        NOT NULL DEFAULT 0,
    asset_key   VARCHAR(64)   NOT NULL COMMENT '业务资源键，如 mall.payQr',
    file_name   VARCHAR(255)  NOT NULL,
    url         VARCHAR(500)  NOT NULL COMMENT '相对访问路径或HTTPS地址，不存文件二进制',
    mime_type   VARCHAR(100)  NOT NULL,
    file_size   BIGINT        NOT NULL DEFAULT 0,
    status      CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
    create_by   BIGINT NULL, create_time DATETIME NULL,
    update_by   BIGINT NULL, update_time DATETIME NULL,
    deleted     TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_asset_key (merchant_id, asset_key)
) ENGINE=InnoDB COMMENT='应用媒体文件元数据表；文件本体存img或uploads目录';

CREATE TABLE IF NOT EXISTS base_party (
    id            BIGINT        NOT NULL,
    merchant_id   BIGINT        NOT NULL DEFAULT 0,
    party_type    CHAR(1)       NOT NULL COMMENT 'S 供应商 C 客户',
    name          VARCHAR(64)   NOT NULL,
    biz_type      VARCHAR(16)   NULL COMMENT '客户类型：CANTEEN/RESTAURANT/WHOLESALER/RETAIL（散客）',
    contact       VARCHAR(32)   NULL,
    phone         VARCHAR(20)   NULL,
    password      VARCHAR(100)  NULL COMMENT 'BCrypt 登录密码（客户自助注册用）',
    email         VARCHAR(64)   NULL COMMENT '邮箱',
    address       VARCHAR(255)  NULL,
    bank_name     VARCHAR(64)   NULL COMMENT '供应商银行（客户可为空）',
    bank_account  VARCHAR(64)   NULL,
    credit_limit  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '客户赊账额度',
    credit_used   DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '客户已用赊账',
    status        CHAR(1)       NOT NULL DEFAULT '0',
    audit_status  CHAR(1)       NOT NULL DEFAULT '1' COMMENT '0待审核 1已通过 2已驳回',
    audit_remark  VARCHAR(255)  NULL,
    apply_doc_url VARCHAR(255)  NULL COMMENT '采购商申请资料图片',
    remark        VARCHAR(255)  NULL,
    create_by     BIGINT NULL, create_time DATETIME NULL,
    update_by     BIGINT NULL, update_time DATETIME NULL,
    deleted       TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_party_type (merchant_id, party_type),
    UNIQUE KEY uk_party_phone (merchant_id, party_type, phone),
    KEY idx_party_login (party_type, phone, deleted, update_time)
) ENGINE=InnoDB COMMENT='往来单位表（供应商/客户合一，按 party_type 区分）';

-- ---------------- 采购管理 pur_ ----------------

CREATE TABLE IF NOT EXISTS pur_order (
    id           BIGINT        NOT NULL,
    merchant_id  BIGINT        NOT NULL DEFAULT 0,
    order_no     VARCHAR(32)   NOT NULL,
    supplier_id  BIGINT        NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    status       CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0 待入库 1 部分入库 2 已入库 3 已取消',
    order_date   DATE          NULL,
    expect_date  DATE          NULL,
    remark       VARCHAR(255)  NULL,
    items_json   JSON          NULL COMMENT '明细 [{"goodsId","goodsName","qty","price","amount","receivedQty"}]',
    create_by    BIGINT NULL, create_time DATETIME NULL,
    update_by    BIGINT NULL, update_time DATETIME NULL,
    deleted      TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pur_order_no (order_no),
    KEY idx_pur_order_pending (merchant_id, deleted, status, create_time)
) ENGINE=InnoDB COMMENT='采购订单表（含明细）';

CREATE TABLE IF NOT EXISTS pur_receipt (
    id           BIGINT        NOT NULL,
    merchant_id  BIGINT        NOT NULL DEFAULT 0,
    receipt_no   VARCHAR(32)   NOT NULL,
    order_id     BIGINT        NULL,
    supplier_id  BIGINT        NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    status       CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0 已入库 1 已冲销',
    receipt_date DATE          NULL,
    auditor      VARCHAR(32)   NULL,
    remark       VARCHAR(255)  NULL,
    photo_urls   JSON          NULL COMMENT '入库凭证照片路径数组',
    items_json   JSON          NULL COMMENT '明细 [{"goodsId","goodsName","batchNo","qty","price","amount","produceDate"}]',
    create_by    BIGINT NULL, create_time DATETIME NULL,
    update_by    BIGINT NULL, update_time DATETIME NULL,
    deleted      TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_pur_receipt_no (receipt_no),
    KEY idx_pur_receipt_daily (merchant_id, deleted, status, receipt_date)
) ENGINE=InnoDB COMMENT='采购入库单表（含明细）';

-- ---------------- 库存管理 inv_ ----------------

CREATE TABLE IF NOT EXISTS inv_stock (
    id          BIGINT        NOT NULL,
    merchant_id BIGINT        NOT NULL DEFAULT 0,
    goods_id    BIGINT        NOT NULL,
    qty         DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '当前库存数量',
    create_by   BIGINT NULL, create_time DATETIME NULL,
    update_by   BIGINT NULL, update_time DATETIME NULL,
    deleted     TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_goods (merchant_id, goods_id),
    CONSTRAINT chk_stock_qty CHECK (qty >= 0)
) ENGINE=InnoDB COMMENT='库存汇总表';

CREATE TABLE IF NOT EXISTS inv_batch (
    id           BIGINT        NOT NULL,
    merchant_id  BIGINT        NOT NULL DEFAULT 0,
    goods_id     BIGINT        NOT NULL,
    batch_no     VARCHAR(32)   NOT NULL,
    qty          DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '批次剩余数量',
    price        DECIMAL(12,2) NOT NULL COMMENT '批次进价',
    produce_date DATE          NULL,
    supplier_id  BIGINT        NULL,
    receipt_id   BIGINT        NULL,
    create_by    BIGINT NULL, create_time DATETIME NULL,
    update_by    BIGINT NULL, update_time DATETIME NULL,
    deleted      TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_batch_no (merchant_id, batch_no),
    KEY idx_batch_goods (merchant_id, goods_id),
    CONSTRAINT chk_batch_qty CHECK (qty >= 0)
) ENGINE=InnoDB COMMENT='批次库存表（FIFO 成本核算）';

CREATE TABLE IF NOT EXISTS inv_adjust (
    id          BIGINT        NOT NULL,
    merchant_id BIGINT        NOT NULL DEFAULT 0,
    adjust_no   VARCHAR(32)   NOT NULL COMMENT '损耗单号 / 盘点单号',
    adjust_type VARCHAR(16)   NOT NULL COMMENT 'LOSS 损耗 CHECK 盘点',
    goods_id    BIGINT        NOT NULL,
    batch_no    VARCHAR(32)   NULL,
    qty         DECIMAL(12,3) NULL COMMENT '损耗数量 / 实盘盈亏数量',
    amount      DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '损耗金额',
    reason      VARCHAR(64)   NULL COMMENT '损耗原因',
    book_qty    DECIMAL(12,3) NULL COMMENT '盘点：账面数量',
    actual_qty  DECIMAL(12,3) NULL COMMENT '盘点：实盘数量',
    status      CHAR(1)       NOT NULL DEFAULT '0' COMMENT '损耗 0已登记；盘点 1已调账',
    adjust_date DATE          NULL,
    operator    VARCHAR(32)   NULL,
    create_by   BIGINT NULL, create_time DATETIME NULL,
    update_by   BIGINT NULL, update_time DATETIME NULL,
    deleted     TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_adjust_no (adjust_no),
    KEY idx_adjust_type (merchant_id, adjust_type, adjust_date)
) ENGINE=InnoDB COMMENT='库存调整表（损耗/盘点合一，按 adjust_type 区分）';

-- ---------------- 销售管理 sal_ ----------------

CREATE TABLE IF NOT EXISTS sal_order (
    id          BIGINT        NOT NULL,
    merchant_id BIGINT        NOT NULL DEFAULT 0,
    sale_no     VARCHAR(32)   NOT NULL,
    customer_id BIGINT        NOT NULL,
    group_id    BIGINT        NULL COMMENT '拼单团 id',
    group_member_id BIGINT    NULL COMMENT '拼单成员 id',
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '原价合计',
    discount    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '优惠金额',
    payable     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '应收金额',
    freight_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '运费',
    cost_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT 'FIFO 销售成本合计',
    pay_status  CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0 未收 1 部分 2 已收',
    status      CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0 正常 1 已退货 2 已取消',
    sale_date   DATETIME      NULL,
    salesperson VARCHAR(32)   NULL,
    receiver_name    VARCHAR(64)  NULL COMMENT '收货人',
    receiver_phone   VARCHAR(20)  NULL COMMENT '收货电话',
    receiver_address VARCHAR(255) NULL COMMENT '收货地址',
    remark      VARCHAR(255)  NULL,
    cancel_reason VARCHAR(32) NULL,
    cancel_remark VARCHAR(255) NULL,
    cancel_operator VARCHAR(32) NULL,
    cancel_time DATETIME NULL,
    refund_status CHAR(1) NOT NULL DEFAULT '0' COMMENT '0未退款 1已模拟退款',
    refund_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    refund_time DATETIME NULL,
    items_json  JSON          NULL COMMENT '明细含 batchAllocations 完整 FIFO 批次分配快照',
    create_by   BIGINT NULL, create_time DATETIME NULL,
    update_by   BIGINT NULL, update_time DATETIME NULL,
    deleted     TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sal_order_no (sale_no),
    KEY idx_sal_date (merchant_id, sale_date),
    KEY idx_sal_customer_date (customer_id, deleted, sale_date, id),
    KEY idx_sal_group (group_id),
    KEY idx_sal_group_member (group_member_id)
) ENGINE=InnoDB COMMENT='销售单表（含明细，FIFO 成本已结转至 items_json.costAmount）';

-- ---------------- 财务结算 fin_ ----------------

CREATE TABLE IF NOT EXISTS fin_settlement (
    id         BIGINT        NOT NULL,
    merchant_id BIGINT       NOT NULL,
    party_type CHAR(1)       NOT NULL COMMENT 'C 客户（应收） S 供应商（应付）',
    party_id   BIGINT        NOT NULL COMMENT '往来单位 id',
    biz_type   VARCHAR(16)   NOT NULL COMMENT 'SALE 销售应收 RECEIPT 入库应付',
    biz_no     VARCHAR(32)   NULL COMMENT '销售单号 / 入库单号',
    amount     DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '应收/应付金额',
    settled    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '已收/已付金额',
    balance    DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '未结余额',
    status     CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0 未结清 1 已结清',
    due_date   DATE          NULL,
    pay_method VARCHAR(16)   NULL COMMENT '最近一次收付款方式',
    pay_time   DATETIME      NULL COMMENT '最近一次收付款时间',
    create_by  BIGINT NULL, create_time DATETIME NULL,
    update_by  BIGINT NULL, update_time DATETIME NULL,
    deleted    TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_settle_biz (biz_type, biz_no),
    KEY idx_settle_party (merchant_id, party_type, party_id),
    KEY idx_settle_open (merchant_id, party_type, status),
    CONSTRAINT chk_settle_amounts CHECK (
        amount >= 0 AND settled >= 0 AND balance >= 0
        AND settled + balance = amount
    )
) ENGINE=InnoDB COMMENT='往来结算表（应收/应付合一，收付款直接冲减 settled/balance）';

-- ---------------- 分拣配送 dlv_ ----------------

CREATE TABLE IF NOT EXISTS dlv_order (
    id            BIGINT        NOT NULL,
    merchant_id   BIGINT        NOT NULL DEFAULT 0,
    dlv_no        VARCHAR(32)   NOT NULL COMMENT '分拣单号',
    sale_order_id BIGINT        NULL COMMENT '关联销售单 id',
    sale_no       VARCHAR(32)   NULL COMMENT '销售单号',
    customer_id   BIGINT        NULL,
    customer_name VARCHAR(64)   NULL,
    total_qty     DECIMAL(12,3) NOT NULL DEFAULT 0 COMMENT '分拣总数量',
    status        CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0 待分拣 1 分拣中 2 已分拣 3 已配送 4 已取消 5 已签收',
    dlv_date      DATE          NULL COMMENT '分拣日期',
    picker        VARCHAR(32)   NULL COMMENT '分拣员',
    deliverer     VARCHAR(32)   NULL COMMENT '配送员',
    delivered_time DATETIME     NULL COMMENT '进入已配送状态时间',
    signed_time   DATETIME      NULL COMMENT '客户签收时间',
    sign_type     VARCHAR(8)    NULL COMMENT '签收来源 MANUAL/AUTO',
    remark        VARCHAR(255)  NULL,
    items_json    JSON          NULL COMMENT '分拣明细 [{"goodsId","goodsName","qty","unit","batchNo"}]',
    create_by     BIGINT NULL, create_time DATETIME NULL,
    update_by     BIGINT NULL, update_time DATETIME NULL,
    deleted       TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    active_sale_order_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND status <> '4' THEN sale_order_id ELSE NULL END
    ) STORED COMMENT '有效销售单唯一键，取消后置空',
    PRIMARY KEY (id),
    UNIQUE KEY uk_dlv_no (dlv_no),
    UNIQUE KEY uk_active_dlv_sale (merchant_id, active_sale_order_id),
    KEY idx_sale_order_id (sale_order_id),
    KEY idx_dlv_merchant (merchant_id),
    KEY idx_dlv_auto_sign (status, delivered_time)
) ENGINE=InnoDB COMMENT='分拣配送单（含明细 JSON，关联销售单）';

-- ---------------- 客户收货地址 base_ ----------------

CREATE TABLE IF NOT EXISTS base_address (
    id             BIGINT        NOT NULL,
    merchant_id    BIGINT        NOT NULL DEFAULT 0,
    party_id       BIGINT        NOT NULL COMMENT '客户 base_party.id',
    receiver_name  VARCHAR(64)   NOT NULL COMMENT '收货人',
    receiver_phone VARCHAR(20)   NOT NULL COMMENT '收货电话',
    address        VARCHAR(255)  NOT NULL COMMENT '收货地址',
    is_default     TINYINT       NOT NULL DEFAULT 0 COMMENT '1 默认地址',
    create_by      BIGINT NULL, create_time DATETIME NULL,
    update_by      BIGINT NULL, update_time DATETIME NULL,
    deleted        TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    default_party_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND is_default = 1 THEN party_id ELSE NULL END
    ) STORED COMMENT '默认地址唯一键，非默认为空',
    PRIMARY KEY (id),
    UNIQUE KEY uk_address_default (merchant_id, default_party_id),
    KEY idx_address_party (merchant_id, party_id)
) ENGINE=InnoDB COMMENT='客户收货地址簿';

-- ---------------- 售损耗报备 sal_ ----------------

CREATE TABLE IF NOT EXISTS sal_claim (
    id              BIGINT        NOT NULL,
    merchant_id     BIGINT        NOT NULL DEFAULT 0,
    party_id        BIGINT        NOT NULL,
    order_id        BIGINT        NOT NULL COMMENT '原销售单 id',
    sale_no         VARCHAR(32)   NULL,
    reason          VARCHAR(500)  NOT NULL COMMENT '报备原因',
    images_json     JSON          NULL COMMENT '图片路径数组',
    video_url       VARCHAR(255)  NULL,
    status          CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0待审核 1已通过 2已驳回 3已补发',
    audit_remark    VARCHAR(255)  NULL,
    audit_by        VARCHAR(32)   NULL,
    audit_time      DATETIME      NULL,
    reship_order_id BIGINT        NULL,
    reship_sale_no  VARCHAR(32)   NULL,
    create_by       BIGINT NULL, create_time DATETIME NULL,
    update_by       BIGINT NULL, update_time DATETIME NULL,
    deleted         TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    active_claim_order_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN deleted = 0 AND status IN ('0', '1') THEN order_id ELSE NULL END
    ) STORED COMMENT '处理中报备唯一键，处理后置空',
    PRIMARY KEY (id),
    UNIQUE KEY uk_active_claim_order (merchant_id, active_claim_order_id),
    KEY idx_claim_order (order_id),
    KEY idx_claim_party (merchant_id, party_id)
) ENGINE=InnoDB COMMENT='客户损耗报备（证据 + 审核 + 补发）';

CREATE TABLE IF NOT EXISTS sal_claim_setting (
    id                 BIGINT NOT NULL,
    merchant_id        BIGINT NOT NULL DEFAULT 0,
    claim_window_hours INT    NOT NULL DEFAULT 14 COMMENT '主动签收后可报损小时数',
    create_by          BIGINT NULL, create_time DATETIME NULL,
    update_by          BIGINT NULL, update_time DATETIME NULL,
    deleted            TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_setting_merchant (merchant_id)
) ENGINE=InnoDB COMMENT='商户损耗报损时限设置';

-- ---------------- 拼单 sal_ ----------------

CREATE TABLE IF NOT EXISTS sal_group (
    id             BIGINT        NOT NULL,
    merchant_id    BIGINT        NOT NULL DEFAULT 0,
    group_no       VARCHAR(32)   NOT NULL,
    goods_id       BIGINT        NOT NULL,
    group_price    DECIMAL(12,2) NOT NULL DEFAULT 0,
    items_json     JSON          NULL COMMENT '多菜品快照 [{"goodsId","goodsName","unit","groupPrice","originalPrice","qty"}]',
    target_count   INT           NOT NULL DEFAULT 3,
    duration_hours DECIMAL(4,1)  NOT NULL DEFAULT 24.0,
    config_date    DATE          NULL,
    member_count   INT           NOT NULL DEFAULT 0,
    leader_party_id BIGINT       NULL,
    status         CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0拼团中 1已成团 2已过期/取消',
    expire_time    DATETIME      NULL,
    create_by      BIGINT NULL, create_time DATETIME NULL,
    update_by      BIGINT NULL, update_time DATETIME NULL,
    deleted        TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_no (group_no),
    KEY idx_group_goods (merchant_id, goods_id, status)
) ENGINE=InnoDB COMMENT='拼单团';

CREATE TABLE IF NOT EXISTS sal_group_member (
    id          BIGINT      NOT NULL,
    group_id    BIGINT      NOT NULL,
    party_id    BIGINT      NOT NULL,
    order_id    BIGINT      NULL,
    sale_no     VARCHAR(32) NULL,
    status      CHAR(1)     NOT NULL DEFAULT '0' COMMENT '0已参与 1已下单 2已退出',
    exit_time   DATETIME    NULL,
    create_by   BIGINT NULL, create_time DATETIME NULL,
    update_by   BIGINT NULL, update_time DATETIME NULL,
    deleted     TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_party (group_id, party_id),
    KEY idx_group_member_party (party_id)
) ENGINE=InnoDB COMMENT='拼单成员';

CREATE TABLE IF NOT EXISTS sal_group_setting (
    id                    BIGINT      NOT NULL,
    merchant_id           BIGINT      NOT NULL,
    enabled               TINYINT     NOT NULL DEFAULT 0,
    default_duration_hours DECIMAL(4,1) NOT NULL DEFAULT 24.0,
    penalty_threshold     INT         NOT NULL DEFAULT 3,
    penalty_window_days   INT         NOT NULL DEFAULT 30,
    penalty_hours         INT         NOT NULL DEFAULT 24,
    create_by BIGINT NULL, create_time DATETIME NULL,
    update_by BIGINT NULL, update_time DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_setting_merchant (merchant_id)
) ENGINE=InnoDB COMMENT='拼单全局设置';

CREATE TABLE IF NOT EXISTS sal_group_daily_config (
    id             BIGINT      NOT NULL,
    merchant_id    BIGINT      NOT NULL,
    biz_date       DATE        NOT NULL,
    confirmed      TINYINT     NOT NULL DEFAULT 0,
    copied_from_date DATE      NULL,
    confirmed_by   VARCHAR(32) NULL,
    confirmed_time DATETIME    NULL,
    create_by BIGINT NULL, create_time DATETIME NULL,
    update_by BIGINT NULL, update_time DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_daily_config (merchant_id, biz_date)
) ENGINE=InnoDB COMMENT='拼单每日配置确认记录';

CREATE TABLE IF NOT EXISTS sal_group_daily_price (
    id          BIGINT        NOT NULL,
    merchant_id BIGINT        NOT NULL,
    biz_date    DATE          NOT NULL,
    goods_id    BIGINT        NOT NULL,
    price       DECIMAL(12,2) NOT NULL,
    create_by BIGINT NULL, create_time DATETIME NULL,
    update_by BIGINT NULL, update_time DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_daily_price (merchant_id, biz_date, goods_id),
    KEY idx_group_daily_price_lookup (merchant_id, goods_id, biz_date)
) ENGINE=InnoDB COMMENT='拼单每日官方菜价';

CREATE TABLE IF NOT EXISTS sal_freight_rule (
    id             BIGINT        NOT NULL,
    merchant_id    BIGINT        NOT NULL,
    biz_date       DATE          NOT NULL,
    customer_type  VARCHAR(16)   NOT NULL,
    base_fee       DECIMAL(12,2) NOT NULL DEFAULT 0,
    free_threshold DECIMAL(12,2) NOT NULL DEFAULT 0,
    create_by BIGINT NULL, create_time DATETIME NULL,
    update_by BIGINT NULL, update_time DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_freight_rule (merchant_id, biz_date, customer_type)
) ENGINE=InnoDB COMMENT='按业务日和客户类型生效的运费规则';

CREATE TABLE IF NOT EXISTS sal_order_cancel (
    id              BIGINT        NOT NULL,
    merchant_id     BIGINT        NOT NULL,
    order_id        BIGINT        NOT NULL,
    sale_no         VARCHAR(32)   NOT NULL,
    customer_id     BIGINT        NOT NULL,
    reason_code     VARCHAR(32)   NOT NULL,
    reason_text     VARCHAR(255)  NOT NULL,
    responsible     TINYINT       NOT NULL DEFAULT 0,
    refund_status   CHAR(1)       NOT NULL DEFAULT '0',
    refund_amount   DECIMAL(12,2) NOT NULL DEFAULT 0,
    operator_type   VARCHAR(16)   NOT NULL,
    operator        VARCHAR(64)   NULL,
    cancel_time     DATETIME      NOT NULL,
    snapshot_json   JSON          NULL,
    create_by BIGINT NULL, create_time DATETIME NULL,
    update_by BIGINT NULL, update_time DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cancel_order (order_id),
    KEY idx_cancel_customer_time (merchant_id, customer_id, responsible, cancel_time)
) ENGINE=InnoDB COMMENT='销售单取消与模拟退款审计';

CREATE TABLE IF NOT EXISTS sal_customer_penalty (
    id              BIGINT      NOT NULL,
    merchant_id     BIGINT      NOT NULL,
    customer_id     BIGINT      NOT NULL,
    trigger_count   INT         NOT NULL DEFAULT 0,
    status          CHAR(1)     NOT NULL DEFAULT '0',
    start_time      DATETIME    NULL,
    end_time        DATETIME    NULL,
    release_reason  VARCHAR(255) NULL,
    release_by      VARCHAR(64) NULL,
    release_time    DATETIME    NULL,
    create_by BIGINT NULL, create_time DATETIME NULL,
    update_by BIGINT NULL, update_time DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_penalty_customer (merchant_id, customer_id, status, end_time)
) ENGINE=InnoDB COMMENT='频繁退单优惠暂停记录';

CREATE TABLE IF NOT EXISTS sal_group_audit (
    id          BIGINT       NOT NULL,
    merchant_id BIGINT       NOT NULL,
    biz_type    VARCHAR(32)  NOT NULL,
    biz_id      BIGINT       NULL,
    action      VARCHAR(32)  NOT NULL,
    before_json JSON         NULL,
    after_json  JSON         NULL,
    operator    VARCHAR(64)  NULL,
    create_by BIGINT NULL, create_time DATETIME NULL,
    update_by BIGINT NULL, update_time DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_group_audit_time (merchant_id, create_time)
) ENGINE=InnoDB COMMENT='拼单价格、开关和人工操作审计';

CREATE TABLE IF NOT EXISTS sal_group_promotion (
    id          BIGINT        NOT NULL,
    merchant_id BIGINT        NOT NULL DEFAULT 0,
    title       VARCHAR(100)  NOT NULL,
    description VARCHAR(500)  NULL,
    start_time  DATETIME      NOT NULL,
    end_time    DATETIME      NOT NULL,
    status      CHAR(1)       NOT NULL DEFAULT '0' COMMENT '0启用 1停用',
    sort        INT           NOT NULL DEFAULT 0,
    create_by   BIGINT NULL, create_time DATETIME NULL,
    update_by   BIGINT NULL, update_time DATETIME NULL,
    deleted     TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_group_promo_active (merchant_id, status, start_time, end_time, sort)
) ENGINE=InnoDB COMMENT='拼菜促销活动';

CREATE TABLE IF NOT EXISTS sal_group_promotion_item (
    id           BIGINT        NOT NULL,
    merchant_id  BIGINT        NOT NULL DEFAULT 0,
    promotion_id BIGINT        NOT NULL,
    goods_id     BIGINT        NOT NULL,
    promo_price  DECIMAL(12,2) NOT NULL,
    create_by    BIGINT NULL, create_time DATETIME NULL,
    update_by    BIGINT NULL, update_time DATETIME NULL,
    deleted      TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_promo_goods (promotion_id, goods_id),
    KEY idx_group_promo_item_goods (merchant_id, goods_id)
) ENGINE=InnoDB COMMENT='拼菜促销活动菜品';
