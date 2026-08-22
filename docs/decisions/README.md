# 决策记录

## 使用规则

- `已批准`：可以作为后续设计和实现前提。
- `工作假设`：用于推进设计，但实施前可能需要确认。
- `待决策`：存在明显不同的实现结果，不能静默选择。
- `已否决`：保留原因，避免以后重复讨论。

决策发生变化时，不删除旧结论；修改状态、日期并记录替代原因。重大决策稳定后可以拆分为单独 ADR。

## 已批准

### D-001 项目独立维护

- 日期：2026-08-22
- 结论：在 `smart-manage` 之外建立独立 GitHub 项目 ChalSense，未来由 `smart-manage` 作为使用方接入。
- 原因：通用验证能力需要独立协议、发布、测试和安全生命周期。

### D-002 产品定位

- 日期：2026-08-22
- 结论：面向 JVM 与 Web 应用的自托管人机验证协议、服务端引擎与无框架前端组件。
- 原因：项目价值应集中在安全协议和通用能力，而不是成为某个登录页的滑块控件。

### D-003 首版交付形态

- 日期：2026-08-22
- 结论：Java Core 优先，同时保留薄服务层；服务层不得拖慢协议与 Core 的质量。
- 原因：Java 是首个真实使用生态；HTTP 服务为跨语言接入保留边界，但首版不建设完整平台。

### D-004 首批用户与国际化方向

- 日期：2026-08-22
- 结论：先服务国内 JVM 与 Web 项目，但协议、命名和文档结构从第一天支持英文与跨语言扩展。

### D-005 隐私默认值

- 日期：2026-08-22
- 结论：默认不采集设备指纹，只处理最小轨迹和请求上下文；未来指纹能力只能作为显式启用的可选插件。

### D-006 开源与素材治理

- 日期：2026-08-22
- 结论：使用 Apache-2.0 与 DCO；所有图片、字体和模板记录来源及许可证。

### D-007 首版验证形态

- 日期：2026-08-22
- 结论：首版以滑块拼图为主要交互，不加入文字点选。
- 原因：先解决协议、坐标、安全状态和误判问题，控制字体、OCR、国际化和无障碍复杂度。

### D-008 项目名称

- 日期：2026-08-22
- 结论：项目名称使用 `ChalSense`，含义为 `Challenge + Sense`。
- 备注：命名与发布坐标已由 D-019 补充；组织、scope、坐标和商标的实际可注册性仍须在正式公开前最终核验。

### D-009 当前阶段优先级

- 日期：2026-08-22
- 结论：当前优先沉淀上下文、威胁模型、协议、模块边界、路线图和完成标准，不立即开始编码。
- 状态：阶段性目标已完成。阶段 0 于 2026-08-22 通过评审，后续最小脚手架范围由 D-020 约束；本决策仍约束不得越过设计基线提前扩张实现。

### D-010 Java 版本基线

- 日期：2026-08-22
- 结论：ChalSense v1 的源码和字节码最低基线为 Java 17，CI 至少验证 Java 17 与 Java 21；独立服务推荐运行在 Java 21，但不得使用 Java 21 专属 API 破坏 Core 兼容性。
- 原因：首批用户是国内 JVM 项目，Java 17 覆盖面更广；当前 Core 不依赖 Java 21 专属能力，Java 17 构件也可以直接运行在 Java 21 上。

### D-011 票据消费拓扑

- 日期：2026-08-22
- 结论：票据只能通过 ChalSense Core 的统一消费语义处理，业务不得直接读写 ChalSense Redis 数据。嵌入式模式调用进程内 Java API，服务模式调用受信任 HTTP API，两者最终进入相同的 Core 状态机与 State Store SPI。
- 原因：统一校验站点、动作、上下文、协议版本、过期和一次性消费，同时隐藏 Redis Key、Lua 与序列化格式，避免各业务形成不兼容实现。

### D-012 首版 PoW 范围

- 日期：2026-08-22
- 结论：v0.1 只实现滑块拼图，不实现正式 PoW；Core 从首版提供验证类型扩展边界，但不提前公开未实现的 `POW` 协议类型。PoW 在 v0.2 完成设备性能、安全参数和无障碍评估后再实现。
- 原因：PoW 只能提高批量计算成本，不能证明用户是人，并且需要评估低性能设备、WebView、耗电、Worker 和内存不足。v0.1 由接入方提供 MFA、邮件或人工协助等替代路径。

### D-013 v0.1 协议与原子消费语义

- 日期：2026-08-22
- 结论：v0.1 使用显式 `protocolVersion = "1"`；请求严格解析并拒绝重复或未知字段，响应接收方忽略未知字段。验证和票据消费在调用面认证、请求大小、限流及标识检查后原子取走状态，再执行过期、绑定、结构和答案校验；取走后任何结果都不恢复。存储结果未知时失败关闭，并禁止以同一 challenge 或 ticket 透明重试。过期条件统一为 `now >= expiresAt`，不存在、过期和重放使用合并的不可用错误语义。
- 结论：`verificationTicket` 使用 32 字节 CSPRNG 随机状态型 bearer token，Redis 查找 key 使用 SHA-256 摘要；摘要输入的精确定义由 D-022 固定为解码后的 32 个原始字节。一次性消费始终由 Core 统一完成。
- 原因：阻止同一凭据反复试探和并发双消费，消除跨实现的过期与故障歧义，同时避免签名 token 在仍需防重放状态时额外引入算法与密钥轮换复杂度。

