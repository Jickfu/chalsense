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
11. [决策记录](docs/decisions/README.md)：区分已批准结论、工作假设和待决策事项。
12. [技术决策依据](docs/remaining-decisions.md)：了解 D-013～D-018 的选型依据、取舍及 D-014 的条件边界。
13. [路线图](docs/roadmap.md)：了解分期范围、完成标准和评估指标。
14. [阶段 0 评审](docs/stage-0-review.md)：了解当前证据、阻塞项和是否允许开始实现。

## 当前阶段的完成标准

- 核心概念和安全术语有稳定定义。
- 首版威胁模型、协议流程和模块边界可以接受评审。
- 关键决策有原因、有状态、有日期，不依赖聊天记录才能还原。
- 首版范围足够小，能够验证协议与 Core，而不是被管理后台、多语言 SDK 或大量玩法拖慢。

## 当前构建基线

根构件为 `io.github.chalsense:chalsense-parent:0.1.0-SNAPSHOT`，当前包含 `chalsense-protocol`、`chalsense-core` 与 `chalsense-store-redis`。生产代码以 Java 17 字节码发布，Core 与 Redis Store 均不依赖 Spring；测试在构建时直接执行冻结坐标、协议和状态序列化向量。

当前 Core 已实现 framework-independent 的 challenge 创建、单次验证、ticket 签发与单次消费状态机，以及可注入 `Clock`、CSPRNG token 生成器、`ChallengeGenerator`、`SiteRegistry`、`StateStore` SPI 和隐私最小化安全事件。创建流程只有在 challenge 原子落库已确认后才返回公开几何和两个资源引用；目标位置与容差不进入公开结果。Core 还提供无运行时依赖的严格状态 JSON codec，其逐字节 golden vectors 已由 D-027 冻结。

`chalsense-store-redis` 依 D-028～D-030 使用 Jedis 7.5.3、池化 `RedisClient`、二进制 `SET NX PXAT` / `GETDEL`，实现 Redis OSS 7.2.x 与 Valkey 7.2.x standalone 的单 key 原子存储、硬 TTL、故障结果映射和不可解码状态的失败关闭。可能在连接异常后自动重放命令的 `RedisClusterClient` 已延期，不属于当前兼容范围。测试用内存 Store 和确定性生成器只存在于测试源码；尚未创建 HTTP、Spring、Widget 或生产 challenge 图片生成实现。

Windows：

```text
.\mvnw.cmd clean verify
```

Linux/macOS：

```text
./mvnw clean verify
```

Maven Wrapper 固定 Maven 3.9.16，并校验官方分发包的 SHA-256。普通 Maven 构建执行全部纯 Java 测试；CI 另在 Java 17/21 上对 Redis 7.2.14 与 Valkey 7.2.14 执行同一组 Store 集成测试。生产图片生成、HTTP、Starter、Widget 与官网均不在当前实现范围内。
