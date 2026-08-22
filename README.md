# ChalSense

ChalSense 是一个面向 JVM 与 Web 应用的自托管人机验证项目，目标是提供协议安全、隐私友好、可扩展的验证核心、无框架前端组件和薄服务层。

当前公开主仓库：[Jickfu/chalsense](https://github.com/Jickfu/chalsense)。

项目已于 2026-08-22 通过阶段 0 设计基线评审，现已进入最小协议、Core 与 Redis State Store 实现阶段，尚未开始完整 v0.1 业务实现。威胁模型、协议、安全边界、模块职责、坐标规范和关键技术决策已经形成可追溯基线；当前只实现可验证的协议、Core 与生产状态存储基础，不提前扩张验证码玩法、框架、官网或管理能力。

## 当前定位

> Self-hosted human verification for modern applications.

- Java Core 优先，首版同时提供不拖累 Core 质量的薄服务层。
- 首个生态是国内 JVM 与 Web 项目，但协议、命名和文档结构从第一天考虑英文与跨语言使用。
- 默认不采集设备指纹，只处理完成验证所必需的最小轨迹和请求上下文。
- 首版以滑块拼图切入，但项目不绑定滑块、Spring 或图片 CAPTCHA。
- Apache-2.0 开源，使用 DCO；图片、字体和模板必须记录合法来源与许可证。

## 新对话阅读顺序

1. [项目上下文](docs/project-context.md)：了解前因后果、原项目实践和为什么创建 ChalSense。
2. [产品定位与边界](docs/product-scope.md)：了解目标用户、价值主张、目标与非目标。
3. [调研结论](docs/research/landscape.md)：了解开源实现、云服务及参考资料。
4. [架构方向](docs/architecture.md)：了解模块、安全边界和技术选择。
5. [威胁模型](docs/threat-model.md)：了解资产、信任边界、攻击能力、控制和残余风险。
6. [协议与状态机](docs/protocol.md)：了解 challenge、verify、verificationTicket 的线协议和故障语义。
7. [协议测试向量](docs/protocol-test-vectors.md)：了解跨实现一致行为及机器向量的使用方式。
8. [坐标与交互规范](docs/coordinates.md)：了解缩放、Canvas、CSS、DPR、Pointer Events、轨迹和容差。
9. [状态存储与稳定序列化](docs/state-storage.md)：了解状态 JSON、版本演进、原子 Store 与结果未知语义。
10. [Redis / Valkey Store 设计](docs/redis-store-design.md)：了解客户端依赖、key、命令、TTL、故障映射与集成测试拓扑。
11. [Widget 设计与测试](docs/widget.md)：了解 Web Component API、transport、渲染、交互、无障碍和浏览器测试边界。
12. [滑块生成器与短时资源](docs/slider-generator.md)：了解素材、图片生成、资源发布、上限和清理边界。
13. [HTTP API v0.1](docs/http-api.md)：了解已批准的路径、认证、错误、CORS、资源和 Server 实现线。
14. [公开限流与反向代理](docs/rate-limiting.md)：了解双桶、代理信任、网络标识隐私和失败策略。
15. [可观测性、审计与健康](docs/observability.md)：了解低基数指标、隐私安全日志和管理端口边界。
16. [决策记录](docs/decisions/README.md)：区分已批准结论、工作假设和待决策事项。
17. [技术决策依据](docs/remaining-decisions.md)：了解 D-013～D-018 的选型依据、取舍及 D-014 的条件边界。
18. [路线图](docs/roadmap.md)：了解分期范围、完成标准和评估指标。
19. [阶段 0 评审](docs/stage-0-review.md)：了解当前证据、阻塞项和是否允许开始实现。

## 当前阶段的完成标准

- 核心概念和安全术语有稳定定义。
- 首版威胁模型、协议流程和模块边界可以接受评审。
- 关键决策有原因、有状态、有日期，不依赖聊天记录才能还原。
- 首版范围足够小，能够验证协议与 Core，而不是被管理后台、多语言 SDK 或大量玩法拖慢。

## 当前构建基线

Java 根构件为 `io.github.chalsense:chalsense-parent:0.1.0-SNAPSHOT`，当前包含 `chalsense-protocol`、`chalsense-core`、`chalsense-store-redis` 与薄 `chalsense-server`。生产代码以 Java 17 字节码发布，Core 与 Redis Store 均不依赖 Spring；测试在构建时直接执行冻结坐标、协议和状态序列化向量。npm workspace 当前包含无运行时依赖的 `@chalsense/widget`。

当前 Core 已实现 framework-independent 的 challenge 创建、单次验证、ticket 签发与单次消费状态机，以及可注入 `Clock`、CSPRNG token 生成器、`ChallengeGenerator`、`SiteRegistry`、`StateStore` SPI 和隐私最小化安全事件。D-033 增加了只依赖 JDK 的生产滑块 PNG 生成器、受控背景来源 SPI、短时资源发布 SPI、硬资源上限和失败清理；仓库仍不内置来源不明的生产素材或多实例资源存储。创建流程只有在 challenge 原子落库已确认后才返回公开几何和两个资源引用；目标位置与容差不进入公开结果。Core 还提供无运行时依赖的严格状态 JSON codec，其逐字节 golden vectors 已由 D-027 冻结。

`chalsense-store-redis` 依 D-028～D-030、D-034～D-035 使用 Jedis 7.5.3 和池化 `RedisClient`，实现 Redis OSS 7.2.x 与 Valkey 7.2.x standalone 的状态原子消费、短时资源和公开双桶限流。状态与限流结果未知均失败关闭，不回退内存；可能自动重放命令的 `RedisClusterClient` 已延期。

`@chalsense/widget` 依 D-032 实现原生 `<chalsense-widget>`、Canvas/Pointer Events、键盘控制、双语文案、替代验证事件和可注入 transport；D-034 增加官方 `createHttpTransport`。`chalsense-server` 固定 Spring Boot 4.1.1，提供 public/trusted 分离端点、静态站点与 credential 配置、精确 CORS、请求上限、Redis 短时资源、D-035 公开限流，以及 D-036 低基数指标、结构化审计和独立 loopback Prometheus 端口。成功事件仍只表示取得 ticket；生产部署边界见 [Server 说明](chalsense-server/README.md)。

Windows：

```text
.\mvnw.cmd clean verify
```

Linux/macOS：

```text
./mvnw clean verify
```

Widget（Node.js 24 LTS）：

```text
npm ci
npm run verify:widget
```

Maven Wrapper 固定 Maven 3.9.16，并校验官方分发包的 SHA-256。普通 Maven 构建执行 Java 与 Server 边界测试；Widget 使用 npm lockfile、TypeScript、Vitest 与 Playwright。CI 另在 Java 17/21 上对 Redis 7.2.14 与 Valkey 7.2.14 执行同一组 Store 集成测试，并在 Node.js 24/Chromium 上验证 Widget。官网、Starter、容器发布和动态管理能力尚不在当前实现范围内。