### D-014 整数坐标与容差原型基线

- 日期：2026-08-22
- 结论：线协议坐标统一使用 `1_000_000` 整数规范化单位，采用 ties away from zero 舍入、明确 clamp、真实按下点相对轨迹和 DPR/Canvas backing store 隔离规则。服务端按相同公式重算最终拼图片位置，客户端不能指定容差。
- 条件：`tolerance = clamp(round(pieceWidth * 8 / 100), 6250, 18750)` 仅批准为原型工作假设，不是最终安全参数。最终数值必须依据缩放、触控、误拒率和攻击通过率验证；调整只提升 `policyVersion`，不改变已批准坐标线协议。
- 原因：整数规范化坐标能够减少源图、CSS、DPR 和跨语言浮点边界差异；容差属于需要实测的体验和攻击成本参数，不能仅凭设计冻结。

### D-015 v0.1 资源交付方式

- 日期：2026-08-22
- 结论：`challenge.create` 返回两个短时、不透明资源 URL，分别交付背景二进制图和透明拼图片；资源生命周期不超过 challenge，同源部署可返回相对 URL。v0.1 不默认使用 Base64、永久素材 URL 或复杂组合图。
- 原因：降低 JSON 体积和内存复制，保持浏览器缓存、CSP、透明边界和坐标实现清晰；资源 URL 不被当作答案保护或安全秘密。

### D-016 Redis OSS / Valkey 兼容基线

- 日期：2026-08-22
- 结论：首版生产 State Store 支持 Redis OSS 7.2.x 与 Valkey 7.2.x，并只依赖 Redis OSS 7.2 核心命令子集；CI 对两者执行相同原子性和故障向量。简单单 key 取走优先 `GETDEL`，需要复合原子语义时只使用同一 hash slot 内的 Lua。
- 原因：7.2 已提供所需原子命令，具有较成熟兼容面；相比 6.2 有更合适的支持窗口，同时避免把首版绑定到 Redis 7.4/8 的许可证和分发差异。

### D-017 稳定状态序列化

- 日期：2026-08-22
- 结论：Redis value 使用 ChalSense 自有、版本化的 UTF-8 JSON 状态模型，`storageVersion` 与 `protocolVersion` 分离；只使用显式字段、安全整数和无填充 Base64url。禁止 Java 原生序列化、类名多态、第三方内部 Map、浮点和未版本化格式。
- 原因：保证跨语言可读、滚动升级可迁移、golden vector 可验证，并避免持久格式与 Java 类或第三方实现耦合。

### D-018 `SiteRegistry` 与 service credential

- 日期：2026-08-22
- 结论：Core 提供统一 `SiteRegistry` SPI。`siteKey` 是公开、随机、稳定的站点标识，注册项显式包含状态、允许 Origin、允许 action、挑战策略和服务端凭据。独立服务模式使用每站点、带 `keyId` 的 32 字节随机 service credential，允许新旧 key 短期重叠和显式吊销；浏览器不得持有。嵌入式模式使用进程内信任边界，不要求配置形式化 API secret。
- 原因：分离公开路由标识和秘密认证材料，同时让嵌入式与服务模式共享站点模型，并支持无停机轮换和最小权限审计。

### D-019 命名与发布坐标

- 日期：2026-08-22
- 状态：已批准；GitHub 组织与主仓库归属已由 D-031 修订，容器镜像路径已由 D-039 修订，其他命名和发布坐标保持有效。
- 结论：品牌展示统一使用 `ChalSense`，代码和普通标识统一使用小写 `chalsense`。GitHub 组织与主仓库目标原为 `chalsense/chalsense`；Java `groupId` 与包名前缀为 `io.github.chalsense`；Maven 构件命名为 `chalsense-core`、`chalsense-protocol`、`chalsense-spring-boot-starter` 和 `chalsense-server`；npm scope 为 `@chalsense`，前端组件为 `@chalsense/widget`；配置前缀为 `chalsense.*`。当前实际仓库和容器路径分别以 D-031、D-039 为准。
- 结论：官网与域名不进入当前功能实现范围；正式公开前仍须验证 GitHub 组织、npm scope、Maven Central namespace、容器路径和商标的实际可用性与所有权。
- 原因：统一品牌、包、构件和配置命名，减少接入歧义；将尚未注册的外部资源明确设为发布门禁，而不是假装已经占有。

### D-020 Maven、模块与版本基线

