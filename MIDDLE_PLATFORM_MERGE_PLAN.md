# 分布式 Agent 电商平台 · V1 Architecture & Implementation Baseline

> 状态：**V1 基线 — FROZEN**（冻结前校准完成；先按本文实现，再改文档）  
> 日期：2026-09 · 校准：状态机 / 取消退款 / Runtime 持久化 / Checkout CONFIRMED / Hard Decisions / Tool Contract

**项目名称（对外）**：分布式 Agent 电商平台

> Monorepo 根目录：`E:\workspace\distributed-agent-commerce_分布式智能Agent电商平台`（GitHub 建议仓库名：`distributed-agent-commerce`）  
（亦可并列：分布式电商与智能客服平台）

**一句话定位**：以多品类商城为业务载体，提供需求理解、商品比较、确认式购买与售后协助；交易核心负责库存、支付与履约的可靠执行与故障恢复。用户一句话发起购物任务，系统能解释选择、等待确认、走完**可执行交易链路**，并在超时与重启后恢复到正确业务状态。

| 角色 | 仓库 | 能力 |
| --- | --- | --- |
| **交易核心** | `trade-core/` | 目录 · Checkout · 订单 · 库存预留 · PaymentAttempt · Outbox |
| **支付 / 配送 / 通知** | 同仓 Bank / Delivery / Email | **模拟适配器**（可注入失败/超时）；非真实支付与物流商 |
| **商城 + BFF + Agent Runtime** | `storefront/` | 商城 · 秒杀入口 · 单 Agent · RAG · 受控工具 · Runtime 持久化 |

- 前台 GitHub：https://github.com/kikiarya/OldPhoneStore  
- 前台演示：https://oldphonestore.onrender.com/

**借鉴（非宣称兼容）**：UCP/ACP 的连续体验、实时目录、有状态结算。**未实现前不写「兼容 ACP/UCP」**。

**不再加宽**：不引入 MCP、Graph RAG、Kafka、K8s、service mesh、本地 LLM 作为 V1 必需；Multi-Agent / Reranker 后置（§17 / §8）。

---

## 1. V1 产品范围

| 维度 | V1 决定 |
| --- | --- |
| 商户 | **单商户** |
| 品类 | 多品类**实物**；标准数量库存 + 二手单件样例 |
| 售后 | **未发货取消**（含已支付未发货 → 退款流程）；**不做**已发货退货/换货 |
| 角色 | 用户：浏览、Agent 购物、本人查单/取消；运营：写商品/价/库存到核心 |
| 适配器 | Bank / Delivery / Email = 可演示模拟器 |
| 秒杀 | 分布式模块；Agent 走同一业务接口 |
| 外部协议 | 不实现 ACP/UCP |

「不限于二手手机」≠「多商户平台」。

### 1.1 V1 Hard Decisions（全部已选定，禁止「实现时再选」）

| 项 | V1 最终决定 |
| --- | --- |
| Merchant | Single merchant |
| Currency | **CNY** |
| 运费 | 订单商品小计 **&lt; 99 CNY → 运费 10 CNY**；**≥ 99 → 包邮**；无其它优惠 |
| 配送范围 | 演示用国内地址字符串即可；核心不做精细地理围栏，只校验非空地址字段 |
| Product 模型 | 现表 **Product = 可售卖 SKU**（sku / name / price）；SPU/Item 拆分后置；二手先用库存=1 |
| 交易核心 DB | **PostgreSQL**（与现仓一致） |
| 前台遗留 / Runtime DB | **SQLite**（OldPhoneStore）：遗留只读单 + `agent_runs` 等 |
| RAG 存储 | 同机：**文档文件 + 本地向量索引**（实现可选 sqlite-vss / 简单嵌入缓存）；非独立向量云 |
| Inventory | `available` / `reserved`；**条件更新**预留；报价阶段不占库存 |
| Multi-line shortage | **整单失败**（同事务逐行预留，任一行失败 rollback） |
| Payment | Simulated Bank + **PaymentAttempt** + **Reconciler** |
| Payment timeout | Attempt=`UNKNOWN`，Order=`PAYMENT_PENDING`，**Reservation 保留，不释放** |
| Bank API | 必须支持 `pay(paymentAttemptId, amount)` 与 **`query(paymentAttemptId)`** |
| Cancellation | 仅未发货；未支付直接取消释放库存；**已支付未发货 → 退款工作流** |
| Returns | **Not supported** |
| Checkout 确认 | 核心 Session 状态 **CONFIRMED + quoteVersion**（不用独立 confirmationToken） |
| Agent | **Single Agent** + 明确工具集 |
| Agent state | **Durable**：SQLite `agent_runs` / `agent_steps` / `tool_executions` |
| Model | Replaceable API；本地模型可选、非前提 |
| RAG 范围 | 政策 / FAQ / 商品说明；**不做商品库** |
| 结构化搜品 | `search_products` → **Trade Core** |
| Price/stock/order/refund | **Trade Core 实时** |
| Deployment 主演示 | **Docker Compose 本机双栈（模式 A）** |
| `USE_TRADE_CORE=false` | **禁止创建新核心交易**；可只读展示遗留 SQLite；**核心历史订单始终路由 Trade Core** |
| ACP/UCP | Not implemented |

