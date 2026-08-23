# Security Policy

## Supported versions

ChalSense 尚未发布稳定版本。`main` 上的代码处于 `0.x` 开发阶段，不提供生产支持或安全修复时限承诺。首个版本发布后，本节会列出受支持版本和停止支持日期。

## Reporting a vulnerability

请不要通过公开 Issue、Discussion、PR、日志或演示站点披露未修复漏洞、有效 ticket、credential、Redis URI、攻击样本或个人数据。

请使用仓库 **Security → Advisories → Report a vulnerability** 的 GitHub 私密漏洞报告入口：

<https://github.com/Jickfu/chalsense/security/advisories/new>

报告建议包含：受影响提交/版本、影响与攻击前提、最小复现步骤、是否已公开、建议缓解方式，以及希望使用的署名。请使用无真实用户数据、无生产 secret 的最小样本；若必须提供敏感附件，先在私密报告中协商传输方式。

维护者会尽力在 3 个工作日内确认收到、在 7 个工作日内给出初步分类；这些是响应目标而不是 SLA。修复、公告和披露日期将根据影响、利用状态、发布可用性和报告者意见协调。未经协调请不要公开利用细节。

## Scope

优先处理以下问题：

- challenge 或 verification ticket 重放、双消费、跨站点/动作/上下文绕过；
- service credential、ticket、答案、轨迹或网络标识泄露；
- CORS、Origin、代理信任、限流或 trusted/public 授权面绕过；
- Redis 状态损坏、结果未知重试或过期语义导致的安全失效；
- 容器、发布工作流、签名、SBOM/provenance 或依赖供应链绕过；
- 可由不可信输入触发的远程代码执行、任意文件访问或显著资源耗尽。

验证码只能作为纵深防御的一层。仅证明自动化可以解题、视觉挑战可被计算机视觉分析，或客户端成功回调可伪造，而没有突破已声明的服务端边界，通常属于已知限制；但新的低成本批量绕过证据仍欢迎私密报告。

## Safe harbor

仅在你拥有或获准测试的系统和数据上研究；避免隐私侵害、持久化、拒绝服务、社工、破坏数据以及访问超出证明问题所需的内容。发现敏感数据后停止访问并在私密报告中说明。善意遵守本政策的研究，项目会按协作修复处理，不主动威胁法律行动。
