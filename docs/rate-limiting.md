# 公开限流与反向代理边界

## 文档状态

- **已批准决策：** D-035。
- **范围：** public create/verify 的多实例限流、客户端网络归一化、代理信任和部署门禁。
- **明确不保证：** 限流与验证码都只是纵深防御，不能单独证明真人，也不能替代业务账户限流、MFA、风险策略或 DDoS 清洗。

## 请求顺序

事实与已批准顺序如下：

1. 路由、请求大小、media type、Content-Encoding、Origin/CORS 和标识词法前置检查。
2. 解析可信网络来源并生成不透明 `clientKey`。
3. Redis 原子判定客户端桶和站点桶。
4. 只有允许时调用 Core；verify 随后才可能原子取走 challenge。

`LIMITED` 返回 `429 RATE_LIMITED` 和 `Retry-After`，不调用 Core。`UNAVAILABLE` 返回 `503 DEPENDENCY_UNAVAILABLE`，不回退内存。Widget 不自动重试；调用方放弃当前交互并按服务端策略重新开始。

## 两级 token bucket

每个 operation 分别配置：

- client bucket：`siteKey + operation + clientKey`；
- site bucket：`siteKey + operation`。

策略使用 `burst` 与 `interval`：桶最多容纳 `burst` 次突发，长期每 `interval` 补充一次。Redis Lua 使用服务端 `TIME` 和 GCRA 等价的 theoretical arrival time，只在两个桶都允许时写入两个新值，并给 key 设置覆盖空闲桶生命周期的硬 TTL；配置必须保证该 TTL 不超过 24 小时。两个 key 共享 `{siteKey}` hash tag，为未来受控的 Redis Cluster 同 slot 执行保留边界；当前客户端兼容范围仍是 standalone。

限额是部署策略，不是协议常量。仓库示例仅用于启动和测试，生产必须根据生成 CPU、Redis 容量、NAT 用户密度、真实通过率和攻击基准校准。

## 网络身份与隐私

- `trustedProxyCidrs` 为空时完全忽略 `X-Forwarded-For`，只使用 TCP peer。
- peer 可信时，从右向左检查 XFF；连续剥离可信代理，首个非可信地址是客户端。多个 header 行按线序合并，空项、`unknown`、端口、zone ID、非规范 IPv4 或非 IP 字面量均拒绝。
- IPv4 保留 32 位；IPv6 清零低 64 位，以 `/64` 网络作为限流粒度。
- `clientKey = base64url(first16(HMAC-SHA-256(hmacKey, canonicalNetworkBytes)))`。
- `hmacKey` 必须是独立的 32 字节随机 secret，不复用 service credential。轮换会重置现有客户端桶；不得记录该 key、原始 IP 或可逆映射。

这不是设备指纹：不采集浏览器、Canvas、字体、硬件或跨站标识，也不把网络 key 暴露给 Widget、Core、日志或响应。

## 反向代理要求

公网部署必须由代理终止 TLS，并同时限制连接数、请求体、读取/发送超时和资源响应带宽。代理必须覆盖而不是追加来自公网的 `X-Forwarded-For`，其出站地址必须落入 Server 的显式可信 CIDR。若无法保证该链路，保持 `trustedProxyCidrs` 为空并让 Server 只看到直连地址。

资源 GET/HEAD 不进入内建 Redis 限流，避免每个图片请求增加控制面 Redis 往返；代理负责资源连接和带宽限制。trusted consume 不暴露浏览器 CORS，并应位于只允许业务后端访问的网络路径。