---

## 2. 架构

```text
商城 / Agent 对话 / 运营后台
           ↓
      Express BFF（身份转发、DTO、聚合）
           ↓
 Agent Runtime（同进程；任务状态、工具 ACL、确认门闩、恢复）
      ↓                         ↓
 受控业务工具              RAG（解释性知识）
      ↓
 Java Trade Core（Checkout / Order / Reservation / PaymentAttempt / Outbox）
      ↓
 Bank / Delivery / Email（模拟适配器）+ RabbitMQ
```

| 组件 | 职责 | 不负责 |
| --- | --- | --- |
| BFF | 身份转发、DTO、聚合读 | 交易编排、扣款 |
| Agent Runtime | 任务状态、工具控制、确认等待、**durable 恢复** | 金额/库存权威、伪造用户 |
| RAG | FAQ、政策、商品说明 | 价库订单；**商品候选召回** |
| Trade Core | Session、订单、预留、支付协调、归属校验、`search_products` | 话术、向量检索 |
| Redis | 缓存、秒杀准入 | 履约库存真相 |
| Outbox + MQ | 可靠领域事件 | — |

**原则**：浏览器不直连核心写接口；模型不拥有用户身份；成交须 Checkout **CONFIRMED** 后由核心 complete。

### UI 归属

| 界面 | 归属 |
| --- | --- |
| C 端 / Agent / 秒杀 | OldPhoneStore |
| COMP5348 React | 核心调试台 |
| `/admin` | 运营写 → Trade Core |

---

## 3. 明确不做（V1）

- monorepo 硬揉；假装已接好；SQLite/PG 订单双写真相  
- BFF 循环单品下单当终态；Agent 无 Session 直接扣款  
- 模糊聊天当付款授权；跨用户查单  
- flag 关闭后把核心历史单切回 SQLite  
- 虚拟商品、订阅、跨境、多商户、已发货退换货  
- 未验证写「保证最终一致性」/「兼容 ACP/UCP」  
- 本地 LLM 当准确性前提；V1 多 Agent（§17）  
- 继续堆 MCP / GraphRAG / Kafka / K8s 等名词

---

## 4. 领域模型与交易状态机（P0）

### 4.1 商品

V1：`Product` 实体 = **可售卖 SKU**。类目扩展属性用 JSON。二手：库存 1 或后续 Item。

### 4.2 库存字段与防超卖

每仓/SKU（或聚合视图）维护 `available`、`reserved`。预留：

```sql
UPDATE inventory
SET available = available - :qty,
    reserved  = reserved  + :qty
WHERE sku_id = :sku AND available >= :qty;
-- affected_rows == 1 → 成功；== 0 → 缺货
```

多 SKU：同一 Store DB 事务内逐行预留；任一失败 **整单 rollback**。

**InventoryReservation 状态**：`HELD` → `COMMITTED`（支付成功）| `RELEASED`（支付明确失败或未支付取消）。

### 4.3 Complete 与支付生命周期（冻结）

```text
complete checkout（Session 必须为 CONFIRMED 且 quoteVersion 匹配）
        ↓
Store 本地事务
---------------------------------
创建 Order = PAYMENT_PENDING
创建 InventoryReservation = HELD   -- 条件更新扣减 available
create PaymentAttempt（paymentAttemptId）
---------------------------------
        ↓ commit

调用 Bank.pay(paymentAttemptId, amount)  （事务外）
        ↓

SUCCESS → Order=PAID；Reservation=COMMITTED；写 Outbox（配送等）
明确失败 → Order=FAILED；Reservation=RELEASED（还 available）
超时/网络错误 → PaymentAttempt=UNKNOWN；Order=PAYMENT_PENDING；
                 Reservation 保持 HELD（绝不因超时直接释放）
        ↓
Reconciler 用 paymentAttemptId 调 Bank.query
        ↓
SUCCESS / FAILED → 同上终态转换
```