- 日期：2026-08-22
- 结论：使用 Maven 多模块工程，根父构件为 `io.github.chalsense:chalsense-parent`，首批只创建 `chalsense-protocol` 与 `chalsense-core`；其他构件等到出现真实职责和测试时再创建。所有 Java 构件同仓、统一版本，从 `0.1.0-SNAPSHOT` 开始；`@chalsense/widget` 将来可独立版本化，但必须显式声明协议兼容范围。
- 结论：仓库提交 Maven Wrapper，固定 Maven `3.9.16`；Java 编译使用 `--release 17`，并按 D-010 在 CI 验证 Java 17 与 Java 21。`chalsense-protocol` 不含生产依赖，`chalsense-core` 只依赖 `chalsense-protocol`，不得依赖 Spring。
- 依赖审查：构建插件使用 Apache-2.0 的 Maven Compiler Plugin `3.15.0`、Maven Surefire Plugin `3.5.5` 和 Maven Wrapper `3.3.4`；测试使用 EPL-2.0 的 JUnit `6.1.3`，以及 Apache-2.0 的 Jackson Databind `2.21.5` 读取机器测试向量。它们均处于活跃维护状态；JUnit/Jackson 仅为测试依赖，不进入发布构件的传递依赖。替代方案分别是 JDK 断言/自建测试执行器和自写 JSON 解析器，但会降低诊断质量或增加不必要的解析风险。
- CI 依赖审查：使用 MIT 许可、由 GitHub 官方活跃维护的 `actions/checkout` 与 `actions/setup-java`，固定到经签名发布对应的完整提交 SHA，并给予只读仓库权限。替代方案是手写 checkout/JDK 下载脚本或依赖 runner 预装环境，但会增加供应链脚本和环境漂移风险。
- 原因：先建立可验证的协议和 Core 边界，同时避免空模块、框架耦合和生产依赖泄漏；Wrapper 保证开发机与 CI 使用相同 Maven 版本。

### D-021 v0.1 协议词法约束与 Java 模型

- 日期：2026-08-22
- 结论：`challengeId` 固定为 16 字节 CSPRNG 随机值（22 字符无填充 Base64url）；`verificationTicket` 固定为 32 字节（43 字符无填充 Base64url）；`contextDigest` 固定为 32 字节（43 字符无填充 Base64url）。`siteKey` 使用 `^[A-Za-z0-9_-]{8,64}$`，`action` 使用 `^[a-z][a-z0-9._-]{0,63}$`。
- 结论：轨迹固定为 2～256 点，`t` 为 `0..30000` 且单调不递减，坐标绝对值不超过 `2_000_000`；首点必须为 `START` 且 `(x,y,t)=(0,0,0)`，末点必须为 `END`，中间点只能为 `MOVE`。
- 结论：`chalsense-protocol` 使用不可变 Java records/enums，不带 Jackson 或 HTTP 框架注解，不引入生产依赖。`0.x` 阶段的不兼容公开 API 调整只能随次版本升级并提供迁移说明；`1.0` 后遵守语义化版本兼容规则。HTTP URL 与具体状态码留到 `chalsense-server` 创建前冻结，Core 先使用稳定的逻辑 outcome/error code。
- 原因：固定长度与严格字符规则可以消除多实现解析歧义；128 位 challenge 随机空间在限流和短 TTL 下充分，bearer ticket 保留 256 位强度。框架无关模型保持 Core 和跨语言协议边界清晰。

### D-022 ticket 存储查找摘要输入

- 日期：2026-08-22
- 结论：先按 D-021 严格校验 43 字符无填充 Base64url `verificationTicket`，解码并确认得到 32 个原始字节，再计算 `SHA-256(rawTicketBytes)`；Redis 查找 key 使用摘要的小写十六进制表示。不得保存原始 ticket，不使用 HMAC，也不得改为对 43 个线协议字符计算摘要。
- 原因：ticket 的规范身份是 32 字节随机值；对原始字节摘要与现有跨实现向量一致，不依赖文本编码，并消除了 Java、HTTP 服务和其他语言实现生成不同存储 key 的歧义。ticket 具有 256 位随机熵，无需用服务端密钥抵抗离线枚举。

### D-023 v0.1 协议向量冻结

- 日期：2026-08-22
- 结论：将通过 Java Core runner 的状态机、故障、并发和解析向量冻结为 `docs/test-vectors/protocol-v1.json`，集合标识为 `chalsense-protocol-v1`。冻结前 draft SHA-256 为 `54bc2d19d888e972a549ba279e9d4978a622275002a3637865a58d65f29a4269`，首个冻结文件 SHA-256 为 `1ecdf006dbd26b0a1ce0ee1915c843465178b2d7fcb673513ffa7c9487a8c666`。
- 结论：已有向量的输入和 expected 不得改写；兼容增强只能追加向量。发现安全错误时必须保留证据、记录撤销原因并通过新的协议版本或明确替代决策修正，不得静默改变历史预期。
- 原因：为 Java Core、未来 HTTP 适配器和其他语言实现提供稳定的跨实现合约，同时让安全语义变化保持可审计。

### D-024 调用方与 Origin 模型

