# 状态存储与稳定序列化

## 文档状态

- **已批准决策：** D-013、D-016、D-017、D-025、D-028～D-030 固定了原子状态语义、Redis/Valkey 基线、版本化 UTF-8 JSON、TTL 双重检查、Redis 命令映射、不可解码状态语义和 Cluster 延期边界。
- **实现事实：** `chalsense-core` 已提供无运行时依赖的 `StateJsonCodec`，并由 `docs/test-vectors/state-json-v1.json` 的 Java runner 验证；`chalsense-store-redis` 已实现该 SPI。
- **已批准兼容性项：** D-027 已冻结 payload 精确字段集合、writer 字段顺序、严格 reader 和 golden bytes。冻结前草案 SHA-256 为 `8af527c5be54044c58608a5781c9f9b7ab9b3bd95f94dd5350648ed643947125`，首个冻结文件 SHA-256 为 `919142f4f087edeb01a3a4e555909caa7cca7ed5690466f8e4fa4ca9ea7f1c87`。
- **范围：** 本文定义 Core 状态值和 `StateStore` SPI 合约，不定义 Redis key 前缀、连接池、客户端库或部署凭据。

## 稳定 envelope

D-017 已批准顶层结构：

```json
{
  "storageVersion": 1,
  "kind": "challenge",
  "protocolVersion": "1",
  "payload": {}
}
```

`storageVersion` 决定持久化 reader，`protocolVersion` 决定线协议语义，两者不得混用。`kind` v0.1 只接受 `challenge` 或 `ticket`，调用方必须选择对应的强类型解码方法，不能依赖类名多态或客户端提供的 Java 类型。

## storageVersion 1 payload

### Challenge

固定字段为：

- `challengeType`
- `siteKey`
- `action`
- `contextDigest`
- `issuedAt`
- `expiresAt`
- `geometry`
- `policyVersion`

`geometry` 固定包含 `pieceStartX`、`pieceTargetX`、`pieceStartY`、`pieceWidth`、`pieceHeight` 和 `tolerance`。目标和容差只存在于受信任状态，不进入 challenge 公开响应。

### Ticket

固定字段为：

- `siteKey`
- `action`
- `contextDigest`
- `challengeType`
- `policyVersion`
- `verifiedAt`
- `issuedAt`
- `expiresAt`

Redis value 不保存原始 `verificationTicket`；key 使用 D-022 固定的 `SHA-256(rawTicketBytes)` 摘要。

## Writer 合约

- 输出无 BOM、无缩进、无尾随换行的 UTF-8 JSON。
- 字段顺序固定，数值只使用十进制安全整数，不使用浮点或指数形式。
- 字符串按 JSON 规则转义；不输出无效 surrogate。
- 当前单个状态编码上限为 16 KiB；超过上限失败，不截断。
- writer 只写当前 `storageVersion = 1`，不伪造旧版本。

固定顺序仅用于可诊断性和 golden bytes；它不是 reader 接受输入的顺序要求。

## Reader 合约

- 严格验证 UTF-8，拒绝 BOM、非法字节、空输入和超过 16 KiB 的输入。
- 允许 JSON 合法空白和任意成员顺序。
- 拒绝重复字段、未知字段、缺失字段、错误类型、错误 `kind`、未知 `storageVersion` / `protocolVersion`。
- 拒绝小数、指数数值、前导零和超出 `[-9007199254740991, 9007199254740991]` 的整数。
- 通过协议值对象和状态构造器再次校验标识、Base64url、坐标及时间范围。
- 解码失败统一抛出 `StateSerializationException`；适配器不得把内部状态内容或解析位置返回给不可信调用方。

## `StateStore` 原子合约

| 操作 | 必须保证的语义 |
| --- | --- |
| `storeChallengeIfAbsent` | 按 `(siteKey, challengeId)` 原子 create-if-absent；不得覆盖；明确冲突返回 `ALREADY_EXISTS` |
| `takeChallenge` | 原子读并删除；最多一个调用取得 `Present`；取走后不得恢复 |
| `storeTicketIfAbsent` | 按 ticket digest 原子 create-if-absent；不得覆盖 |
| `takeTicket` | 原子读并删除；最多一个调用取得 `Present`；取走后不得恢复 |

`Failed` 表示 Store 确认操作未产生状态变化；`Unknown` 表示调用方无法知道操作是否生效。`TakeResult.Unreadable` 表示状态已经原子取走但其字节无法由冻结 reader 安全解码，Store 不得恢复原始值。Core 对 `Unknown` 和 `Unreadable` 均失败关闭且不以同一凭据透明重试；后者记录 `STORE_STATE_UNREADABLE`。生产 Store 必须根据状态中的 `expiresAt` 设置硬 TTL；Core 仍执行服务端时钟到期检查。

当前纯 Java和真实服务端集成测试均以 32 个同时调用验证 create-if-absent 和 take 最多一个胜者。CI 对 Redis OSS 7.2.14 与 Valkey 7.2.14 执行同一组 create/take、TTL、并发、损坏状态和跨 Store 实例测试；命令网关故障注入覆盖断连/响应丢失的 `Unknown` 且不重试语义。真实故障切换仍需部署级测试。

## 版本演进

同一 `storageVersion` 不得静默改变必需字段或字段含义。新增版本必须：

1. 先发布可读取新旧版本的 reader；
2. 再切换 writer 写入新版本；
3. 保留旧 golden vectors 和迁移测试；
4. 最后在明确完成最长 TTL 与回滚窗口后移除旧 reader。

状态 JSON 是 ChalSense 内部兼容面，不是业务可直接读写的 API。业务系统仍必须通过 Core 或受信任服务接口消费 ticket。
