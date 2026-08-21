# Redis / Valkey State Store 实现设计

## 文档状态

- **已批准决策：** D-016 固定 Redis OSS 7.2.x / Valkey 7.2.x 与核心命令子集；D-017、D-027 固定状态 JSON；D-025 固定基于 `expiresAt` 的硬 TTL；D-028/D-029 固定客户端、key、命令、故障映射和不可解码状态语义；D-030 将 Redis Cluster 入口延期。
- **实现事实：** `chalsense-store-redis` 已使用 Jedis 7.5.3、单 key `SET NX PXAT` / `GETDEL` 和二进制 API 实现 `StateStore`。
- **范围：** 本文件同时记录已批准设计、实现边界和验证拓扑；不把部署凭据、TLS/ACL 参数或未来限流纳入当前模块。

## 客户端调研事实

调研日期：2026-08-22。版本与维护状态在升级依赖时必须重新核验。

| 候选 | 已核对事实 | 与 ChalSense 的关系 |
| --- | --- | --- |
| Jedis 7.5.3 | MIT；官方兼容表覆盖 Java 17/21 和 Redis 7.2；7.2 起提供新的池化 `RedisClient` / `RedisClusterClient` API；同步二进制 API直接提供 `SET ... NX PX/PXAT` 与 `GETDEL` | Core 的 `StateStore` 当前为同步短命令接口，不需要引入 reactive 编程模型；池化客户端适合嵌入式和服务模式 |
| Lettuce 7.6.0 | MIT；线程安全 multiplexed 连接；同步、异步和 reactive API；支持 TLS、Sentinel、Cluster；当前版本测试 Redis 7.2～8.x | 高并发异步服务能力成熟，但会把 Netty、Reactor 及其版本协调引入 Redis 模块，而 v0.1 Store 不需要 reactive API |
| Valkey-Java | MIT；由 Jedis fork 而来，由 Valkey 社区维护；当前路线图仍列有异步 API等后续工作 | 可作为未来替代适配器；首版同时支持两个服务端时，使用同一客户端执行完全相同测试更能发现服务端行为差异 |

主要参考：