- 日期：2026-08-22
- 结论：Core 使用 sealed `CallerContext` 区分 `TrustedBackend` 与 `PublicBrowser(origin)`。前者只能由进程内受信任边界或完成 service credential 认证的适配器创建；后者用于公开浏览器 create/verify。调用方类型不是 JSON 字段，客户端不能自行声明 trusted。trusted 调用可跳过 Origin，但不得跳过站点状态和 action 策略。
- 结论：Origin 只接受绝对 `http`/`https` origin，禁止 userinfo、路径、query 和 fragment；scheme/host 小写，国际化域名以 ASCII punycode 配置，默认端口归一化为无端口，非默认端口保留，拒绝尾随点和非规范 IP 表示。`http` 默认只允许 loopback，并要求站点显式启用本地开发开关。`allowedOrigins` 对规范化后的 scheme/host/port 精确比较，不支持通配符。
- 结论：未知或禁用站点、不允许的 action 使用统一 `CALLER_UNAUTHORIZED`；Origin 不匹配使用 `ORIGIN_NOT_ALLOWED`。这些前置拒绝不得修改 challenge/ticket 状态。
- 原因：Origin 只是浏览器使用隔离而非身份认证；显式 caller 类型可以防止把浏览器输入误当作受信任调用，同时消除多适配器对默认端口、大小写和 URI 组件的比较差异。

### D-025 站点策略、TTL 与撤销时机

- 日期：2026-08-22
- 结论：每个站点显式配置 `challengeTtl`、`ticketTtl`、`policyVersion`、`allowedActions`、`allowedOrigins` 和 `allowInsecureLoopbackOrigins`。`challengeTtl` 默认 120 秒、允许 30～300 秒；`ticketTtl` 默认 60 秒、允许 10～120 秒。越界配置必须在构造或启动时失败，不静默修正。
- 结论：站点禁用后立即拒绝 create、verify 和 consume；已有状态不主动批量删除，等待 TTL 清理。verify 在原子 take 前检查站点状态和 Origin，take 后若 challenge 中的 action 已撤销则验证失败并烧毁 challenge。consume 因请求已包含 action，在 take 前检查站点状态和 action；拒绝时不消费 ticket。
- 结论：生产 State Store 必须以状态的 `expiresAt` 设置硬 TTL，Core 同时执行 `now >= expiresAt` 检查。
- 原因：短时状态同时受基础设施 TTL 和 Core 时钟保护；明确撤销时机既阻止未授权调用烧毁凭据，也让策略变化立即失败关闭。

### D-026 `challenge.create` 补充向量冻结

- 日期：2026-08-22
- 结论：将通过 Java Core runner 的 `challenge.create` 授权、成功、生成失败、原子写入故障、结果未知和 ID 冲突向量作为独立集合冻结为 `docs/test-vectors/challenge-create-v1.json`，集合标识为 `chalsense-challenge-create-v1`。冻结前草案 SHA-256 为 `e0106b7a1a0e4bb11bbb9460cdbf7287b3a80aae41ca88fd07db640fd0bc9db9`，首个冻结文件 SHA-256 为 `2d037c079d2def5e03985a66ae0e3c40be2924123a99d2da81b515188817d799`。
- 结论：D-023 的 `protocol-v1.json` 保持逐字节不变。create 集合固定以下跨实现语义：授权失败不得调用生成器或写状态；只有原子写入已确认才返回 challenge；结果未知不得重试或返回 `challengeId`；只有明确 `ALREADY_EXISTS` 才更换随机 ID，最多尝试 3 个 ID；公开几何不包含 `pieceTargetX` 或 `tolerance`。已有向量只能追加，不得改写输入或 expected。
- 边界：本决策不冻结 HTTP 路径/状态码、生产资源 URL 生成方式、图片算法或 Redis 具体实现。
- 原因：在不改变 D-023 历史哈希的前提下，为 Java Core、未来 HTTP 服务和其他语言实现建立可审计的创建状态机兼容合约，并覆盖最容易产生不一致凭据的写入不确定性。

### D-027 storageVersion 1 状态序列化冻结

- 日期：2026-08-22
- 结论：将通过 Java Core runner 与独立 Jackson 解析交叉验证的状态序列化向量冻结为 `docs/test-vectors/state-json-v1.json`，集合标识为 `chalsense-state-json-v1`。冻结前草案 SHA-256 为 `8af527c5be54044c58608a5781c9f9b7ab9b3bd95f94dd5350648ed643947125`，首个冻结文件 SHA-256 为 `919142f4f087edeb01a3a4e555909caa7cca7ed5690466f8e4fa4ca9ea7f1c87`。
- 结论：storageVersion 1 固定使用 `storageVersion`、`kind`、`protocolVersion`、`payload` envelope，固定 Challenge/Ticket payload 字段和 writer 顺序；输出为无 BOM、无格式化和无尾随换行的 UTF-8 JSON，单值上限 16 KiB。reader 不依赖字段顺序，但拒绝重复、未知或缺失字段、非法 UTF-8、错误类型/版本、浮点、指数、非 ASCII 数字与 JSON 安全整数越界。
- 结论：已有向量只能追加，不得改写输入、expected 或 golden bytes。同一 storageVersion 不得静默改变字段或语义；演进必须使用新 storageVersion，并遵循先 reader、后 writer 的滚动升级顺序。
- 边界：本决策不固定 Redis key 前缀、Redis 客户端库、连接配置、Lua 文本或部署拓扑。
- 原因：为 Redis OSS、Valkey、滚动升级和其他语言实现建立稳定、可审计且不依赖 Java 类序列化的持久化兼容面。

### D-028 Redis Java 客户端、模块与 key schema