| 问题 | V1 答案 |
| --- | --- |
| 先扣库存还是先建单？ | **同事务**：建 Order(PENDING) + Reservation(HELD) + PaymentAttempt，再调 Bank |
| Bank 超时库存？ | **保留 HELD**；由 Reconciler 决定 |
| UNKNOWN 维持多久？ | Reconciler 周期重试（如 30s）；超过演示阈值（如 24h）标 `NEEDS_REVIEW`，人工/脚本处理，**仍不自动当失败释放**除非 query 明确 FAILED |
| 谁查 Bank？ | Store 内 **Payment Reconciler**（已有 pending 恢复思路升级为按 attemptId query） |
| Agent 重启查什么？ | Runtime durable：`checkoutId`/`orderId`/`idempotencyKey` → 调核心 get；禁止 new key |
| 取消 PAYMENT_PENDING？ | 允许：先 query Bank；若仍未知则拒绝取消或进入人工；若未支付成功则 Order=CANCELLED、Reservation=RELEASED |

### 4.4 订单 / 退款展示

| 展示 | 权威 |
| --- | --- |
| processing | Order=`PAYMENT_PENDING` |
| paid | `PAID` |
| failed | `FAILED` |
| cancelled | 订单已取消（未支付取消，或已支付取消且退款流程已启动后的订单态） |
| refund_pending / refunded / refund_unverified | **核心 Refund 记录**（模拟 Bank 退款 + query） |
| shipped / completed | **核心 + Delivery 事件**；前台投影只展示 |

### 4.5 取消 vs 退款（消除矛盾）

**V1 一句话**：  
支持未发货取消——**未支付**订单直接取消并释放库存；**已支付未发货**取消后进入退款流程。V1 **不支持**已发货退货、换货。

```text
未支付 + 未发货取消 → CANCELLED + Reservation RELEASED
PAID + 未发货取消 → CANCEL_REQUESTED → Refund=PENDING → REFUNDED（或 unverified）
已发货 → 拒绝取消（V1）
```

退款状态是「已支付未发货取消」的配套，**不是**退货业务。

---

## 5. Checkout Session

报价**不占库存**；complete 时原子预留。

### 5.1 服务器状态（取代 confirmationToken）

```text
POST   /checkouts                    → DRAFT
POST   /checkouts/{id}/quote         → QUOTED (quoteVersion++)
POST   /checkouts/{id}  (改行/地址)  → 回到需重新 quote；清除 CONFIRMED
POST   /checkouts/{id}/confirm       → CONFIRMED（绑定当前 quoteVersion）
POST   /checkouts/{id}/complete      → 校验仍为 CONFIRMED + 同 version
                                       → 创建订单…（§4.3）
```

| 规则 | 决定 |
| --- | --- |
| 确认 | 用户触发 `confirm`；核心写入 CONFIRMED + version + 用户 id |
| 失效 | 改价/商品/数量/地址/重新 quote → 非 CONFIRMED |
| complete body | `{ checkoutId, idempotencyKey }`；version 以服务端 CONFIRMED 为准 |
| 过期 | quote/confirm 过期时间写死（如 30 min）；过期不可 complete |

**禁止** Agent 把「帮我买」直接映射为扣款接口。

---

## 6. 购物 Agent

**主故事**：800 元内安静办公键鼠 → 澄清 → `search_products` → RAG 补证据 → 比较 → Checkout → confirm → complete → 查支付/物流 → 未发货取消。

单 Agent + 工具集；多 Agent 见 §17。

### 6.1 选品：结构化搜索 vs RAG

```text
User 约束
  → search_products(category, priceMax, filters)  @ Trade Core
  → 真实 SKU candidates
  → search_knowledge / 商品说明 RAG（静音吗、适不适合办公）
  → LLM 比较（须引用证据）
  → create/update checkout
```

**RAG 不当商品数据库。**

### 6.2 Agent Runtime 持久化（P0）

同进程内：

