# 架构方向

## 文档状态

本文记录已经形成共识的架构方向。详细协议、安全、坐标、前端与生成器规则分别见 `docs/protocol.md`、`docs/threat-model.md`、`docs/coordinates.md`、`docs/widget.md` 和 `docs/slider-generator.md`。阶段 0 已通过评审；公开字段与调用方策略以 D-013～D-018、D-021～D-025 为准，模块与发布坐标以 D-019～D-020 为准，Redis Store 以 D-028～D-030 为准，Widget 以 D-032 为准，生产滑块生成边界以 D-033 为准。

## 总体结构

```text
业务系统
  ├─ Java API / Spring Boot Starter
  └─ HTTP API
        ↓
ChalSense Protocol
        ↓
ChalSense Core
  ├─ Challenge Generator
  ├─ Coordinate Model
  ├─ Verifier
  ├─ Track Heuristics
  ├─ Ticket Issuer
  ├─ State Store SPI
  ├─ Rate Limit SPI
  └─ Metrics and Audit Events
        ↑
TypeScript Web Component

薄服务层 = HTTP 适配 + 配置装配 + 健康检查
```

## 模块方向

模块名称与发布坐标已经批准；当前已创建三个 Maven 模块和一个 npm workspace：

- `chalsense-protocol`：公共请求、响应、错误、版本和 JSON Schema。
- `chalsense-core`：挑战生成、验证、票据、安全状态机和扩展接口；不依赖 Spring。
- `chalsense-store-redis`：Redis/Valkey 状态、原子消费、TTL、短时资源和 D-035 原子双桶限流实现。
- `chalsense-spring-boot-starter`：Spring Bean、配置绑定和接入适配。
- `chalsense-server`：薄 HTTP 服务层，不重复实现 Core 逻辑。
- `@chalsense/widget`：TypeScript Web Component、Canvas 渲染、Pointer Events 和主题。
- `chalsense-testkit`：固定挑战、测试密钥、时间控制、重放和故障场景。

首版不应为了模块名整齐拆出没有独立职责的空模块。最终拆分要服从依赖边界和发布需要。

当前 Maven 依赖方向为 `chalsense-server → chalsense-store-redis → chalsense-core → chalsense-protocol`。`chalsense-protocol` 不含生产依赖，`chalsense-core` 与 Redis Store 均不依赖 Spring；Spring Boot 只存在于 Server 适配层。`@chalsense/widget` 是独立 npm workspace，不依赖 Java 模块或 UI 框架，通过协议类型和冻结坐标向量保持一致；Starter 与 Testkit 仍等待真实职责和相应测试。

## 协议概览

```text
业务准备提交受保护动作
  → 创建 challenge，携带 siteKey、action、contextDigest
  → 服务端返回 challengeId、展示数据、协议版本和过期时间
  → Web Component 展示并采集有限轨迹
  → 客户端提交 challengeId、展示几何和轨迹
  → 服务端原子消费 challenge
  → 验证成功签发短时一次性 verificationTicket
  → 客户端随原业务请求提交 verificationTicket
  → 业务服务端原子消费票据并校验 siteKey、action、contextDigest
  → 业务根据验证码、限流、账户风险和其他策略综合决策
```

### 协议原则

- `challengeId` 使用固定 128 位不可预测随机值；`verificationTicket` 使用固定 256 位不可预测随机值。
- 创建、验证和消费使用显式协议版本。
- 任意验证尝试都消费挑战，防止针对同一答案反复试探。
- 成功票据短时有效、单次消费，并绑定站点、动作和业务上下文摘要。
- 登录场景不得把明文账号交给 ChalSense；由业务计算不透明摘要。
- 客户端不能指定服务端答案、允许误差、模板、任意图片尺寸或高风险策略。
- 客户端成功事件只表示组件拿到票据，不代表业务请求已获准。
- 票据格式可以是服务端状态型随机令牌或签名令牌，但只要要求单次使用，就仍需服务端原子消费状态。

D-013、D-014、D-022 已批准使用状态型 256 位随机 token、`SHA-256(rawTicketBytes)` Redis 查找摘要、先原子取走再校验、结果未知失败关闭，以及整数规范化坐标。以 `docs/protocol.md`、`docs/coordinates.md` 和机器向量为准；D-034 已冻结 HTTP v0.1 基线，但 `0.x` 构件尚未作 v1.0 稳定性承诺。

### 票据消费拓扑

业务系统不得直接读写 ChalSense 的 Redis Key。两种部署方式共享相同的 Core 消费语义：

```text
嵌入式模式
业务代码 → ChalSense Java API → Core TicketService.consume() → State Store SPI → Redis

服务模式
业务后端 → 受信任 HTTP API → Core TicketService.consume() → State Store SPI → Redis
```

- Java 嵌入式项目在进程内调用，不承担额外网络开销。
- 跨语言或独立部署项目由业务后端调用受信任 HTTP API。
- 前端不得直接访问票据消费接口，只能将票据随受保护业务请求提交给业务后端。
- Redis Key、Lua、TTL 和序列化格式均属于 ChalSense 内部实现，不构成公共 API。

## 坐标模型

坐标协议必须先于 UI 实现确定，至少区分：

- 源背景图像素坐标。
- 源模板图像素坐标。
- 逻辑展示坐标。
- Canvas backing store 像素。
- CSS 像素。
- Pointer Events 的视口坐标。