- 日期：2026-08-22
- 状态：已批准；其中直接接受 `RedisClusterClient` 的范围已由 D-030 修订，其他结论保持有效。
- 结论：创建 `chalsense-store-redis`，首个生产实现固定使用 Jedis 7.5.3 及其池化 `RedisClient` / `RedisClusterClient`，不同时维护 Lettuce 或 Valkey-Java 适配器。模块依赖 `chalsense-core`，不依赖 Spring，也不复制状态 JSON codec。
- 结论：二进制 value 使用 D-027 冻结字节；key 固定为 `<namespace>:v1:challenge:<siteKey>:<challengeId>` 与 `<namespace>:v1:ticket:<ticketDigestHex>`。namespace 为 1～64 个 ASCII `[A-Za-z0-9._-]` 字符。单 key 写入使用 `SET key value NX PXAT expiresAt`，取走使用 `GETDEL key`；v0.1 不使用 Lua、事务、pipeline、客户端缓存或读副本。
- 结论：Redis/Valkey 命令明确响应映射为确认结果；Jedis 连接、timeout、Cluster 重试耗尽或响应丢失统一映射 `UNKNOWN`，不得透明重试。CI 在 Java 17/21 上对 Redis 7.2.14 与 Valkey 7.2.14 执行相同集成测试。
- 依赖审查：Jedis 为 MIT 许可并活跃维护，官方兼容表覆盖 Java 17/21 与 Redis 7.2；传递依赖及替代方案见 `docs/redis-store-design.md`。Lettuce 7.6.0 是主要替代，但当前同步短命令 Store 不需要引入 Netty/Reactor；Valkey-Java 暂不作为首个生产客户端。
- 原因：以一个同步池化客户端覆盖嵌入式和服务模式，并用完全相同的命令与测试验证 Redis OSS/Valkey 服务端兼容性，减少客户端差异和依赖冲突。

### D-029 原子取走后状态不可解码

- 日期：2026-08-22
- 结论：`TakeResult` 增加 `Unreadable<T>`，明确表示原始字节已由 Store 原子取走且不会恢复，但无法通过冻结状态 reader 构造可信状态。Verify/consume 对外统一返回 `DEPENDENCY_UNAVAILABLE`，内部只记录低基数 `STORE_STATE_UNREADABLE`；调用方不得以同一凭据重试。
- 结论：不得把该情况映射为表示“确定未取走”的 `Failed` 或表示“取走结果未知”的 `Unknown`，不得把损坏字节写回 Store，也不得改用非原子的 `GET` → Java 解码 → `DEL`。
- 原因：精确区分基础设施执行事实，既保持一次性消费边界，又让损坏、未知版本和迁移错误可以安全观测而不泄露状态内容。

### D-030 Redis Cluster 客户端入口延期

- 日期：2026-08-22
- 状态：已批准；部分修订 D-028。
- 新事实：Jedis 7.5.3 的 `ClusterCommandExecutor` 捕获 `JedisConnectionException` 后会在 `maxAttempts` 与总重试时限内重新执行命令。对 `GETDEL` 或 `SET NX PXAT` 而言，连接异常可能发生在服务端已经执行命令、客户端尚未收到响应之后，因此自动重放会把结果未知错误折叠成 `ABSENT`、冲突或其他不准确结果。
- 结论：v0.1 的 `JedisStateStore` 只公开接受池化 `RedisClient` 的构造方式，不直接接受调用方创建的 `RedisClusterClient`。不得以无法由 Store 验证的 `maxAttempts = 1` 使用约定替代安全保证。
- 结论：Redis Cluster 支持推迟到专用适配器能够保证连接结果不确定时不重放命令，并具备真实 Cluster 重定向、迁槽、响应丢失和故障切换测试之后。Redis OSS 7.2.x / Valkey 7.2.x standalone 支持、key schema、命令和其他 D-028 结论不变。
- 原因：一次性状态的重试边界必须由 ChalSense 实现强制保证，不能转嫁给接入方配置；宁可暂时缩小部署拓扑，也不能把已消费状态错误报告为未消费。

### D-031 GitHub 主仓库归属

- 日期：2026-08-22
- 状态：已批准；部分修订 D-019。
- 结论：当前不创建 GitHub 组织，主仓库使用个人账号下的公开仓库 `Jickfu/chalsense`。品牌名、Java/npm 坐标、配置前缀和未来容器镜像命名不因仓库归属而自动改变。
- 边界：若未来迁移到组织仓库，必须保留 GitHub redirect、更新发布来源与安全配置，并通过新的决策记录确认；当前不预先创建或占用 `chalsense` 组织。
- 原因：项目所有者明确选择直接使用个人账号公开托管，避免为当前实现阶段创建额外组织。

### D-032 Widget 工具链、公开边界与无障碍基线

