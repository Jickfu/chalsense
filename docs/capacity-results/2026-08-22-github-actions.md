# 2026-08-22 GitHub Actions 合成容量参考

## 运行事实

- 提交：`2b7af12e3cf23e99f97b21fc7c0f3b45d206771d`
- 运行：[Capacity benchmark #32554626688](https://github.com/Jickfu/chalsense/actions/runs/32554626688)
- 环境：GitHub 托管 Linux amd64 runner，Temurin Java 21.0.12；两个 ChalSense Server 实例与同 runner service container。
- 参数：20 次预热、200 次测量、4 个并发 flow。
- 结果：工作流的 Redis 7.2.14 与 Valkey 7.2.14 两段均成功，所有 create/resource/verify/consume 状态机断言通过。

| 后端 | flows/s | create p50/p95/p99 | resources p50/p95/p99 | verify p50/p95/p99 | consume p50/p95/p99 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Redis 7.2.14 | 75.315 | 24.480 / 40.893 / 51.020 ms | 9.487 / 17.584 / 24.362 ms | 8.426 / 17.669 / 20.547 ms | 6.112 / 11.813 / 14.764 ms |
| Valkey 7.2.14 | 73.771 | 24.699 / 39.196 / 46.039 ms | 9.310 / 16.721 / 19.571 ms | 8.806 / 17.445 / 25.310 ms | 6.942 / 12.910 / 16.731 ms |

响应 body 的 p50/p95/p99 在本次运行中为：create 596/596/596 bytes，verify 141/141/141 bytes，consume 90/90/90 bytes；两个 PNG 资源合计约 2.1 KiB，具体大小随程序生成图像而变化。原始 JSON 保留在该次 GitHub Actions 任务摘要中。

## 推论与限制

- **推论**：在本次短运行和共享 runner 噪声范围内，Redis 与 Valkey 没有表现出数量级差异；这不证明两者在生产拓扑等价。
- **限制**：测量窗口只有 2.656 秒和 2.711 秒，没有覆盖 JIT 长期稳定、GC、CPU、内存、Redis 命令率、网络延迟或故障恢复。
- **限制**：使用程序生成的小型测试图片，不代表生产素材的编码成本与带宽。
- **明确不保证**：本结果不是 SLA、部署规格、默认限额、安全通过率或真人证明，也不能替代真实用户误拒率和攻击通过率评估。

## 建议

下一轮在候选生产规格上运行至少 15 分钟，分档测试并发与图片尺寸，同时采集 CPU、RSS/heap、GC、Redis command rate/latency 和错误率；再依据目标 p95/p99 与故障余量提出限额和告警阈值。当前不冻结数值。
