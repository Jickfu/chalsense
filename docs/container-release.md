# 容器发布与供应链验证

本文定义 `ghcr.io/jickfu/chalsense-server` 的发布门禁。当前仓库只建立了流程，尚未创建版本标签或发布正式镜像。

## 结论分类

- **已批准决策**：镜像路径为 `ghcr.io/jickfu/chalsense-server`，替代 D-019 的未占用组织路径。
- **事实**：`.github/workflows/release-container.yml` 的手动运行只构建并扫描，不登录 GHCR、不签名、不发布；只有 `vMAJOR.MINOR.PATCH` 标签运行且根 POM 为完全相同的非 SNAPSHOT 版本时才进入 publish job。
- **事实**：发布前分别构建并扫描 `linux/amd64` 与 `linux/arm64`；存在已有修复版本的 `HIGH` 或 `CRITICAL` OS/library 漏洞时失败。
- **事实**：发布构建生成多架构清单、BuildKit SPDX SBOM 与 max-mode provenance，并额外生成 CycloneDX JSON；镜像 digest 使用 GitHub OIDC 的 Sigstore keyless 签名，同时写入 GitHub build provenance 与 SBOM attestation。
- **推论**：签名、provenance 和 SBOM 可以证明构件来自指定工作流并提高成分透明度，但不能证明代码无漏洞、运行环境可信或验证码能单独证明用户是真人。
- **建议**：正式发布仍需人工检查版本说明、依赖许可证、基础镜像安全公告、Q-004 校准门禁和所有 CI；不能把“扫描无发现”表述为“无漏洞”。

## 触发与标签

人工验证供应链工作流但不发布：

```text
gh workflow run release-container.yml --ref main
```

正式发布只接受稳定语义版本标签，例如 `v0.1.0`。发布前必须把所有 Maven 构件从 `0.1.0-SNAPSHOT` 改为 `0.1.0`，完成发布评审并提交，然后才可创建标签。标签会产生：

- `ghcr.io/jickfu/chalsense-server:0.1.0`
- `ghcr.io/jickfu/chalsense-server:0.1`
- `ghcr.io/jickfu/chalsense-server:0`
- `ghcr.io/jickfu/chalsense-server:latest`

发布身份绑定 GitHub tag ref。删除或移动标签不能撤回已经发布的镜像、签名和 transparency log 记录；版本标签必须视为不可变。

## 验证

拉取时优先使用发布记录中的 digest，而不是可移动 tag。GitHub provenance 可验证为：

```text
gh attestation verify \
  oci://ghcr.io/jickfu/chalsense-server@sha256:REPLACE_WITH_DIGEST \
  -R Jickfu/chalsense
```

Cosign keyless 签名验证：

```text
cosign verify ghcr.io/jickfu/chalsense-server@sha256:REPLACE_WITH_DIGEST \
  --certificate-identity-regexp '^https://github.com/Jickfu/chalsense/.github/workflows/release-container.yml@refs/tags/v[0-9]+\\.[0-9]+\\.[0-9]+$' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com
```

CycloneDX 文件作为 GitHub Actions artifact 保留 90 天，并作为 OCI/GitHub SBOM attestation 绑定 digest；BuildKit 还为每个平台写入 SPDX SBOM。artifact 保留期不等于发布支持期，长期发布归档仍需在首发前决定。

## 权限与依赖

扫描 job 只有 `contents: read`。publish job 仅在标签运行，拥有 `packages: write`、`id-token: write` 和 `attestations: write`；工作流不接收长期 registry、Cosign 或签名私钥。

CI Action 全部固定完整提交 SHA：Docker 官方的 QEMU/Buildx/login/metadata/build-push，GitHub 官方的 attest/attest-sbom/upload-artifact，Sigstore 的 cosign-installer，以及 Aqua Security 的 Trivy action。它们只在 CI 执行，不进入镜像或语言构件。Action 或扫描策略升级必须检查 release notes、许可证、维护状态和权限变化。

## 残余风险与不保证

- 依赖仓库、GitHub runner、Docker Hub、GHCR、Fulcio、Rekor 或 OIDC 上游仍可能故障或被攻陷。
- `ignore-unfixed=true` 会报告但不阻断没有上游修复版本的漏洞；发布评审必须人工判断是否延期、换基础镜像或接受风险。
- Trivy 的架构矩阵负责发布门禁；额外 CycloneDX 文件由 runner 原生架构生成，而 BuildKit 的 per-platform SPDX attestations才是多架构逐平台成分记录。
- 当前未建立镜像撤回、安全公告、长期归档、可复现字节级构建或紧急重签流程。