- 日期：2026-08-22
- 状态：已批准。
- 结论：创建 npm workspace `packages/widget`，包名为 `@chalsense/widget`，首版版本 `0.1.0`，自定义元素名为 `<chalsense-widget>`。实现使用 TypeScript 和原生 Web Component、Shadow DOM、Canvas、Pointer Events，不使用 UI 框架、运行时依赖或生产 bundler。
- 结论：构建基线为 Node.js 24 LTS 与 npm lockfile；固定开发依赖 TypeScript 7.0.2、Vitest 4.1.11 和 Playwright 1.62.1。许可证分别为 Apache-2.0、MIT 和 Apache-2.0，只用于构建与测试。发布前仍须核验 `@chalsense` npm scope 所有权。
- 结论：Widget 通过接入方注入的 `ChalSenseTransport` 调用逻辑 `challenge.create` / `challenge.verify`，当前不固定 HTTP URL、认证、CORS 或错误状态码。任一 verify 只发送一次；超时、断网或不确定结果不得透明重试同一 challenge。
- 结论：公开成功事件只携带 `verificationTicket` 及服务端时间，不表示业务动作获准或用户已被证明为真人。客户端 challenge、资源、坐标、轨迹、时间戳、事件和回调继续视为不可信输入。
- 结论：默认不采集 pressure、倾角、设备指纹、持久设备标识或未批准浏览器遥测。轨迹只包含 D-014 允许的相对 `x`、`y`、`t` 与事件类型，并限制为 2～256 点、30 秒。
- 结论：首版提供简体中文和英文默认文案及覆盖接口，支持鼠标、触摸、笔和键盘操作；始终提供 `chalsense-alternative` 事件供接入方转入 MFA、邮件或人工协助。键盘可操作不等于视觉拼图对所有用户可访问，替代流程仍是接入要求。
- 结论：发布包以同源外部 `widget.css` 提供 Shadow DOM 样式，不依赖 inline style、`unsafe-inline` 或 `eval`；fixture 浏览器测试在 `default-src 'self'`、`style-src 'self'`、`script-src 'self'` 的 CSP 下运行。
- 原因：现在 Widget 已有真实渲染、交互、无障碍与跨浏览器测试职责，满足 D-020 的建模块条件；注入 transport 可以在 HTTP 服务尚未冻结时验证前端协议行为，避免把部署 URL 或框架耦合进 npm 公共 API。

### D-033 滑块生成器与短时资源边界

- 日期：2026-08-22
- 状态：已批准。
- 结论：v0.1 在 `chalsense-core` 提供只依赖 JDK 的生产滑块生成器、受控本地 `BackgroundImageSource` SPI 和 `ChallengeResourcePublisher` SPI；不联网抓取素材，不内置来源不明图库，不引入图片框架。首个编码基线为 `320 × 180` 背景 PNG 与 `50 × 50` 透明拼图片 PNG；D-015 允许以后在资源元数据不变的前提下增加 WebP 编码器。
- 结论：源图宽高、像素数、输出字节、生成并发和几何边距必须有硬上限。随机几何使用可注入 CSPRNG；确定性测试使用测试专用随机源和程序生成图片。目标位置、容差、`contextDigest` 与 ticket 不进入资源发布边界。
- 结论：两个资源必须整包发布或明确失败，资源有效期不得超过 challenge。资源在 TTL 内允许重复读取，不改变 verify 的一次性消费。Challenge State 未确认写入、ID 冲突或后续创建失败时执行 best-effort 清理；清理失败的孤儿资源也必须由 publisher 的硬 TTL 删除。
- 结论：Core 不提供会被误用为多实例生产存储的默认内存资源实现，也不冻结 HTTP 资源路径。具体文件系统、对象存储或 Redis 二进制实现由适配层显式装配，并遵守相同 TTL、容量和失败语义。
- 安全修订：资源清理必须接收本次 `publish` 返回的完整资源引用集合，不得只按 `challengeId` 推导删除目标。原因是挑战 ID 碰撞时，按 ID 清理可能误删既有挑战资源；publisher 生成的独立资源标识才是本次发布的精确所有权边界。
- 原因：用 JDK 能力先建立可测试、无框架的生成边界，同时避免把素材来源、HTTP 路径或特定对象存储耦合进 Core；整包发布、失败清理与硬 TTL 限制资源泄漏和半成功挑战。

### D-034 HTTP API v0.1 与独立服务实现线

- 日期：2026-08-22
- 状态：已批准。
- 结论：冻结 `docs/http-api.md` 的 `/v1/public` 与 `/v1/trusted` 分离路径、path 中的 `siteKey/challengeId`、严格请求 JSON、固定 body 上限、200/409/422 等状态码、精确无 credential CORS，以及 `Authorization: Bearer <keyId>.<secret>` service credential 承载。HTTP 适配层只能调用 Core，不复制状态机或接受客户端声明 trusted。
- 结论：`chalsense-server` 使用 Spring Boot 4.1.x Servlet 栈并固定 patch 版本，依赖不得进入 Protocol、Core、Redis Store 或 Widget。首个纵向切片不引入 Spring Security、Spring Data Redis、ORM、模板、管理后台或响应式栈；在内建公开限流完成前默认只监听 loopback，公网试用必须由反向代理提供 TLS、速率/并发限制和请求上限。
- 结论：Redis 短时资源使用独立 128 位 `resourceId`、单 hash key 与 Lua 原子完成不存在检查、两个资源和绝对 TTL 写入；读取不消费，结果未知不返回 URL，不回退内存。资源 ID 不复用 `challengeId`，清理只针对本次 publish 返回的资源集合。
- 原因：path `siteKey` 让 CORS preflight 可在不读取 body 时应用站点策略；分离授权面阻止调用方类型混淆；成熟 Servlet 容器减少自制 HTTP 边界；Redis 原子资源包让多实例读取与 challenge 生命周期一致。

