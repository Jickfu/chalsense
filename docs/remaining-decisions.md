# 阶段 0 技术决策依据

## 文档状态

- 状态：建议已于 2026-08-22 获批准，正式结论以 `docs/decisions/README.md` 的 D-013～D-018 为准。
- 目标：保留选型比较、事实依据和被否决方案；本文不替代决策记录。
- 范围：资源交付、Redis 最低版本、稳定序列化、`siteKey` 注册和服务端密钥轮换，以及协议/坐标草案中暴露出的兼容性选择。

## 已批准方案摘要

| 项目 | 已批准方案 | 主要理由 | 替代方案的影响 |
| --- | --- | --- | --- |
| 资源交付 | challenge 响应返回两个短时不透明 URL，分别提供背景 WebP/PNG 与透明拼图片 PNG | 避免 Base64 膨胀和 JSON 大对象；浏览器缓存、流式响应、CSP 与独立服务更清晰 | 必须重写协议资源对象、缓存和部署边界 |
| Redis 最低版本 | Redis OSS 7.2.x 或 Valkey 7.2.x；实现只依赖 Redis OSS 7.2 命令子集 | `GETDEL` 已可用；7.2 兼容面和许可证/生命周期比锁定 6.2 或 7.4+ 更适合作为首版基线 | 6.2 需承担更短支持期；7.4+/8 会改变许可证与兼容声明 |
| 稳定序列化 | 自有、版本化、UTF-8 JSON 状态模型；严格 reader、显式字段、整数与 Base64url；Redis string + TTL | 跨语言、可迁移、可诊断；不耦合 Java/第三方内部类型 | CBOR/Protobuf 需新增依赖、Schema 工具与未知字段策略 |
| `siteKey` | 公开、随机、稳定标识；通过 `SiteRegistry` 注册 Origin、action、状态和受信任凭据 | 同时覆盖嵌入式与服务模式，不把公开 key 当秘密 | 若用域名或自增 ID，会增加迁移、枚举和多 Origin 歧义 |
| 服务端密钥轮换 | 每站点多个带 `keyId` 的 256 位 service API key；新旧重叠、显式吊销；浏览器永不持有 | 无管理后台也能离线轮换；独立服务有清晰受信任调用面 | mTLS-only 运维较重；单密钥替换会造成停机窗口 |
| ticket 形态 | 32 字节随机、不透明、状态型 token；Redis key 使用 `SHA-256(rawTicketBytes)` | 单次消费本就需要状态；无需签名/查找密钥轮换，泄露 Redis key 不能还原 token | 签名 token 仍需防重放状态且引入签名算法与 key ring |
| 坐标 | `1_000_000` 整数规范化单位、固定舍入、服务端容差 | 跨语言边界确定，不受 DPR 和 Canvas backing store 影响 | CSS pixel/浮点协议需要更多显示尺寸信任和容差分歧处理 |

## D-014 与 Q-004：坐标及最终容差

### 已批准方案与条件

批准 `docs/coordinates.md` 的整数规范化坐标、舍入、clamp 与 Pointer Events 规则。容差算法暂以以下值进入原型，而不是直接作为永久安全参数：

```text
tolerance = clamp(roundHalfAwayFromZero(pieceWidth * 8 / 100), 6250, 18750)
```

原型和初始真实用户测试必须验证误拒率；任何调整都要提升 `policyVersion`，且 Challenge State 固化创建时的容差。

### 取舍

- 单纯 source pixel 容差会随图片分辨率变化。
- 单纯 CSS pixel 容差依赖客户端声称的显示尺寸，且跨缩放不稳定。
- 固定百分比会在极大/极小模板上失控，因此使用模板宽度比例加规范化上下限。
- 该算法只定义位置误差，不等于轨迹可信度或真人证明。

### 批准条件

机器向量先冻结换算和边界；容差数值在原型完成前保留“工作假设”状态。若评审要求阶段 0 必须冻结最终数值，则阶段 0 尚不能通过。

## D-015：资源交付方式

### 已批准方案