- **memory**：单次 request 瞬时状态  
- **durable（SQLite）**：

| 表 | 最低字段 |
| --- | --- |
| `agent_runs` | runId, userId, status, currentStep, checkoutId, orderId, **idempotencyKey**, createdAt, updatedAt |
| `agent_steps` | runId, step, input/output 摘要 |
| `tool_executions` | runId, tool, args hash, result ref, status |

**`idempotencyKey` 必须在调用 `complete` 之前写入 durable。**  
恢复：读 run → 若已有 orderId/checkout 结果则查询核心展示；**禁止**新生成幂等键再 complete。

### 6.3 Agent Tool Contract（V1 完整清单）

Agent 只使用 **task-level business tools**，不直接操作底层资源。

| 工具 | 作用 |
| --- | --- |
| `search_products` | 结构化召回 SKU |
| `get_product` | SKU 详情（价库以核心为准） |
| `search_knowledge` | FAQ/政策/说明 RAG |
| `create_checkout` | 创建 Session |
| `update_checkout` | 改行/地址（使确认失效） |
| `get_checkout` | 读 Session/报价 |
| `confirm_checkout` | 用户确认后调用 |
| `complete_checkout` | 仅 Session=CONFIRMED |
| `get_order` | 本人订单 |
| `cancel_order` | 本人 + 未发货规则 + 用户确认 |

**禁止暴露给 Agent**：`set_price`、`set_inventory`、`charge_bank`、`update_order_status`、`refund_money` 等。

知识/权限表：FAQ→RAG；价库订单退款→核心；身份→服务端上下文；描述中的指令≠工具权限。

---

## 7. 契约要点

### 幂等

- ≤48 字符；**complete 前持久化**于 Runtime（及核心订单键）；重启复用  
- 同键同指纹 → 原结果；同键异指纹 → **409**

### 身份

- Store 验服务凭证 + 用户；**禁止缺省 userId=1**  
- 查单/取消/工具：仅本人或运营

### Flag

`USE_TRADE_CORE=false` → **拒绝新核心交易**（提示启核心）；遗留 SQLite 只读展示；凡有核心 orderId 的**永远**走核心。

---

## 8. 模型与 RAG

可替换 API + 带版本 RAG + 实时工具 + 核心校验。本地模型可选。

RAG：版本、依据、缺资料声明、核心优先于文案价库、检索内容不提权。重排：评测不足再加。

---

## 9. 秒杀

活动额度核心划拨；Redis 准入；超时≠失败；同键恢复；资格持久态。规则与 §4.3 UNKNOWN **同一哲学**。

---

## 10. Outbox 最小语义

1. 业务状态变更与 **outbox insert 同一 DB 事务**  
2. Worker：`PENDING` → publish → `SENT`  
3. publish 失败：保持 PENDING，退避重试  
4. Consumer：按 **eventId 去重**，成功再 ACK  

不引入 Kafka/CDC。验收：停 MQ → 积压 → 恢复续传。

---

## 11. 演示拓扑

| 模式 | 用途 |
| --- | --- |
| **A Compose 本机双栈** | **主演示 / 参考环境** |
| B 双云 | 可选公网 |
| C Render 前台 | flag=false；不宣称已接核心 |

从阶段 1 起保留启动方式与 `runId`/`checkoutId`/`orderId`/`paymentAttemptId` 关联证据。

---

## 12. 实施主线（先交易，后 Agent）

```text
0 冻结（本文）
→ 1 单品：checkout→quote→confirm→complete→预留→PENDING→Bank→恢复→取消/退款
→ 2 多行 + 运营写入
→ 3 购物 Agent（工具对接上述 API）
→ 4 秒杀与对账
→ 5 退货等售后 / 协议适配（后置）
```

**禁止先做 Agent 再补交易。** Agent 出问题时应能区分 Runtime/模型 vs 交易未通。

### DoD 摘要

- 阶段 1：§4 状态机可演示；Reconciler；未支付取消；已支付未发货取消→退款记录；Runtime 可不必先做完，但 REST 闭环必须通  
- 阶段 2：多行整单失败；Admin 写核心  
- 阶段 3：Tool Contract + durable Runtime + §13/§14 Agent 验收  
- 阶段 4：秒杀丢响应恢复  

---

## 13. 验收

### 13.1 交易与故障