D-014 规定公开协议统一使用 `1_000_000` 整数规范化坐标，显示逻辑尺寸只用于渲染。前端必须从真实按下点记录相对位移，不能假设用户总是从滑块中心拖动。确切公式、舍入、clamp、DPR 和容差工作假设见 `docs/coordinates.md`。

需要通过属性测试覆盖任意等比例缩放、非整数尺寸、不同 DPR、滑块任意抓取点、边界取整和触控事件。

## 轨迹模型

- 轨迹点包含相对 `x`、相对 `y`、相对时间和事件类型。
- 服务端限制轨迹点数量、总时长、坐标范围、事件顺序和请求体大小。
- 首版轨迹判断只称为启发式规则，并返回内部原因码供指标使用。
- 完整轨迹不得写入普通日志或长期保存。
- 在没有真实样本、标注、离线评估和回滚机制前不引入机器学习模型。

## 状态与原子性

已定义两个状态对象：

- Challenge State：答案、几何、类型、站点、动作、上下文摘要、创建时间、过期时间、协议版本。
- Ticket State：站点、动作、上下文摘要、签发时间、过期时间、消费状态、协议版本。

首个生产实现使用 Redis OSS / Valkey：

- 使用 `SET NX PXAT` 与 `GETDEL` 实现单 key 原子写入和取走；当前不需要 Lua。
- 序列化使用 ChalSense 自有、带版本的稳定模型，不暴露第三方内部 Map。
- 连接、timeout 或响应丢失映射 `Unknown`，不可解码的已取走状态映射 `Unreadable`；Core 均失败关闭且不透明重试。
- 内存实现只用于单元测试、本地演示和明确的非生产环境。

D-030 发现 Jedis `RedisClusterClient` 会在连接异常后自动重新执行命令，因此 v0.1 当前只实现基于池化 `RedisClient` 的 standalone 部署。Cluster 不是已实现兼容范围；必须在专用适配器能强制不重放结果不确定命令并完成真实 Cluster 故障测试后再开放。

storageVersion 1 的 Core codec、严格 reader、固定 writer 与原子 Store 合约见 `docs/state-storage.md`。D-027 已冻结逐字节 golden vectors；后续实现不得静默改变同一 storageVersion 的字段或字节合约。

## 技术选择方向

生产滑块生成器依 D-033 位于 Core，使用 JDK `BufferedImage` / `ImageIO` 输出有界 PNG，不新增运行时依赖。素材通过 `BackgroundImageSource` 注入，两个二进制资源通过 `ChallengeResourcePublisher` 整包发布；publisher 不接触答案和业务上下文。状态写入未确认时 Core 尽力删除资源，硬 TTL 负责最终清理。具体对象存储、共享文件系统或资源 HTTP 路由仍属于适配层，不进入 Core。

- Java 基线：源码和字节码最低支持 Java 17，CI 至少验证 Java 17 与 Java 21。
- 服务运行时：独立服务推荐 Java 21，但不得使用 Java 21 专属 API 破坏 Core 兼容性。
- 前端：TypeScript、原生 Web Component、Canvas、Pointer Events。
- 协议：OpenAPI 3.1 与 JSON Schema。
- 自动化测试：JUnit、属性测试、Testcontainers、Vitest、Playwright。
- 发布：Maven Central、npm、GitHub Container Registry。
- 许可证：Apache-2.0；贡献流程使用 DCO。

## 薄服务层边界

首版服务层只负责：

- 暴露 challenge、verify、ticket consume 或受信任的服务端校验接口。
- 配置 Core、Redis 和密钥材料。
- 健康检查、指标、结构化错误和必要的 Origin/CORS 策略。
- 生成 OpenAPI 文档和 OCI 镜像。

首版服务层不负责：

- 管理后台、多租户计费、运营报表和用户系统。
- 自建服务发现、动态配置中心或复杂集群编排。
- 在适配层复制 Core 的状态机、验证器或票据逻辑。

## PoW 与替代验证方向

- v0.1 只发布滑块拼图，不实现正式 PoW，也不提前公开未实现的 `POW` 协议类型。
- Core 从首版提供 `ChallengeGenerator`、`ChallengeVerifier` 等验证类型扩展边界。
- v0.1 接入方必须为不能完成视觉挑战的用户提供 MFA、邮件验证或人工协助等替代路径。
- PoW 计划在 v0.2 评估并实现，实施前必须验证低性能设备、WebView、耗电、Worker、内存不足和算法参数。
- PoW 只能作为提高批量请求成本的挑战，不得宣称它可以证明用户是人。

## 仍需验证的问题

D-013～D-035 已批准上述协议、资源、存储、站点模型、协议词法约束、ticket 摘要输入、调用方/Origin、TTL、Redis Store、不可解码状态语义、Cluster 延期、Widget、生产滑块生成边界、HTTP v0.1 与公开限流/代理信任边界。D-023 冻结了 verify/consume 向量，D-026 独立冻结了 create 补充向量，D-027 冻结了 storageVersion 1 状态 JSON。D-014 确定性原型也已通过并由 Widget Vitest runner 执行。Q-004 的最终经验性容差校准属于 v0.1 发布前门禁；它可以调整 `policyVersion` 和服务端容差，但不得改变整数坐标线协议或把容差交给客户端指定。
