# ChalSense Server

`chalsense-server` 是 D-034 批准的薄 Spring Boot HTTP 适配层。它只负责请求边界、静态配置、CORS、service credential、资源读取和健康检查；challenge/verify/ticket 状态机仍全部由 Core 执行。

当前默认监听 `127.0.0.1:8080`。限流能力完成前不得直接暴露公网；生产环境必须置于负责 TLS、速率/并发限制、超时和访问日志脱敏的反向代理之后。

## 最小配置

```yaml
chalsense:
  redis-uri: rediss://user:password@redis.example:6379
  redis-namespace: chalsense
  background-directory: /srv/chalsense/backgrounds
  maximum-concurrent-generations: 4
  sites:
    - site-key: site_test
      display-name: Example
      status: ACTIVE
      policy-version: "1"
      challenge-ttl: 120s
      ticket-ttl: 60s
      allow-insecure-loopback-origins: false
      allowed-actions: [login]
      allowed-origins: [https://app.example.com]
      credentials:
        - key-id: credential_1
          secret-sha256: REPLACE_WITH_43_CHARACTER_BASE64URL_SHA256
          active: true
          not-before: 1787360000000
          expires-at: 1787964800000
```

`secret` 必须由 32 个 CSPRNG 字节产生，并仅交给业务后端。Server 配置的是 `SHA-256(rawSecretBytes)` 的 32 字节无填充 Base64url，不是明文 secret；HTTP 使用 `Authorization: Bearer <keyId>.<secret>`。轮换时可短期同时配置新旧 key，并通过 `active: false` 立即吊销旧 key。`keyId` 在整个 Server 配置中必须唯一。

背景目录只读取目录第一层的 `.png`、`.jpg`、`.jpeg` 文件；项目不会下载客户端指定 URL。运维方必须为每个素材维护来源和许可证清单，仓库不附带生产素材。

## 安全边界

- 公共端点只对站点精确允许的 Origin 返回无 credential CORS。
- create、verify、consume 的 JSON body 上限分别是 2 KiB、64 KiB、4 KiB；压缩请求被拒绝。
- 资源存入 Redis 单 hash，并由 Lua 原子写入和 `PEXPIREAT` 设置硬过期；读取可重复，challenge 与 ticket 仍只能消费一次。
- 客户端、坐标、轨迹、时间戳和成功事件均不可信。取得 ticket 不等于业务授权，更不单独证明用户一定是真人。
- 默认不采集设备指纹。

## 端到端集成测试

普通 `mvn test` 不要求本机安装 Redis。对专用测试实例显式启用后，测试会启动两个随机 HTTP 端口的 Server，共享一个随机 Redis namespace，完整执行 create、跨实例资源重复读取、并发 verify、并发 consume 和再次重放。并发路径必须各自只有一次返回 200，其余返回 409。

```text
./mvnw -pl chalsense-server -am \
  -Dchalsense.server.integration=true \
  -Dchalsense.redis.host=127.0.0.1 \
  -Dchalsense.redis.port=6379 test
```

需要 ACL/TLS 时可改传 `-Dchalsense.redis.uri=rediss://...`。URI 可能含凭据，不得写入仓库、命令输出或 CI 日志。GitHub Actions 会在 Java 17/21 上分别对 Redis OSS 7.2.14 和 Valkey 7.2.14 执行该测试；fixture 图片由测试代码在临时目录生成，不使用第三方素材。