1. `challenge.create` 返回 `resources[]`，v0.1 固定两个角色：`BACKGROUND`、`PIECE`。
2. `url` 为短时、不可预测的资源 URL；同源部署返回相对 URL，独立服务可返回已配置的绝对 HTTPS URL。
3. 背景优先 `image/webp`，不支持时允许 PNG；带透明通道的拼图片使用 PNG。编码选择是资源元数据，不改变坐标语义。
4. URL 生命周期不超过 challenge；资源 ID 至少 128 位随机。URL 是 bearer-like 读取能力但图片本身不是秘密，安全性不依赖隐藏图片。
5. 响应设置准确 `Content-Type`、`X-Content-Type-Options: nosniff`、有限 `Content-Length`、`Referrer-Policy: no-referrer`。缓存建议 `private, max-age=<challenge TTL>`；默认不做跨挑战共享缓存。
6. 资源服务只读取挑战资源，不可读取答案、ticket 或业务上下文。资源缺失时 Widget 放弃本 challenge 并新建，不用占位图继续验证。

### 不建议首版采用

- Base64/data URL：约三分之一编码膨胀，增加 JSON 解析、复制和日志误捕获风险。
- 单个复杂组合图：减少一次请求但增加裁剪、透明边界与坐标实现分歧；待测得网络收益后再扩展。
- 永久公开原始素材 URL：便于题库预计算，也会把素材库布局变成兼容接口。
- 把短时签名 URL 当作答案保护：图片和客户端逻辑始终可被攻击者观察。

## D-016：Redis 最低版本与兼容声明

### 已核对事实

- Redis 官方命令文档标明 `GETDEL` 自 6.2.0 可用。
- Redis 官方当前版本管理页列出的支持期中，Redis 6.2 到 2027-04-01，Redis 7.2 到 2029-12-01；日期会变化，发布前需再次核验。
- Redis 官方许可证页说明 7.2.x 及更早版本为 BSD-3-Clause，7.4 使用 RSALv2/SSPLv1，8+ 提供 RSALv2/SSPLv1/AGPLv3 三选一。
- Valkey 官方迁移文档说明 Valkey 7.2.4 源自 Redis OSS 7.2.4，并保持 RESP、配置与 Redis OSS 7.2 及更早版本兼容。

参考：

