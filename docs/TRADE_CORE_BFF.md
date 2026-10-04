# Storefront → Trade Core 契约

浏览器使用前台 JWT；Express 从登录身份取得核心账号关联，解密 Store 颁发的 JWT 并以 Bearer 转发。不会使用客户端 userId、X-User-Id、默认用户 1 或前台签名密钥冒充核心用户。

## 身份

- `GET /api/auth/core-link` 查询关联/登录有效性。
- `POST /api/auth/core-link` 提交 `{username,password}` 到核心登录。BFF 不保存密码、不向浏览器返回核心 JWT。
- `core_account_links` 一对一关联，已有用户不能切换 Core 身份。登出清除核心令牌但保留映射。
- 核心令牌 AES-256-GCM 加密存入 SQLite；使用 `CORE_TOKEN_ENCRYPTION_KEY`，未设置时从前台 JWT 密钥派生。密钥至少 32 字节。更换密钥后需要重新登录，不能据此宣称完整密钥管理。
- 旧 `user_id_map` 保留备查，不自动认证；需要本人验证核心账号。历史共享用户 1 的订单需人工核对，不能批量认领。

## 结算

全部接口要求前台登录，并校验 `core_checkout_owners` 中的归属。

| 接口 | 输入/语义 |
| --- | --- |
| POST /api/checkout | items 1–100 行，正整数 quantity，不重复 SKU；shippingAddress |
| GET /api/checkout/:id | 状态、报价版本、持久化 idempotencyKey |
| PUT /api/checkout/:id | 修改商品/地址，核心清除旧报价确认 |
| POST /api/checkout/:id/quote | 生成报价 |
| POST /api/checkout/:id/confirm | `{quoteVersion}` 必须是展示给用户的版本 |
| POST /api/checkout/:id/complete | `{quoteVersion}`，使用服务端持久化键；提供不同键返回 409 |

核心目录的商品使用显式 skuId。本地 product_id/phone_id 必须经过 product_sku_map；默认不假定两个数据库主键相同。浏览器不传 bankMock。

UI 保存结算 ID、幂等键、报价版本和购物车快照用于刷新重试；先展示商品单价、数量、运费、总价与地址，再请求确认。网络不明时不创建新结算。已完成结算可再次 complete 修复 SQLite 订单投影。同一浏览器更改购物车后恢复旧结算，不会清掉新购物车。

关闭 USE_TRADE_CORE 只禁止创建新核心结算；已创建结算、核心订单读取/取消仍走 Store。核心商品不能落入本地下单。订单详情和客服核心订单查询使用实时核心结果，失败明确返回不可用；列表的核心记录标记 stale_projection，需查详情刷新。

## 范围与限制

SQLite 投影不是交易事实来源。核心提交成功、BFF 投影失败时，用同一结算重试修复；尚无后台投影对账任务。浏览器清理本地存储或换设备后，登录同一个商城账号，通过 /orders.html 读取服务端归属记录恢复结算。创建核心结算后 BFF 意外退出可能留下未确认的孤立结算，不会因此自动扣款。

账号关联是过渡方案，不是统一 SSO。历史结算没有 BFF 归属记录时拒绝访问，需要核对后补录。旧库数据、商品映射、Redis 秒杀数据没有在此次代码迁移中自动合并。客服仍为关键词 FAQ/规则工具，未实现自主 Agent 交易闭环。回调、WebSocket/gRPC 与公网部署安全不在此次验收范围。

## P0 订单与结算恢复

首页我的订单入口为 /orders.html。GET /api/checkout 按当前身份分页读取服务端结算记录，每页 20 条。核心读取失败的记录标记 unavailable，不显示伪造状态。列表读取没有交易副作用。页面支持交易账号重新登录、查询实时订单状态、重新核对并恢复结算，以及找回已完成但本地投影缺失的订单。恢复使用服务端原幂等键，不依赖浏览器保存的报价。

订单列表暂显示最近 50 条本地记录；详情手动刷新读取实时核心状态。尚无配送轨迹时间线或自动轮询。没有 BFF 归属记录的历史结算不自动认领。接口回归共 18 项通过；真实 Docker 全链路和新增页面的浏览器验收尚待完成。