# 协议测试向量说明

## 文档状态

- 状态：v0.1 verify/consume 协议向量与 create 补充向量均已独立冻结；两套 Java Core runner 均已通过。
- 机器可读向量：`docs/test-vectors/protocol-v1.json` 与 `docs/test-vectors/challenge-create-v1.json`。
- 状态序列化冻结向量：`docs/test-vectors/state-json-v1.json`；它依 D-027 固定 storageVersion 1 的字节与 reader 合约。冻结前草案 SHA-256 为 `8af527c5be54044c58608a5781c9f9b7ab9b3bd95f94dd5350648ed643947125`，首个冻结文件 SHA-256 为 `919142f4f087edeb01a3a4e555909caa7cca7ed5690466f8e4fa4ca9ea7f1c87`。
- 首个冻结文件 SHA-256：`1ecdf006dbd26b0a1ce0ee1915c843465178b2d7fcb673513ffa7c9487a8c666`。
- create 补充冻结文件 SHA-256：`2d037c079d2def5e03985a66ae0e3c40be2924123a99d2da81b515188817d799`；其冻结前草案 SHA-256 为 `e0106b7a1a0e4bb11bbb9460cdbf7287b3a80aae41ca88fd07db640fd0bc9db9`。
- 目标：未来 Java Core、HTTP 服务和其他语言实现对相同输入、时钟、随机源与 State Store 脚本产生一致的安全结果。

向量不是性能基准，也不证明轨迹启发式能识别真人。它验证的是编码、状态转换、边界、绑定与故障语义。

## Runner 合约

每个实现的 vector runner 必须能注入：

- 固定服务端时钟 `now`；
- 固定 challenge / ticket 状态；
- 固定 ticket 随机输出；
- State Store 结果：`PRESENT`、`ABSENT`、明确失败和 `TAKE_RESULT_UNKNOWN`；
- 并发屏障，用于让多个调用同时进入原子 `take`；
- 关闭真实网络、图片生成和业务框架的纯 Core 模式。

create runner 还必须能注入固定 challenge ID 序列、确定性 `ChallengeGenerator`、站点状态/Origin/action 策略，以及 `CONFIRMED`、`ALREADY_EXISTS`、`FAILED`、`UNKNOWN` 写入结果序列。

Runner 对每个向量至少断言：

1. 对外 outcome 或错误码；
2. 原子 `take` 调用次数；
3. challenge/ticket 在操作后的存在性；
4. ticket 签发或成功消费次数；
5. 结果未知时没有透明重试；
6. 对外响应不含 `internalReason`。`internalReason` 只用于测试内部可观测事件。

## 轨迹引用展开规则

JSON 中 `trackRef` 是测试夹具缩写，不是公开协议字段：

- `validTrack`：直接使用 `fixtures.validTrack`。
- `validTrack-with-final-x-N`：复制 `fixtures.validTrack`，仅将末点 `x` 改为整数 `N`。
- 公开请求发送前，runner 必须展开为真实 `track` 并删除 `trackRef` / `trackFinalX`。

## 覆盖矩阵

| 能力 | 向量 |
| --- | --- |
| 创建成功与浏览器 Origin 规范化 | `V-CREATE-001/002`（create 草案） |
| 创建前站点、action、Origin 拒绝且不改状态 | `V-CREATE-003`～`006`（create 草案） |
| 创建写失败与结果未知 | `V-CREATE-007/008`（create 草案） |
| ID 冲突重试与上限 | `V-CREATE-009/010`（create 草案） |
| 生成器失败 | `V-CREATE-011`（create 草案） |
| 正常验证与消费 | `V-VERIFY-001`、`V-CONSUME-001` |
| 容差闭区间与越界 1 单位 | `V-VERIFY-002`～`004` |
| 到期边界 `now >= expiresAt` | `V-VERIFY-005`、`V-CONSUME-004` |
| 失败也消费 | `V-VERIFY-004/006`、`V-CONSUME-002/003/004` |
| 重放或不存在不可枚举 | `V-VERIFY-007`、`V-CONSUME-005` |
| 存储结果未知 | `V-VERIFY-008`、`V-CONSUME-006` |
| 存储确认在 take 前失败 | `V-VERIFY-010`、`V-CONSUME-007` |
| 验证成功但 ticket 写入失败或结果未知 | `V-VERIFY-009/011` |
| 并发线性化 | `V-CONCURRENCY-001/002` |
| 解析器分歧 | `V-PARSER-001`～`004` |

## 必须补充但不冻结具体样本的生成测试

机器可读示例之外，每种实现还必须以相同性质执行属性/生成测试：

- 任意 N（2～64）个并发验证或消费调用，成功数不超过 1。
- `finalPieceX` 在整数最小值、最大值、`target ± tolerance` 与再越过 1 单位的边界。
- 轨迹 0、1、2、256、257 点；时间倒退；重复 `START/END`；缺失末点；坐标越界。
- `siteKey`、`action`、`contextDigest`、版本每次只改变一项的绑定矩阵。
- TTL 比 Core `expiresAt` 早/晚清除、服务时钟回拨和多个实例时钟漂移。
- 原子命令在执行前失败、执行后响应丢失、ticket 写入成功后响应丢失。
- HTTP 适配器和进程内 API 对同一 Core fixture 返回相同逻辑 outcome。

## 向量版本管理

- 冻结前的 draft SHA-256 已记录在 `protocol-v1.json`；冻结文件中的已有向量只能追加，不能改变输入或 expected。
- 若发现旧向量存在安全错误，保留原文件并标记撤销原因，由新的协议主版本替代，不能静默重写历史。
- 序列化格式版本与线协议版本独立；内部存储迁移向量应放在未来 Store 专用集合中。
- `challenge-create-v1.json` 是 D-026 冻结的独立补充集合，不修改 D-023 已冻结文件。它的已有向量同样只能追加，不能改写输入或 expected；若发现安全错误，必须保留证据并通过替代决策或新协议版本处理。
