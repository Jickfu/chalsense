# 容量测试与限额校准

本文定义 `chalsense-server` v0.1 的可重复合成容量测试方法。它用于发现回归和形成部署校准起点，不构成生产 SLA、安全通过率或真人证明。

## 结论分类

- **事实**：基准通过两个本地 Server 实例、真实 Redis/Valkey、JDK `HttpClient` 和程序生成的仓库自有测试图片，执行 create、两个资源读取、verify 与 trusted consume 完整流程。
- **事实**：客户端输入、坐标、轨迹、时间戳和回调仍不可信；基准为了构造可成功的合成请求，会在计时区间之外直接读取隔离测试 keyspace 中的 challenge state。生产代码和接入方不得使用这条路径。
- **事实**：报告只含运行目标、Java/OS、并发量、吞吐、分位延迟和载荷字节数，不含 `siteKey`、IP、challenge、ticket、轨迹、答案或请求体。
- **推论**：GitHub 托管 runner 的邻居负载和虚拟机规格会波动，单次结果适合比较同拓扑回归，不足以推导生产容量。
- **建议**：生产限额与告警应在目标机器、目标 Redis 网络拓扑、真实图片规格和预期并发下重新校准，并保留故障与突发余量。
- **工作假设**：首轮参考运行使用 Java 21、Redis OSS 7.2.14、Valkey 7.2.14、20 次预热、200 次测量和 4 个并发流程；这些参数不是冻结的协议或安全阈值。

## 测量口径

一次 flow 依次包含：

1. `challenge_create`；
2. 两个 challenge resource 的顺序 GET（合并记录为 `resources`）；
3. `challenge_verify`；
4. `ticket_consume`。

`flowsPerSecond` 从全部测量 flow 的共同起止时间计算。每个阶段报告 nearest-rank `p50`、`p95`、`p99` 与 `max`；延迟单位是微秒。载荷分布使用同样算法，单位是响应 body 字节。预热样本不进入报告。任一状态码或状态机断言失败都会让任务失败；数值本身暂不设置跨环境硬门槛。

基准保留真实的图片生成、Redis 状态操作、限流和隐私安全审计开销。基准模式只扩大隔离测试站点的 token bucket，避免校准工具被普通集成测试的示例限额截断；普通测试仍验证 429 和 `Retry-After`。

## 执行方式

GitHub Actions 的 `Capacity benchmark` 仅支持手动触发，不加入每次 push/PR。默认依次运行 Redis 与 Valkey，结果写入任务摘要。调整输入时必须记录 warmup、iterations、concurrency、提交 SHA、运行链接及后端版本。

具有专用 Redis/Valkey 的本地环境可运行：

```text
./mvnw -pl chalsense-server -am \
  -Dchalsense.server.integration=true \
  -Dchalsense.capacity.benchmark=true \
  -Dchalsense.capacity.target=local-redis \
  -Dchalsense.capacity.warmup=20 \
  -Dchalsense.capacity.iterations=200 \
  -Dchalsense.capacity.concurrency=4 \
  -Dchalsense.redis.port=6379 test
```

机器报告位于 `chalsense-server/target/capacity-report.json`。只能连接专用测试实例，因为测试会创建并在结束时清理自己的随机 namespace。

带提交与运行环境限定的参考结果存放在 [`capacity-results/`](capacity-results/)；当前首份记录为 [2026-08-22 GitHub Actions 合成容量参考](capacity-results/2026-08-22-github-actions.md)。不得脱离其限制说明引用数字。

## 校准与评审门禁

部署校准至少要覆盖预期稳态、预期峰值、Redis 网络延迟和单实例故障后的剩余容量。建议从满足目标 p95/p99 的持续吞吐中保守取值，再分别设置 client bucket、site bucket、反向代理连接/带宽限制和告警；不能直接复制本仓库示例值或一次共享 runner 结果。

在冻结任何默认限额或告警阈值前，还需要至少 15 分钟稳定性运行、CPU/内存与 Redis 命令观测、故障注入，以及真实用户误拒率和已知攻击通过率数据。容量基准不采集设备指纹，也不把轨迹转存为性能样本。