1. 单品：Order + HELD/COMMITTED + Bank 流水或明确 FAILED  
2. 停 MQ：Outbox 续传  
3. pay 超时：UNKNOWN，库存不释放；query 后到终态  
4. 幂等：同指纹原结果；异指纹 409；重启不双单  
5. 跨用户拒绝  
6. flag=false：新交易拒绝；历史核心单仍走核心  
7. 多行缺货整单失败  
8. 秒杀丢响应恢复  
9. 未支付取消释放库存；已支付未发货取消有核心退款态  

### 13.2 Agent

| 场景 | 必须观察到 |
| --- | --- |
| 无匹配组合 | 说明并建议改条件 |
| 改预算/数量 | 新报价；旧 CONFIRMED 失效 |
| 缺资料 | 不编造 |
| 未 confirm | 无订单/扣款 |
| confirm 后涨价/缺货 | 拦截并重新 quote |
| complete 响应丢失 | 同键恢复，一单 |
| 描述含恶意指令 | 不提权、不泄单 |
| Runtime 进程重启 | 从 SQLite 恢复并展示核心真实进度 |

---

## 14. 评测计划

### 14.1 分层

| 层 | 内容 | 模型 |
| --- | --- | --- |
| L0 | 权限、幂等、状态机、Session、归属 | mock LLM 即可 |
| **Live Eval (L1)** | 模型/prompt/工具路由质量 | **必须真实模型** |
| **Trace Replay** | Runtime/解析/恢复回归 | 录制轨迹；**不能**评价新模型能力 |

换 Prompt/Model → 跑 Live Eval，不能只靠 Replay。

### 14.2 规模与目录

约 40–60 条；`evals/cases/*.jsonl` + gold + reports（同前）。

### 14.3 指标（定义收紧）

| 指标 | 定义 | V1 通过线 |
| --- | --- | --- |
| L0 pass | 断言全过 | 100% |
| Tool correctness | **case-level**：该 case 全部 required tool 及关键参数正确才算过 | ≥90% |
| Faithfulness | 关键事实可映射检索或 API | ≥90% |
| 幻觉/越权 | 编造价库、未确认下单、跨用户 | **固定评测集内观察为 0**（一票否决；≠宣称系统永无幻觉） |
| Retrieval | 金标 doc/sku 在 **Recall@5** | ≥85% |
| Task success | 主故事类到正确终态 | ≥80%（记实绩） |
| Latency/Cost | p95、token | 只记录 |

---

## 15. 风险（已收敛）

Hard Decisions 已覆盖原「二选一」。剩余实现风险：Reconciler 与模拟 Bank query 对齐；Checkout 表结构落库；Runtime 与 complete 的键写入时序单测覆盖。

---

## 16. 简历文案（目标态）

验证前勿当作已完成。措辞用「可执行交易链路 / 业务状态机与故障恢复」，避免与「模拟 Bank」冲突的「真实支付」误解。通过 §13/§14 后再写最终一致性类表述。

**分布式 Agent 电商平台**｜2025.09 – 2026.xx

- 对话完成需求理解、比较与确认式下单；Checkout Session + Trade Core 裁定金额与库存。  
- Order / InventoryReservation / PaymentAttempt + Outbox；支付未知由 Reconciler 按 attemptId 恢复。  
- 单 Agent：可替换模型 + 版本化 RAG + 业务工具契约；价库订单走核心。  
- 秒杀准入与核心额度隔离。Compose 故障注入；Render 为隔离演示。

---

## 17. V2 多 Agent（非 V1）

预研草案：Coordinator / Scout / Quote Steward / Concierge / Policy；成交门闩仍在核心 CONFIRMED；共享 runId 与同一幂等键；默认同进程多角色。  
**启动条件**：V1 阶段 1–3 + 评测通过，且失败归因显示角色混淆为主。未达标不写「已实现多 Agent」。

（角色表与切片细节保持预研级，不扩展进 V1 实现。）

---

## 18. 冻结结论

- 业务设计、总体架构、Agent 边界、实施主线：**冻结，可开工**。  
- 本次校准已消歧：支付/库存/订单状态机、取消与退款、Runtime SQLite 持久化、Checkout CONFIRMED、Hard Decisions、Tool Contract、搜品 vs RAG、Outbox 语义、评测措辞。  
- **下一步**：阶段 1——用 REST 打通 `quote → confirm → complete → reservation → pay/query → cancel/refund`；再挂 Agent 工具。