- [Redis `GETDEL`](https://redis.io/docs/latest/commands/getdel/)
- [Redis Open Source 版本管理](https://redis.io/docs/latest/operate/oss_and_stack/install/version-mgmt/)
- [Redis 许可证](https://redis.io/legal/licenses/)
- [Valkey 兼容与迁移](https://github.com/valkey-io/valkey-doc/blob/main/topics/migration.md)

### 已批准方案

- 产品声明：支持 Redis OSS 7.2.x 和 Valkey 7.2.x 及其后续兼容版本；CI 至少各跑一个当前维护 patch 版本。
- 实现基线：只使用 Redis OSS 7.2 核心命令、RESP2/RESP3 客户端兼容行为和单 key Lua/`GETDEL` 原子操作，不依赖 Redis 8 集成模块或 Valkey 专属命令。
- 简单状态取走优先 `GETDEL`；若一次操作必须校验类型/版本或联动同一 hash slot 内的附属 key，使用有 SHA 缓存与 `NOSCRIPT` 恢复的 Lua，并在 Redis 与 Valkey 都运行相同向量。
- 首版不承诺 Redis 6.2。它技术上具备 `GETDEL`，但支持窗口接近尾声；接入方若要求 6.2，应作为单独兼容评审而不是默认基线。
- 文档用“Redis-compatible State Store”描述协议能力，用准确产品名列出已测试实现，避免暗示所有兼容数据库都受支持。

### 部署安全最低线

- 专用 ACL 用户，只开放所需 key 前缀与命令；禁止公网暴露，生产使用传输加密或受保护私网。
- 安全状态操作必须到可写主节点；不从副本读取。
- key 必须带环境/实例命名空间；Redis Cluster 的脚本只操作同一 hash slot。
- 明确持久化、复制与故障转移会影响最近状态丢失风险；高安全等级部署需验证写确认策略。

## D-017：稳定序列化格式

### 已批准方案

Redis value 使用自有 UTF-8 JSON，顶层固定：

```json
{
  "storageVersion": 1,
  "kind": "challenge",
  "protocolVersion": "1",
  "payload": {}
}
```

约束：

- `storageVersion` 与 `protocolVersion` 分离；reader 按 `(kind, storageVersion)` 分派。
- 只允许对象、数组、boolean、null、字符串和安全范围整数；字节使用无填充 Base64url；时间使用 epoch ms。
- 禁止 Java 原生序列化、类名、多态 default typing、第三方 `AnyMap`、浮点和未版本化 Map。
- writer 输出固定字段顺序以便诊断和 golden test，但 reader 不依赖成员顺序。
- reader 拒绝重复字段、未知 `storageVersion`、错误 `kind`、缺失安全字段和越界值；同版本未知字段默认拒绝，迁移由新版本显式处理。
- 每个版本有 golden bytes、旧版读取和迁移测试；写入永远使用当前版本，滚动升级期间必须先部署可读新旧版本的 reader。
- Redis TTL 是硬删除边界，JSON 内 `expiresAt` 是 Core 的第二道过期检查。

### 完整性与机密性

工作假设仍把 Redis 视为高敏感受信任基础设施。v0.1 不默认对状态再做应用层加密；答案不进入日志，Redis 通过网络、ACL 和运维隔离保护。若未来要求防 Redis 管理员读取或离线篡改，应单独决定 AEAD envelope、密钥托管和轮换，不能在 JSON 中临时添加自制加密。

## D-018：`siteKey` 注册模型

### 已批准方案

新增 `SiteRegistry` Core SPI。一个 Site 配置至少包含：

| 字段 | 规则 |
| --- | --- |
| `siteKey` | 公开、稳定、随机生成，建议 `site_` + 16 字节 Base64url；不可包含租户名称或域名 |
| `displayName` | 仅运维用途，不进入安全比较 |
| `status` | `ACTIVE`、`DISABLED`；禁用后拒绝新建/验证/消费 |
| `allowedOrigins` | 精确规范化的 scheme/host/port 集合；禁止带路径与通配凭据 |
| `allowedActions` | 明确 action 集合，不接受任意客户端 action |
| `challengePolicy` | TTL、资源、容差/轨迹 `policyVersion` 和限流引用 |
| `serviceCredentials` | 独立服务模式的受信任调用凭据列表；嵌入式模式可为空 |

v0.1 不建设管理后台。嵌入式和服务模式都通过同一配置模型加载：本地强类型配置、挂载的受控配置文件或调用方实现的 SPI。启动时检查唯一性、Origin 规范化、action 格式、TTL 边界和密钥状态；错误配置失败启动，不静默放宽。

`siteKey` 不是认证凭据。Origin 校验是浏览器使用隔离，不阻止非浏览器伪造 Origin；公开接口还必须限流。

## D-018：服务端凭据与轮换模型

### 已批准方案

- 独立服务模式为每个 site 配置一个或多个 `serviceCredential`：`keyId` + 32 字节随机 secret + 状态 + 创建/失效时间。
- HTTP 使用标准 `Authorization` header 承载 bearer 形式的高熵 secret，并用单独 header 或 token 前缀定位 `keyId`；禁止 query string、Cookie 和浏览器存储。
- registry 不保存可直接使用的明文 secret；保存适合高熵 token 校验的摘要。比较采用常量时间，认证失败返回统一错误。
- 轮换流程：新增 `ACTIVE` 新 key → 部署调用方 → 观察旧 key 无调用 → 将旧 key 标为 `REVOKED`。允许短期两个 `ACTIVE`，不允许无期限重叠。
- 紧急吊销立即拒绝对应 key。key 日志只记录 `keyId`，不记录 secret。
- 凭据用途限制为受信任 API；若未来拆分管理与 consume 权限，使用显式 scope，而不是复用同一 secret。
- 嵌入式模式直接依赖进程内调用边界，不为了形式一致把 service secret 放入同一 JVM。

### ticket 与轮换解耦

`verificationTicket` 为 32 字节随机值，Redis 查找 key 使用 `SHA-256(rawTicketBytes)`；精确定义见 D-022。由于 token 具有 256 位随机熵，未加密摘要不提供现实可行的离线猜测空间；该设计不需要 ticket 签名 key 或查找 key 轮换。若未来改为自包含签名 token，仍因单次消费保留状态，并需新增算法、`keyId`、轮换和降级攻击决策，因此 v0.1 不建议采用。

## 已批准决策映射

评审结论已拆分为：

1. D-013：v0.1 线协议、原子消费点、结果未知与错误合并语义。
2. D-014：整数坐标、舍入、轨迹结构和容差原型基线。
3. D-015：短时 URL 的资源交付方式。
4. D-016：Redis OSS/Valkey 7.2 兼容基线与部署安全要求。
5. D-017：版本化 JSON 状态序列化。
6. D-018：`SiteRegistry`、公开 `siteKey`、service credential 与轮换模型。

其中 D-014 的坐标确定性原型已经通过；当前容差继续作为 v0.1 初始工作参数，最终经验性校准仍由 Q-004 跟踪并作为 v0.1 发布前门禁。其他建议均已从待决策移入已解决记录。
