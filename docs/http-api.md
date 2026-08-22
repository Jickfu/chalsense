# HTTP API v0.1

## 文档状态

- **状态：已由 D-034 批准。** 本文是 v0.1 HTTP 兼容性基线；实现仍处于 `0.x` 开发阶段，不代表已经发布稳定版。
- **已批准决策：** Core 状态机与字段语义，以及本文的 HTTP 路径、JSON 映射、状态码、认证承载和 CORS，均已冻结为 v0.1 兼容性基线。
- **安全前提：** 浏览器、Origin、坐标、轨迹、时间戳、资源请求和成功回调均不可信。HTTP 服务只适配 Core，不复制验证状态机，也不把验证成功解释为“用户一定是真人”。

## 推荐的部署与技术栈

建议创建 `chalsense-server`，使用 Spring Boot 4.1.x Servlet 栈并在仓库中固定一个 patch 版本。2026-08-22 核对的官方资料显示 Spring Boot 当前 4.1 线最低要求 Java 17，并提供嵌入式 Servlet 容器、外部化配置和健康检查能力：

- [Spring Boot System Requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot 项目页](https://spring.io/projects/spring-boot)

Spring Boot 与 Spring Framework 为 Apache-2.0；嵌入式 Tomcat 为 Apache-2.0。依赖只进入 `chalsense-server`，不得传递到 `chalsense-core`、`chalsense-protocol` 或 Widget。首个纵向切片不引入 Spring Security、Spring Data Redis、ORM、模板引擎、管理后台或响应式栈。

不建议以 JDK `HttpServer` 作为生产边界：它适合测试夹具，但请求限制、服务器生命周期、错误处理、代理集成和运维基线都需要自行补齐。Spring Boot 3.5.x 是可行兼容替代，但新项目若没有既有 Spring 3 约束，直接建立当前 4.1 线可减少紧接着迁移 Jakarta/框架主版本的成本。

## API 根路径与授权面

所有协议端点固定在 `/v1`。公开浏览器面与受信任业务面使用不同路径，避免一个控制器根据可伪造字段切换调用方类型。

| 方法与路径 | 调用方 | 作用 |
| --- | --- | --- |
| `POST /v1/public/sites/{siteKey}/challenges` | 不可信浏览器 | 创建 challenge |
| `POST /v1/public/sites/{siteKey}/challenges/{challengeId}/verify` | 不可信浏览器 | 单次验证 challenge |
| `GET /v1/public/resources/{resourceId}/{role}` | 浏览器 | 读取短时背景或拼图片 |
| `HEAD /v1/public/resources/{resourceId}/{role}` | 浏览器 | 获取同一资源元数据 |
| `POST /v1/trusted/sites/{siteKey}/verification-tickets/consume` | 已认证业务后端 | 单次消费 ticket |
| `GET /livez` | 运维探针 | 仅表示进程可响应，不探测 Redis |
| `GET /readyz` | 运维探针 | 检查必要配置与 Redis 主连接可用性，不暴露内部详情 |

`siteKey` 和 `challengeId` 只出现在路径，不在 JSON body 重复。这样 CORS preflight 可以在不读取请求体时按 `siteKey` 选择精确 Origin 策略，也不存在路径值与 body 值不一致的第二种实现结果。

v0.1 不在同一端点混合公开与受信任 create/verify。若未来业务后端需要代建 challenge，应新增明确的 trusted 路径并进入相同 Core 命令，而不是让客户端提交 `trusted=true`。

## JSON 请求与成功响应

### 创建

```json
{
  "protocolVersion": "1",
  "action": "login",
  "contextDigest": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
}
```

成功使用 `200 OK`，响应沿用 `docs/protocol.md` 的 `CreatedChallenge` 字段。由于 API 不提供 challenge 的 GET 或稳定资源位置，不使用暗示可解引用 `Location` 的 `201 Created`。

### 验证

```json
{
  "protocolVersion": "1",
  "solution": {
    "finalPieceX": 593750,
    "track": [
      {"x": 0, "y": 0, "t": 0, "event": "START"},
      {"x": 531250, "y": 0, "t": 420, "event": "END"}
    ]
  }
}
```

成功使用 `200 OK`，只返回 `protocolVersion`、`verificationTicket`、`issuedAt` 和 `expiresAt`。

### 消费

```json
{
  "protocolVersion": "1",
  "verificationTicket": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  "action": "login",
  "contextDigest": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
}
```

成功使用 `200 OK`，只返回 `protocolVersion`、`valid: true`、`verifiedAt` 和 `consumedAt`。最终业务授权仍由调用方结合限流、MFA 和业务风险决定。

请求严格拒绝重复、未知和缺失字段；响应接收方必须忽略未知字段。字段词法、整数范围、轨迹点数与 Base64url 规则完全复用 Core，不在控制器维护第二套宽松校验。

## Content-Type、大小和编码

- JSON 只接受 `Content-Type: application/json`，可带且只可带 `charset=utf-8`；响应为 `application/json;charset=UTF-8`。
- v0.1 拒绝带 `Content-Encoding` 的请求，避免压缩炸弹与代理解压语义分歧。
- create body 上限 2 KiB，verify 64 KiB，consume 4 KiB；超限在 JSON 解析和状态 take 前返回 `413`。
- 资源响应背景上限 1 MiB、拼图片上限 256 KiB，并设置准确 `Content-Length` 和 `Content-Type`。
- 非 JSON media type 返回 `415`；畸形 UTF-8、非法 JSON、未知协议版本或词法错误返回 `400 INVALID_REQUEST`。
- 不接受客户端传入的 `requestId`。服务端为每个响应生成 16 字节随机、22 字符无填充 Base64url request ID；日志与响应只使用该低敏感关联值。

## 错误映射

错误 body 固定为：

```json
{
  "protocolVersion": "1",
  "error": {
    "code": "CHALLENGE_UNAVAILABLE",
    "requestId": "AAAAAAAAAAAAAAAAAAAAAA"
  }
}
```

| HTTP | 公开 `code` | 语义 |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | 请求无法严格解析；若已进入 Core take，则不得以同一凭据重试 |
| 401 | `CALLER_UNAUTHORIZED` | 仅受信任端点缺失或无效 credential；带 `WWW-Authenticate: Bearer` |
| 403 | `CALLER_UNAUTHORIZED` / `ORIGIN_NOT_ALLOWED` | 未知/禁用站点、不允许 action 或公开 Origin 前置拒绝 |
| 409 | `CHALLENGE_UNAVAILABLE` / `TICKET_UNAVAILABLE` | 不区分不存在、过期、已消费或重放；原凭据不得重试 |
| 413 | `INVALID_REQUEST` | 请求体超限，发生在 Core 调用前 |
| 415 | `INVALID_REQUEST` | media type 不受支持，发生在 Core 调用前 |
| 422 | `VERIFICATION_FAILED` / `TICKET_INVALID` | 状态已经消费，原凭据不得重试 |
| 429 | `RATE_LIMITED` | 预留给公开限流；必须创建新 challenge |
| 503 | `DEPENDENCY_UNAVAILABLE` | 失败关闭；verify/consume 不得重试同一凭据 |

未匹配路由与过期/不存在资源使用不带内部细节的 `404`。任何错误都不返回答案距离、容差、轨迹原因、绑定差异、Redis 异常、堆栈或原请求 body。`Cache-Control: no-store` 用于全部 JSON；错误响应不根据不存在、过期或重放改变状态码或耗时策略。

## CORS 与 Origin

- 公开 create/verify 必须有可规范化的 `Origin`，并与路径 `siteKey` 的 `allowedOrigins` 精确匹配；缺失或不匹配均不进入 Core take。
- preflight 只允许 `POST`、`Content-Type`，不允许 credential；`Access-Control-Allow-Origin` 只回显已注册的精确 Origin，不使用 `*`。
- 返回 `Vary: Origin, Access-Control-Request-Method, Access-Control-Request-Headers`，preflight `max-age` 最多 300 秒。
- trusted consume 不返回任何 CORS 许可，并拒绝带 `Origin` 的浏览器式调用。
- Origin 只提供浏览器隔离，不是客户端身份认证；非浏览器攻击者可以伪造，因此仍需网络层限流和业务纵深防御。

## Service credential

trusted consume 使用：

```text
Authorization: Bearer <keyId>.<secret>
```

- `keyId` 使用 8～64 个 ASCII `[A-Za-z0-9_-]`；`secret` 是 32 字节随机值的 43 字符无填充 Base64url。
- 点号是唯一分隔符，两个部分都必须使用规范编码；不接受 query、Cookie、JSON body 或浏览器存储中的 credential。
- registry 保存 `SHA-256(rawSecretBytes)`、状态与生效/失效时间，不保存可用明文；比较采用常量时间。
- credential 必须属于路径中的 `siteKey`。认证成功后适配器才能创建 `CallerContext.TrustedBackend`；客户端 JSON 不能选择调用方类型。
- 允许 D-018 已批准的新旧 key 短期重叠和显式吊销。日志只记录 `keyId`、`siteKey` 和低基数结果，不记录 Authorization。

## Redis 短时资源

建议在 `chalsense-store-redis` 实现 D-033 publisher/reader，而不是把二进制生命周期写进控制器：

- 每个 challenge 生成独立的 16 字节 `resourceId`，不复用 `challengeId`。
- 单个 Redis hash key 保存 background、piece 和最小资源元数据；key 使用独立 namespace 与 resource schema version。
- 同一个 Lua 操作完成“key 不存在检查、两个二进制字段写入、绝对 `PEXPIREAT`”，只返回确认、冲突、失败或结果未知；结果未知不得返回 URL。
- GET/HEAD 使用 `HGET`/必要元数据读取，不消费资源；删除只接收本次 publish 返回 URL 解析出的同一 `resourceId`，不得按 challenge ID 删除。
- 资源 TTL 不晚于 challenge，读取时仍以服务端时间检查 `expiresAt`；Redis 或读取错误返回统一资源不可用，不回退内存或永久文件。
- 资源响应设置 `X-Content-Type-Options: nosniff`、`Referrer-Policy: no-referrer`、`Cross-Origin-Resource-Policy: cross-origin` 和剩余 TTL 内的 `Cache-Control: private`。

Redis 资源会增加内存与网络消耗，因此必须单独记录资源字节、发布失败、读取失败和过期清理指标。未来对象存储适配器可替换该实现，但 URL 和 TTL 语义保持一致。

## 服务器暴露与当前限制

在公开限流 SPI 尚未完成前，Server 默认监听 `127.0.0.1`，不得宣传为可直接裸露公网。公网试用必须经过配置了 TLS、请求速率/并发限制、最大 body、超时和访问日志脱敏的反向代理；Redis 不得公网暴露。

首个纵向切片只提供 readiness/liveness、静态站点配置和上述四类端点，不提供管理后台、动态注册、Swagger UI、用户会话、设备指纹、遥测 SDK 或额外验证类型。

## 需要批准的兼容性结论

批准本文意味着冻结以下 v0.1 基线：路径与 path/body 字段分工、200/409/422 等状态码、严格 JSON 与 body 上限、CORS 规则、`Bearer keyId.secret`、Redis hash + Lua 资源存储，以及 Spring Boot 4.1 Servlet 实现线。之后改变这些内容必须记录替代决策；具体 patch 依赖升级可在兼容范围内经过依赖审查后进行。
