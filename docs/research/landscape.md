# 行业与开源调研结论

## 调研范围

调研包含开源行为验证码、隐私友好挑战、国内外云验证码、安全标准、无障碍指南，以及 `smart-manage` 的实际接入经验。调研时间截至 2026-08-22；维护状态、价格和产品能力可能变化，实施前应重新核验。

## 开源项目

### tianai-captcha

参考：[GitHub](https://github.com/dromara/tianai-captcha)

- Java 生态集成方便，支持滑块、旋转、图片还原和文字点选。
- 资源仓库、CacheStore、Core 和 Spring Boot Starter 提供了良好起点。
- 官方明确说明主要负责生成与匹配，二次验证仍需使用方扩展。
- 默认资源少，坐标与轨迹约定不够显式，安全协议和业务绑定不是 Core 的完整能力。
- 根 POM 默认跳过测试。
- [仓库 LICENSE](https://github.com/dromara/tianai-captcha/blob/master/LICENSE)与 [POM 许可证元数据](https://raw.githubusercontent.com/dromara/tianai-captcha/master/pom.xml)不一致，是 ChalSense 必须避免的发布治理问题。

### AJ-Captcha

参考：[Gitee 主仓库](https://gitee.com/belief-team/captcha)、[GitHub 镜像](https://github.com/anji-plus/captcha)

- 滑块、文字点选、弹出和嵌入模式较成熟。
- Java、PHP、Vue、移动端、小程序等示例覆盖面广。
- 二次校验概念和接入流程比许多纯前端滑块明确。
- 历史前端和部分协议实现偏旧，模块耦合和维护信号需要谨慎评估。
- Gitee 在 2025 年发布过 1.4.0，但 GitHub 镜像明确标记暂停维护，不能只看单一镜像判断活跃度。

### GoCaptcha

参考：[核心项目](https://github.com/wenlng/go-captcha)、[独立服务](https://github.com/wenlng/go-captcha-service)

- 支持点击、滑动、拖拽和旋转，前后端生态较完整。
- 服务化实现覆盖 HTTP、gRPC、Redis、Docker、服务发现和动态配置。
- 模块化和独立服务形态值得借鉴。
- 首版若照搬其完整服务能力，会让配置和运维功能掩盖协议与 Core 的质量目标。

### ALTCHA

参考：[GitHub](https://github.com/altcha-org/altcha)

- 自托管、隐私优先，使用 PoW 降低视觉挑战摩擦。
- 使用 Web Component，重视 CSP、无障碍和多种服务端语言实现。
- Argon2、Scrypt 等内存困难算法可提高专用硬件批量求解成本。
- PoW 只能给请求定价，不能证明操作者是人；还需评估低性能设备的耗时、耗电和可访问性。

### Cap

参考：[GitHub](https://github.com/tiagozip/cap)

- Apache-2.0，自托管，使用 PoW 与浏览器 instrumentation challenge。
- 组件轻量、无视觉题目、支持 Docker 独立部署。
- 值得参考其现代组件、隐私立场、部署体验和文档结构。
- 浏览器探测仍是持续攻防问题，不能把固定客户端信号当作可靠身份。

## 开源项目的共同结论

### 共同优点

- 自托管、成本可控、接入门槛低。
- 图片、模板、缓存和 UI 通常具有一定扩展能力。
- 滑块比扭曲文字验证码更直观。
- 适合中小型系统提高简单自动化成本。

### 共同缺点

- 经常把位置正确或固定轨迹规则称为完整“行为风控”。
- 二次票据、一次性消费、场景绑定、Origin 校验和限流常被留给业务自行实现。
- 缺少公开的真实用户误判率和机器攻击通过率。
- 无障碍、高 DPI、缩放、触控、CSP 和弱网不是首要设计目标。
- 图片、字体、模板和样本的版权治理不足。
- 很少提供稳定协议、测试凭据、攻击回归集和兼容性承诺。

## 云验证码

### 阿里云验证码 2.0

参考：[产品概述](https://help.aliyun.com/zh/captcha/captcha2-0/product-overview/what-is-alibaba-cloud-captcha-2)

- 支持无痕、一点即过、滑块、拼图和图像复原。
- 结合设备、IP、交互行为和轨迹等多维信号。
- 覆盖 Web、H5、App 和小程序，并提供统计与容灾。

### 腾讯云验证码

参考：[操作指南](https://cloud.tencent.com/document/product/1110/36831)、[快速入门](https://cloud.tencent.com/document/product/1110/36839)

- 支持始终验证、可疑验证和无感验证。
- 提供体验、平衡、安全等级，以及 `EvilLevel`、`EvilBitMap` 等风险结果。
- 域名校验、统计、告警和业务自定义决策较完整。
- 服务端票据校验不可省略；未校验时客户端结果很容易伪造。

### 百度智能云 AFD

参考：[接入文档](https://cloud.baidu.com/doc/AFD/s/3miyd0ssy)

- 支持弹出、嵌入、轨迹匹配、点击、文字点选和轨迹绘制。
- `stk` 有效期一分钟且只能验证一次，体现了短时、单次票据的重要性。
- 需要服务端加解密与远程二次校验，网络和故障策略需要业务明确处理。

### Cloudflare Turnstile

参考：[官方文档](https://developers.cloudflare.com/turnstile/)

- 组合 PoW、浏览器 API、浏览器差异和行为信号，通常不展示视觉题目。
- 不要求网站流量经过 Cloudflare CDN。
- 重视隐私、WCAG 2.2 AA、统计和自动化测试专用密钥。
- 验证令牌仍必须服务端校验、短时有效并防止重复使用。

### Google reCAPTCHA

参考：[选择验证类型](https://docs.cloud.google.com/recaptcha/docs/choose-key-type)

- 强项是基于站点历史和场景的风险评分，而不是单一图片题目。
- 官方推荐分数型能力，并指出视觉挑战会增加摩擦，对计算机视觉、人工打码和无障碍用户也存在局限。

### AWS WAF CAPTCHA 与 Challenge

参考：[官方文档](https://docs.aws.amazon.com/waf/latest/developerguide/waf-captcha-and-challenge-actions.html)

- 与 WAF、Bot Control、会话令牌、规则和边缘流量结合。
- 可根据令牌和免疫时间执行静默 Challenge 或 CAPTCHA。
- 更适合 AWS 边缘防护，不是通用自托管验证码库。

## 云服务的共同结论

云服务的主要优势不是验证码图片，而是：

- 大规模设备、网络、账户和行为遥测。
- 持续更新的风险模型和威胁情报。
- 动态决定无感、交互、拒绝、限速或升级认证。
- 统计、告警、灰度、误判反馈、全球基础设施和 SLA。

共同代价包括闭源、持续费用、第三方脚本、远程校验延迟、供应商锁定、隐私与跨境问题，以及故障策略受外部服务影响。

## 安全与无障碍参考

- [OWASP Credential Stuffing Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Credential_Stuffing_Prevention_Cheat_Sheet.html)：CAPTCHA 只是纵深防御的一层，应结合 MFA、限流和指标监测。
- [OWASP Bot Management and Anti-Automation](https://cheatsheetseries.owasp.org/cheatsheets/Bot_Management_and_Anti-Automation_Cheat_Sheet.html)：不同业务动作具有不同威胁模型，单一层通过不代表请求可信。
- [W3C Inaccessibility of CAPTCHA](https://www.w3.org/TR/turingtest/)：交互式 CAPTCHA 会排除部分残障用户，视觉、听觉和认知替代方案也各有局限。
- [WCAG 2.2 Accessible Authentication](https://www.w3.org/WAI/WCAG22/Understanding/accessible-authentication-minimum.html)：认证过程不能只提供要求特定认知能力的路径。
- [Privacy Pass Architecture, RFC 9576](https://www.rfc-editor.org/info/rfc9576/)与 [HTTP Authentication Scheme, RFC 9577](https://www.rfc-editor.org/rfc/rfc9577.html)：为未来隐私保护、不可关联的一次性验证令牌提供长期参考，不属于首版范围。

## 对 ChalSense 的启示

1. 差异化重点是安全协议和工程正确性，不是玩法数量。
2. 轨迹启发式必须诚实描述，不宣传未经数据验证的 AI 风控。
3. Web Component、稳定协议和测试凭据比维护多个框架示例更有长期价值。
4. 服务端一次性验证、场景绑定和原子状态必须成为项目内置能力。
5. 无障碍和隐私必须从协议与产品阶段进入设计，而不是发布后补救。
6. 必须建立真实用户与攻击脚本共同参与的基准体系。

