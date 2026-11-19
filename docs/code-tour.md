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

## 4. 微信小程序

1. `miniprogram/utils/request.js`
2. `miniprogram/pages/index/index.js`
3. `miniprogram/pages/index/index.wxml`

重点观察 Bearer Token、购物车状态持久化、缩略图回退和订单提交。

## 5. Android

1. `android/utils/request.js`
2. `android/pages/index/index.vue`
3. `android/pages/orders/index.vue`
4. `android/pages/groups/index.vue`

重点观察 uni-app API 封装、移动端页面结构、支付、签收、退单和拼单交互。

## 说明

本仓库代码为从完整项目中抽取的展示快照，保留了原始包名和关键依赖引用，但省略了数据库配置、完整模块和运行资源，因此不能单独启动。