### D-035 公开限流与代理网络身份

- 日期：2026-08-22
- 状态：已批准。
- 结论：v0.1 内建限流只覆盖 public create/verify，并在 Core 调用和 challenge take 前执行；trusted consume 继续由 service credential、网络隔离和反向代理保护。429 使用 `RATE_LIMITED` 与整数秒 `Retry-After`，不消费 challenge；Redis 判定结果未知时失败关闭为 503，不回退单机内存。
- 结论：每次请求必须由同一 Redis Lua 原子判定 `siteKey + operation + clientNetwork` 与 `siteKey + operation` 两个 token bucket，只有两者均允许才同时扣减。限额按站点配置，不把示例数值宣传为通用安全阈值。资源 GET/HEAD 的带宽、连接数和缓存保护仍由反向代理承担。
- 结论：默认只信任 TCP 直连地址。只有直连 peer 位于显式 `trustedProxyCidrs` 时才从右向左解析并剥离 `X-Forwarded-For` 中的可信代理；畸形或歧义输入前置拒绝。IPv4 使用完整地址，IPv6 归一到 `/64`，再使用部署方 32 字节 HMAC key 生成 128 位不透明 client key；Redis、日志和指标不得保存原始 IP，也不得与轨迹组合成设备指纹。
- 原因：双桶限制同时控制单一来源和站点总容量；原子判定避免部分扣减与多实例竞态；显式代理信任阻止伪造 forwarded header 绕过；HMAC 和短 TTL 在保留滥用控制能力的同时降低网络标识长期泄露风险。

### D-036 可观测性、审计与管理端口边界

- 日期：2026-08-22
- 状态：已批准。
- 结论：指标和审计只使用固定低基数 `operation`、`outcome`、`reason` 以及服务端生成的单请求 `requestId`。不得记录或标记 `siteKey`、action、Origin、IP/clientKey、challengeId、ticket、contextDigest、坐标、轨迹、答案、Authorization、请求体或资源 URL；默认不启用 tracing 或设备遥测。
- 结论：`chalsense-server` 使用 Spring Boot Actuator、Micrometer 和 Prometheus registry；依赖不得进入 Core。Prometheus 只通过默认监听 `127.0.0.1` 的独立 management 端口暴露，且只开放 `/actuator/prometheus`。`/livez` 仍只表示进程存活，`/readyz` 只返回 Redis 必要依赖的汇总状态。
- 结论：观测输出是非阻塞旁路；日志或指标失败不得改变、恢复或重试 challenge/ticket 状态操作。外部代理、APM 和日志平台的访问控制、脱敏与保留期仍是部署责任。
- 依赖审查：Actuator、Micrometer 与 Prometheus registry 由 Spring Boot 4.1.1 BOM 管理，许可证为 Apache-2.0 且处于活跃维护状态。替代方案是自建指标格式与 registry，但会增加并发、格式和生态兼容风险。
- 原因：提供容量、故障和攻击回归信号，同时阻止高基数标签、bearer 数据和行为轨迹进入长期观测系统；独立 loopback 管理端口避免把运维数据加入公开业务授权面。

### D-037 合成容量基准与阈值治理

- 日期：2026-08-22
- 状态：已批准。
- 结论：容量基准使用现有 JUnit、JDK HTTP client、真实 Redis/Valkey 和程序生成的自有图片，覆盖 create、资源读取、verify 与 trusted consume；不新增运行时依赖或把基准类打入发布构件。基准仅手动触发，不加入普通 push/PR 门禁。
- 结论：报告只包含运行环境、参数、吞吐、分位延迟与响应载荷，不包含任何站点、网络身份、challenge、ticket、答案、轨迹或请求体。测试专用状态读取必须位于计时区间之外，且不得形成生产接口。
- 结论：共享 runner 的单次结果不是 SLA，也不直接冻结默认限额或告警阈值。生产阈值必须在目标拓扑经稳定性、资源、故障和安全效果评估后另行批准。
- 原因：先建立可重复、隐私最小化的回归口径，同时避免把易波动的合成数字误当作普适容量或安全承诺。

### D-038 OCI 镜像与本地 Compose 基线

- 日期：2026-08-22
- 状态：已批准。
- 结论：`chalsense-server` 生成 Java 17 字节码的可执行 Spring Boot JAR；OCI 使用固定 patch tag 的 Eclipse Temurin 21 JDK 构建阶段与 JRE 运行阶段。最终进程固定为非 root `10001:10001`，支持只读根文件系统、`/tmp` tmpfs、移除全部 Linux capabilities 和 `no-new-privileges`。
- 结论：本地 Compose 默认只发布宿主 loopback 业务端口，Redis/Valkey 不发布端口，management 不发布到宿主；容器内非 loopback 监听必须同时启用 D-035 限流。背景素材只允许由部署方只读挂载，镜像不内置或下载生产素材。
- 结论：当前只提供本地构建与 Redis/Valkey 运行基线，不发布正式 GHCR 镜像，也不承诺多架构、SBOM、签名、provenance、Kubernetes 或生产 secret 交付；这些属于正式发布门禁。
- 依赖审查：没有新增 Maven/npm 依赖。基础镜像使用 Docker Official Image `eclipse-temurin`；OpenJDK 为 GPL-2.0 with Classpath Exception，镜像构建文件为 Apache-2.0，基础发行版组件仍需随升级审查。替代方案是 distroless、自建 jlink 或原生镜像，但会增加证书、诊断、兼容与维护成本。
- 原因：先验证最小可部署形态和运行时最小权限，同时避免把开发 Compose、已知 demo secret 或未签名镜像误称为生产交付物。