- [Jedis 仓库、兼容表与许可证](https://github.com/redis/jedis)
- [Jedis 生产使用与异常模型](https://redis.io/docs/latest/develop/clients/jedis/produsage/)
- [Jedis 连接、池化、Cluster 与 TLS](https://redis.io/docs/latest/develop/clients/jedis/connect/)
- [Lettuce 概览与 Java 要求](https://redis.github.io/lettuce/overview/)
- [Lettuce Cluster、SSL 与拓扑刷新](https://redis.github.io/lettuce/ha-sharding/)
- [Valkey-Java 仓库与路线图](https://github.com/valkey-io/valkey-java)
- [Redis `SET`](https://redis.io/docs/latest/commands/set/) 与 [`GETDEL`](https://redis.io/docs/latest/commands/getdel/)

## 依赖选择（已批准）

**D-028 已批准选择 Jedis 7.5.3，并固定精确版本。**

原因：

1. `StateStore` 只执行短时同步单 key 命令，Jedis 的池化同步 API与当前 Core 模型直接匹配。
2. `RedisClient` 的池化同步命令面适合 standalone 部署；虽然 `RedisClusterClient` 提供相同命令面，但其连接异常自动重试不满足当前一次性消费边界，依 D-030 暂不公开。
3. 不把 Netty/Reactor 版本协调引入嵌入式用户，降低与未来 Spring Boot 依赖管理的冲突面。
4. Redis OSS 与 Valkey 必须使用同一客户端、同一命令和同一测试集合，不能以两个客户端的差异掩盖服务端兼容问题。

Jedis 7.5.3 的非测试传递依赖包括 `slf4j-api`、Apache Commons Pool 2、`org.json`、Gson、Error Prone annotations 与 `redis-authx-core`。许可证分别为 MIT、Apache-2.0、Public Domain、Apache-2.0、Apache-2.0 和 MIT；`redis-authx-core` 当前版本标记为 beta，首版不得启用 token-based authentication，升级时需单独跟踪其稳定性。可选 Resilience4j 不引入。

Lettuce 7.6.0 是可接受替代方案；若未来 Core 增加真正异步 Store SPI 或服务性能测试证明 multiplexing 有明确收益，再重新评审，不在 v0.1 同时维护两个生产客户端。

## 模块边界

`chalsense-store-redis` 出现了独立职责和集成测试，满足 D-020 的建模块条件：

```text
chalsense-store-redis
  ├─ depends on chalsense-core
  ├─ depends on redis.clients:jedis
  ├─ RedisKeyspace（确定性 key 构造）
  ├─ JedisStateStore（StateStore 实现）
  └─ Redis/Valkey integration tests
```

模块不得依赖 Spring，不实现限流、站点注册、HTTP、资源存储或业务重试。`StateJsonCodec` 仍由 Core 提供，Redis 模块不得自行维护第二套 JSON 映射。

当前公开构造方式只接受池化 `RedisClient`，且调用方负责客户端生命周期。不得直接传入 `RedisClusterClient`；Cluster 专用适配器必须先解决 D-030 的命令重放问题并通过真实 Cluster 故障测试。

## Key 格式

D-028 固定：

```text
<namespace>:v1:challenge:<siteKey>:<challengeId>
<namespace>:v1:ticket:<ticketDigestHex>
```

- 默认 `namespace = chalsense`；部署可提供带环境含义的前缀，例如 `chalsense.prod`。
- namespace 限制为 1～64 个 ASCII `[A-Za-z0-9._-]` 字符，禁止空白、冒号、`{}` 和控制字符。
- `v1` 是 key schema 版本，不等于 `storageVersion` 或 `protocolVersion`。
- challenge key 可以包含公开 `siteKey` 和随机 `challengeId`；ticket key 只能包含 D-022 定义的摘要，不包含原始 bearer token。
- 所有 v0.1 操作只涉及一个 key，因此让 Redis Cluster 根据完整 key 自然分片，不使用会把全站状态压入单一 slot 的固定 hash tag。
- key 是内部运维兼容面，业务不得自行拼接或直接访问。

## 命令与 TTL

| SPI 操作 | Redis 命令 | 确认结果 |
| --- | --- | --- |
| `storeChallengeIfAbsent` | `SET key value NX PXAT expiresAt` | `OK → CONFIRMED`；nil → `ALREADY_EXISTS` |
| `storeTicketIfAbsent` | `SET key value NX PXAT expiresAt` | `OK → CONFIRMED`；nil → `FAILED`（随机摘要碰撞，不覆盖） |
| `takeChallenge` | `GETDEL key` | bytes → 解码；nil → `ABSENT` |
| `takeTicket` | `GETDEL key` | bytes → 解码；nil → `ABSENT` |

`PXAT` 直接使用状态中冻结的 epoch-ms `expiresAt`，避免客户端重新计算相对 TTL。Redis/Valkey 与 Core 主机仍必须同步系统时钟；Core 保留 `now >= expiresAt` 第二道检查。

这些都是 Redis 7.2 核心单 key 命令，v0.1 不需要 Lua、事务、pipeline、读副本或客户端缓存。只有未来出现必须联动多个同 slot key 的原子语义时才引入固定 Lua，并单独增加 `NOSCRIPT` 恢复测试。

## 故障映射

| 观察 | SPI 结果 | 理由 |
| --- | --- | --- |
| 本地 key / 状态编码在发送前失败 | `FAILED` | 已确认没有命令发出 |
| Redis 明确返回命令错误 | `FAILED` | 服务端已返回未执行的错误；记录低基数内部事件 |
| 连接断开、timeout 或响应丢失 | `UNKNOWN` | 命令可能已经执行，禁止透明重试 |
| `GETDEL` 返回字节且解码成功 | `PRESENT` | 状态已原子取走并可验证 |
| `GETDEL` 返回字节但解码失败 | `UNREADABLE` | 状态已确定取走，但不能构造安全可信的状态对象 |

### 已解决的 SPI 缺口

现有 `TakeResult.Failed` 明确定义为 Store 确认没有取走状态，`TakeResult.Unknown` 定义为不知道是否取走。`GETDEL` 成功返回损坏或未知版本字节时，两者都不准确：Store 已确定取走，但 reader 必须失败关闭。

**D-029 已新增 `TakeResult.Unreadable<T>`：** 表示原始状态已原子取走、未恢复，但无法通过冻结 reader。Verify/consume 对外统一映射 `DEPENDENCY_UNAVAILABLE`，内部记录新的低基数 `STORE_STATE_UNREADABLE`；调用方不得重试同一凭据。该分支不暴露原始字节、字段名或解析错误。

不建议把损坏数据恢复到 Redis，也不建议用 `GET` → Java 解码 → `DEL`，后者会破坏跨实例单次消费原子性。

## 测试拓扑

### 每次 Maven 构建

- key 格式和 namespace 拒绝规则；
- `SET` / `GETDEL` 返回与异常到 SPI 结果的纯单元测试；
- frozen state JSON 与 Redis 二进制 value 逐字节一致；
- 32 路 create-if-absent / take 合约；
- `UNREADABLE` 已取走且不会恢复。

### CI 集成矩阵

| JDK | 服务端镜像 |
| --- | --- |
| 17、21 | `redis:7.2.14` |
| 17、21 | `valkey/valkey:7.2.14` |

两个服务端执行同一测试：create/take、TTL、并发、错误类型、损坏 JSON、跨实例消费和 key 隔离。镜像固定完整 patch tag；升级前查看安全公告并重新执行矩阵。Redis 7.2.14 和 Valkey 7.2.14 是截至调研日期 Docker 官方/项目镜像提供的安全 patch 标签。

本地环境当前没有 Docker 命令，因此真实服务端集成测试在 CI 和具备 Redis/Valkey 的开发机执行；本机仍执行全部纯 Java 合约测试。连接失败和响应丢失的结果映射已由命令网关故障注入测试覆盖，且断言同一操作只调用一次；真实网络分区与故障切换仍属于部署级后续测试，不以自动重试“修复”。

## 实施状态

1. 已创建 `chalsense-store-redis`，生产只提供基于池化 `RedisClient` 的 Jedis standalone 客户端实现，不依赖 Spring。
2. 已实现固定 key schema、冻结二进制 value、`SET NX PXAT` / `GETDEL` 与异常映射。
3. 已实现 `TakeResult.Unreadable` 以及 Core 的失败关闭和低基数安全事件映射。
4. 已配置 CI 在 Java 17/21 上对 Redis 7.2.14 与 Valkey 7.2.14 执行相同集成测试。
5. 本地 JDK 17/21 纯 Java 构建必须通过；真实服务端矩阵的最终证据以 CI 运行结果为准。
6. Redis Cluster 入口依 D-030 延期，不属于当前已实现兼容范围。
