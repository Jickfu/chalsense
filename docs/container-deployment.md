# OCI 镜像与 Compose 部署基线

本文定义 `chalsense-server` v0.1 的容器构建、最小运行拓扑和安全边界。验证码只是纵深防御的一层；部署成功不表示它能单独证明用户一定是真人，也不替代 TLS、限流、MFA 和业务风控。

## 结论分类

- **事实**：根目录 `Dockerfile` 以 Java 21 构建、Java 17 字节码运行，最终进程使用固定 `10001:10001`，镜像不包含生产背景素材。
- **事实**：CI 会在只读根文件系统、`/tmp` tmpfs、全部 capability 移除和 `no-new-privileges` 下启动镜像，并以真实 Redis 验证 `/livez` 和 `/readyz`。
- **事实**：Compose 默认只把业务端口发布到宿主机 `127.0.0.1:8080`；Redis 不发布端口，management 端口也不发布到宿主机。
- **推论**：固定 UID 和只读文件系统能降低容器逃逸后的写入面，但不能替代宿主机、容器运行时和编排平台的补丁与隔离。
- **建议**：生产环境使用反向代理或 ingress 提供 TLS、连接/带宽限制、超时和访问日志脱敏；Redis 使用受控私网、ACL/TLS、备份与容量监控。
- **工作假设**：首个镜像使用 Eclipse Temurin `21.0.11_10` Noble JRE。升级 JDK 或基础系统前必须重新执行镜像启动、健康与漏洞扫描。

## 基础镜像与许可证

构建和运行阶段使用 Docker Official Image `eclipse-temurin`。该镜像由 Adoptium 维护；OpenJDK 使用 GPL-2.0 with Classpath Exception，镜像 Dockerfile/脚本使用 Apache-2.0，基础发行版还包含各自许可证的软件。构建阶段额外安装 Ubuntu `unzip`，使 Maven Wrapper 下载 `.zip` 并执行仓库固定的 SHA-256 校验；它不进入最终运行镜像。官方说明和当前标签以 [Eclipse Temurin Docker Official Image](https://hub.docker.com/_/eclipse-temurin/) 为准。

选择完整 patch tag 而不是浮动 `21`，让评审可以还原构建输入；它仍不是内容摘要固定。D-039 已建立多架构、digest/SBOM、漏洞扫描、签名和 provenance 发布门禁；在第一个合规版本标签完成前，当前本地镜像仍不得宣传为正式发布物。详见[容器发布规范](container-release.md)。

## 本地 Compose

先准备一个只含合法 `.png`、`.jpg` 或 `.jpeg` 的绝对目录，并为素材维护来源与许可证记录。仓库不下载、不生成也不内置生产素材。

```text
cd deploy/compose
cp .env.example .env
# 编辑 .env：填写绝对背景目录，并替换两个 demo-only secret
docker compose up --build
```

默认使用 Redis 7.2.14。切换 Valkey 7.2.14：

```text
docker compose -f compose.yml -f compose.valkey.yml up --build
```

默认站点只允许来自 `http://127.0.0.1:8000` 或 `http://localhost:8000` 的本地 Widget。业务端口只绑定宿主 loopback，因此它是开发/验证拓扑，不是公网部署清单。停止并删除容器：

```text
docker compose down
```

只有明确希望清除本地 Redis/Valkey 演示状态时才运行 `docker compose down --volumes`。

## 生产边界

- 容器内 `server.address=0.0.0.0` 只表示监听容器网络；启动保护仍要求内建双桶限流开启。
- 示例的已知 service secret 与 HMAC key 只能用于宿主 loopback 的本地演示，任何共享或非本机环境都必须分别生成新的 32 字节 CSPRNG secret。
- 背景目录只读挂载。临时写入只允许 `/tmp` tmpfs，镜像不要求 root 或额外 capability。
- `/livez` 只检查进程响应；流量接入门禁必须使用 `/readyz`。健康响应不包含 Redis URI、凭据或节点详情。
- management 端口默认只监听容器 loopback。需要 Prometheus sidecar 时，应建立独立受控监控网络并显式调整监听地址，不能发布到公网。
- service credential、Redis 凭据和 HMAC key 应由 secret manager 注入；环境变量和 Compose `.env` 只是本地示例，不是推荐的生产秘密交付方式。
- 客户端、坐标、轨迹、时间戳和成功回调仍是不可信输入；反向代理不得记录请求体、Authorization、ticket、challenge 标识或动态资源 URL。

## 尚未保证

当前目标路径已由 D-039 修订为 `ghcr.io/jickfu/chalsense-server`，但尚未创建版本标签或发布镜像。当前仍不保证离线镜像、FIPS、rootless runtime 兼容矩阵或 Kubernetes Helm chart。