### D-039 GHCR 路径与容器供应链门禁

- 日期：2026-08-22
- 状态：已批准；修订 D-019 的容器镜像路径，并完成 D-038 的正式发布门禁设计。
- 结论：正式容器路径改为当前账号实际可用的 `ghcr.io/jickfu/chalsense-server`。只有格式为 `vMAJOR.MINOR.PATCH` 且与根 POM 非 SNAPSHOT 版本完全一致的标签可以发布；手动 workflow dispatch 只执行多架构构建和扫描，不发布。
- 结论：发布支持 `linux/amd64` 与 `linux/arm64`。两个架构分别通过 Trivy OS/library 扫描后才能发布；已有修复版本的 `HIGH`/`CRITICAL` 漏洞阻断发布，无修复漏洞进入人工风险评审，不得因扫描通过宣称无漏洞。
- 结论：发布以 digest 为身份，附带 BuildKit per-platform SPDX SBOM、max-mode provenance、CycloneDX SBOM、GitHub provenance/SBOM attestations，并使用 GitHub OIDC 执行 Sigstore keyless 签名和同工作流验证。不配置长期 registry 或签名私钥。
- CI 依赖审查：Docker 官方 Actions 为 Apache-2.0；GitHub attest/upload Actions 为 MIT；Sigstore cosign-installer 与 Aqua Security Trivy action 为 Apache-2.0。全部仅用于 CI、固定完整提交 SHA，并限制 job 权限。替代方案是手写 Buildx/Trivy/Cosign 下载校验脚本或托管签名密钥，但会增加供应链脚本、长期密钥托管和轮换风险。
- 原因：使用已控制的个人账号路径消除未占用组织阻塞；标签、扫描、成分、来源和无长期密钥签名形成可验证发布链，同时保留对上游漏洞与基础设施风险的诚实边界。

## 工作假设

### A-002 生产存储

- Redis 是首个生产状态实现；内存状态仅用于测试与本地演示。

### A-003 前端技术

- 使用 TypeScript、Web Component、Canvas 和 Pointer Events，框架适配包后置。

## 待决策

### Q-004 坐标与容差规范

需要通过原型和属性测试确认规范化坐标、模板几何和允许误差的最终算法。

- 状态：坐标线协议已由 D-014 解决；阶段 0 只要求小型原型证明换算、舍入、clamp 和跨显示环境的确定性。当前容差公式作为 v0.1 初始工作参数，不阻塞阶段 0。
- 后续门禁：最终经验性容差校准移至 v0.1 发布前，必须结合真实用户误拒率和攻击通过率；在此之前不得把当前数值宣传为经过验证的安全阈值。
- 影响：容差保存在 Challenge State 并由 `policyVersion` 标识，因此调整数值不改变已批准线协议。

## 已解决的待决策

### Q-005 资源交付方式

- 状态：已由 D-015 解决。
- 原问题：Base64、二进制响应、组合图或短时资源 URL 的取舍。

### Q-006 Redis 最低版本

- 状态：已由 D-016 解决。
- 原问题：Redis/兼容实现的最低版本与命令子集。

### Q-007 稳定序列化格式

- 状态：已由 D-017 解决。
- 原问题：Redis 中状态模型的稳定、跨语言和迁移格式。

### Q-008 `siteKey` 注册与密钥轮换

- 状态：已由 D-018 解决。
- 原问题：嵌入式与服务模式如何共享站点注册、服务端认证和轮换模型。

### Q-009 v0.1 协议兼容性语义

- 状态：已由 D-013 解决。
- 原问题：协议版本、JSON 演进、原子消费点、结果未知、过期和 ticket 形态。

### Q-001 票据消费拓扑

- 状态：已由 D-011 解决。
- 原问题：嵌入式模式与独立服务模式下，业务系统应通过 ChalSense API 消费票据，还是允许受信任的 Java SDK 直接访问同一 Redis。

### Q-002 首版无障碍替代流程

- 状态：已由 D-012 解决。
- 原问题：首版加入 PoW 非视觉流程，还是只定义扩展接口并由业务提供 MFA、邮件等替代路径。

### Q-003 Java 版本

- 状态：已由 D-010 解决。
- 原问题：Java 17 与 Java 21 的最低版本选择。

## 已否决

### R-001 首版建设完整管理平台

- 原因：会分散协议与 Core 的质量投入。

### R-002 首版提供十几种验证码和全语言 SDK

- 原因：缺少稳定协议前扩张生态会形成大量兼容负担。

### R-003 默认设备指纹

- 原因：不符合隐私最小化定位，也会引入持续攻防和合规成本。

### R-004 把轨迹阈值宣传为 AI 风控

- 原因：在没有真实数据、标注和评估前属于无法证实的安全主张。
