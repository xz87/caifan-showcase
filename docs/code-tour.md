# 代码阅读顺序

## 1. 先看认证

1. `backend/controller/MallAuthController.java`
2. `backend/auth/MallTokenService.java`
3. `backend/auth/MallAuthInterceptor.java`
4. `backend/test/UserChannelAccessTest.java`

重点观察登录令牌生成、Redis 活跃标记、单账号互斥和统一 401。

## 2. 再看业务接口

1. `backend/controller/MallApiController.java`
2. `backend/service/OrderCancellationService.java`

重点观察订单查询、取消退款、库存批次回补和事务边界。

## 3. 文件与报表

1. `backend/service/GoodsThumbnailService.java`
2. `backend/support/StatementExcelWriter.java`

重点观察图片压缩、缓存路径规则和 Apache POI 表格输出。

## 4. 签收与报损

1. `backend/service/DeliveryAutoSignService.java`
2. `backend/service/DlvOrderService.java`
3. `backend/service/LossClaimService.java`
4. `backend/controller/ClaimController.java`

重点观察送达与签收来源、12 小时自动签收、报损资格判断、商户时限配置和补发闭环。

## 5. 微信小程序

1. `miniprogram/utils/request.js`
2. `miniprogram/pages/index/index.js`
3. `miniprogram/pages/index/index.wxml`
4. `miniprogram/pages/orders/index.js`
5. `miniprogram/pages/claim/index.js`

重点观察 Bearer Token、购物车状态持久化、缩略图回退、8 秒静默轮询、下拉刷新和报损提交。

## 6. Android

1. `android/utils/request.js`
2. `android/pages/index/index.vue`
3. `android/pages/orders/index.vue`
4. `android/pages/claim/index.vue`
5. `android/pages/groups/index.vue`

重点观察 uni-app API 封装、订单状态轮询、页面生命周期清理、签收报损、退单和拼单交互。

## 7. 数据库

1. `database/schema.sql`
2. `database/migrations/2026-11-26_manual_claim_auto_sign.sql`

重点观察 31 张业务表、FIFO 批次、唯一生成列、报损时限表以及签收扫描索引。

## 说明

本仓库代码为从完整项目中抽取的展示快照，保留了原始包名和关键依赖引用，但省略了数据库配置、完整模块和运行资源，因此不能单独启动。
