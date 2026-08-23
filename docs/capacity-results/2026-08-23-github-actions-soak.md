# 2026-08-23 GitHub Actions Redis 15 分钟稳定性参考

## 运行身份

- 提交：`682090111119b76b35bfe72c28196cc582d6c008`
- 工作流：[Capacity benchmark / 32632706845](https://github.com/Jickfu/chalsense/actions/runs/32632706845)
- 目标：`github-actions-redis-7.2.14`
- 环境：GitHub 托管 `Linux amd64` runner、Temurin Java `21.0.12`、Redis OSS `7.2.14`
- 输入：warmup `20`、concurrency `4`、duration `900` 秒、backend `redis`
- 审计输出：`chalsense.audit.level=WARN`；Micrometer 安全事件路径保留，但不计生产日志 sink I/O。

## 机器结果

| 指标 | 结果 |
| --- | ---: |
| 实际 flow | 259,264 |
| 测量时长 | 900.718 s |
| 吞吐 | 287.841 flow/s |
| 进程平均 CPU | 2.733 cores |
| JVM heap 采样峰值 | 349,796,072 bytes |
| Redis 服务路径估算命令 | 5,704,339 |
| Redis 命令/flow | 22.002 |
| Redis 服务命令/秒 | 6,333.099 |
| Redis 内存起始/峰值/结束 | 1,287,808 / 109,325,296 / 105,286,776 bytes |
| 资源监控错误 | 0 |

延迟单位为微秒：

| 阶段 | p50 | p95 | p99 | max |
| --- | ---: | ---: | ---: | ---: |
| create | 8,570 | 11,980 | 15,073 | 50,352 |
| 两个 resources | 1,549 | 3,775 | 5,338 | 24,206 |
| verify | 1,473 | 3,452 | 4,934 | 26,097 |
| consume | 950 | 2,728 | 3,991 | 22,542 |

载荷分布：create 固定 596 bytes；两个资源合计 p50/p95/p99/max 为 2,081/2,182/2,201/2,221 bytes；verify 固定 141 bytes；consume 固定 90 bytes。

## 结论分类

- **观察事实**：900 秒持续运行没有 HTTP/状态机断言失败，资源监控 `monitorErrors=0`，工作流成功结束。
- **观察事实**：估算 Redis 命令稳定在约 22 条/flow；该数字已扣除 905 条观测命令和每 flow 一次测试夹具 state GET。
- **推论**：该共享 runner 拓扑能在本次参数下持续执行完整纵向流程，可作为以后同口径回归参考。
- **不能推论**：Redis 结束内存约 100 MiB 主要包含持续负载结束瞬间仍在 TTL 内的 challenge/resource/限流工作集；本次没有停载后等待 TTL 排空，因此不能据此证明或否定内存泄漏。
- **明确不保证**：本结果不是 SLA、生产规格、默认限额、安全通过率或真人证明；不覆盖真实网络、生产日志 sink、Valkey 长时运行、故障注入、水平扩容、停载排空或真实用户流量。

## 后续门禁

1. 在目标部署拓扑重复运行，并记录容器/主机级 CPU、RSS、网络和 Redis latency。
2. 增加停载后至少一个 challenge TTL 的排空观测，比较 Redis 与 heap 的稳定基线；在完成前不宣称无泄漏。
3. 分别执行 Valkey 长时运行、Redis 网络延迟/中断、实例重启和日志 sink 开启条件。
4. 默认限额仍需结合 D-040 真实用户误拒和独立攻击通过率评审，不直接复制本次吞吐数字。
