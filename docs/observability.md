# 可观测性、审计与健康规范

## 文档状态

- **已批准决策：** D-036。
- **范围：** `chalsense-server` 的低基数指标、隐私安全审计事件、liveness/readiness 和 Prometheus 暴露边界。
- **安全前提：** 可观测数据属于受控运维数据，不是新的风险画像或设备指纹来源；观测失败不得改变 challenge、verify 或 ticket 的状态机结果。

## 数据最小化

允许进入指标标签的值只有固定枚举：

- `operation`：`challenge_create`、`challenge_verify`、`ticket_consume`、`resource_read`、`liveness`、`readiness`；
- `outcome`：固定 HTTP 结果类别，例如 `success`、`invalid_request`、`rejected`、`rate_limited`；
- `reason`：Core `SecurityReason` 的固定低基数集合。

禁止作为指标标签或审计字段记录：`siteKey`、`action`、Origin、IP、限流 `clientKey`、`challengeId`、原始或摘要 ticket、`contextDigest`、坐标、轨迹、答案、容差、请求体、Authorization、资源 URL 和素材内容。默认不采集 User-Agent、设备指纹或 tracing span。

服务端为受观测请求生成 128 位随机 `requestId`。它只用于同一次响应与审计事件关联，不接受客户端提供，也不得跨请求或跨站点复用。

## 指标

| 指标 | 类型 | 标签 | 语义 |
| --- | --- | --- | --- |
| `chalsense_requests_total` | Counter | `operation`, `outcome` | 已完成的协议、资源和健康请求数 |
| `chalsense_request_duration_seconds` | Timer | `operation`, `outcome` | Server 适配层请求耗时 |
| `chalsense_security_events_total` | Counter | `operation`, `reason` | Core 产生的低基数内部安全事件 |

Prometheus registry 默认拒绝不以 `chalsense.` 开头的自动指标，避免框架 HTTP URI、进程或第三方依赖在未经评审时扩大标签和暴露面。指标用于容量、错误率和攻击回归判断，不证明用户是真人，也不能替代业务账户限流、MFA 或风险策略。任何新增指标或标签必须先证明值域有硬上限且不含用户、网络或 bearer 标识。

## 结构化审计事件

Server 为上述受观测请求写一行 JSON 事件：

```json
{"event":"chalsense_request","requestId":"AAAAAAAAAAAAAAAAAAAAAA","operation":"challenge_verify","outcome":"rejected","reason":"answer_mismatch","status":422}
```

字段均来自服务端随机值、固定枚举或 HTTP 状态。Core 未产生内部原因时使用 `reason = "none"`。日志 sink 故障、格式化故障或指标 registry 故障不得回滚、重试或改变安全状态操作；运维平台负责访问控制、保留期、完整性与告警。

反向代理和外部 APM 必须另行配置请求体、Authorization 和动态路径脱敏。ChalSense 默认关闭 tracing，避免自动把路径中的 `challengeId` 或 header 发送给第三方。

## 健康与暴露边界

- `GET /livez` 只表示业务进程可以响应，不访问 Redis。
- `GET /readyz` 通过 Redis 主连接执行 `PING`；失败统一返回 `503 {"status":"DOWN"}`，不暴露 URI、凭据、节点或异常。
- Prometheus 使用独立 management 端口，默认 `127.0.0.1:9090`，只暴露 `/actuator/prometheus`。
- management 端口不得公网暴露；生产由本机采集器、受控监控网络或完成认证的反向代理访问。
- Actuator 的 health、info 和其他端点不对外暴露；业务端口继续只保留冻结的 `/livez`、`/readyz` 语义。

## 残余风险与验证

- 即使不记录敏感标识，请求时间和低基数结果仍可能显示业务活动趋势，运维系统仍需最小权限和保留期。
- 外部代理、容器平台或 APM 可能在 ChalSense 之前捕获动态 URL、header 或 body；部署方必须独立审计这些默认值。
- `requestId` 可被获得日志访问权的主体用于单请求关联，因此不能被解释为匿名用户 ID。
- 自动化测试必须断言标签集合固定、审计事件不含敏感 fixture，并验证 Prometheus 只在独立 loopback management 端口暴露。
