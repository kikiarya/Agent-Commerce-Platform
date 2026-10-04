# 主线迁移记录 · 2026-10-04

主线为本仓库：`E:\workspace\distributed-agent-commerce_分布式智能Agent电商平台`。
旧目录 `E:\usyd\5348\distributed-ecommerce-platform` 保留，不再作为后续开发主线。

## 已迁入

- Store JWT 鉴权、报价版本确认、1–100 个不同 SKU 的原子下单、订单价格和地址快照、支付不明状态恢复。
- 原 React 调试前端转发 Bearer 登录令牌。
- Storefront 显式关联 Core 身份、服务端加密保存核心 JWT、结算归属校验、持久化幂等键。
- 浏览器展示报价后确认；同一结算恢复重试；核心订单查询与取消不受新建开关影响。
- 订单与客服查询的身份隔离；核心不可用时不使用本地过期状态冒充实时结果。
- 首页移除内部架构文案。

## 验证

在本主线运行 Java `test bootJar --offline`：46 项测试通过、四个服务打包成功。测试使用 H2/JPA 与模拟外部服务，不代表 PostgreSQL/RabbitMQ/真实支付全链路验收。测试结束存在 H2 PUBLIC schema 清理日志，报告无失败。

在本主线运行 Storefront `npm test`：9 项 BFF 集成测试通过。使用临时 SQLite 和模拟 Core HTTP，覆盖账号关联、多行输入、越权、报价版本、投影故障后的原键恢复、关闭开关后的核心路由、客服查询隔离和登出。

浏览器已检查商城登录、双商品购物车、核心账号关联弹窗和更新后的首页文案；页面控制台未发现错误。浏览器使用主线静态文件与真实 BFF，Core 为模拟服务，不能算完整服务联调。

## Windows 中文路径

Windows protoc 在中文目录下编译失败。可将同一目录临时映射到一个未占用的英文盘符，例如 R:，无须复制仓库：

```powershell
subst.exe R: 'E:\workspace\distributed-agent-commerce_分布式智能Agent电商平台'
Set-Location R:\trade-core
.\gradlew.bat test bootJar --offline
# 使用结束后离开 R: 再解除映射（不会删除文件）
Set-Location E:\workspace
subst.exe R: /D
```

## 数据和回退

没有复制、重置或合并任何已有 SQLite/PostgreSQL 数据库、Docker 卷或 Redis 数据。新增 BFF 关联表在使用时创建。旧的默认用户 1 映射不作为身份凭据；历史归属需要逐条核对。旧本地商品 ID 也不会默认当作核心 SKU。

初次迁入的逐文件 SHA256 清单在 `E:\usyd\5348\migration-20261004\manifest.json`，被替换原文件在同目录 `backup`。该目录只是暂存和回退资料，不是另一个开发主线。迁入后的测试文件、迁移记录及后续文案调整以本仓库 Git diff 为准。

需要回退时先停止相关服务、保存当前 diff，依据清单逐文件比对后恢复备份。不要直接覆盖迁移后新增的人工修改；不要删除数据库或使用全仓库 reset。此次没有提交或推送 Git。

## 尚未包含

自主购物 Agent、向量 RAG、真实模型评测、秒杀接入核心交易、历史数据对账、完整 Docker 服务联调与生产部署。订单列表的核心记录仍是显式标记的本地投影，详情查询才读取核心实时状态。