# 项目上下文

## 文档目的

本文是换对话或引入新贡献者时的上下文入口，记录 ChalSense 的起因、问题来源、调研过程和已经形成的方向。它不替代具体架构和决策文档。

最后更新：2026-08-22。

## 起因

ChalSense 源于 `smart-manage` 登录保护改造。原项目希望降低 OCR 识别、密码猜测、撞库、验证码资源滥用和单账号攻击风险，并需要同时支持公网与企业内网、多实例部署和基本手机操作。

经过讨论，原项目选择：

- 将传统验证码改为点击登录后弹出的滑块拼图。
- 首版不做文字点选。
- 前后端一起改造，而不是只更换登录页 UI。
- 使用 `tianai-captcha` 的图片生成和轨迹匹配能力。
- 项目自行维护挑战接口、Redis 状态、一次性票据、限流、登录绑定和审计。
- Redis 不可用时认证失败关闭；不回退到单实例状态，也不绕过验证。
- 登录保护运行阈值使用系统内置参数，支持多实例动态生效。

## smart-manage 实践带来的认识

### 便利点

- `tianai-captcha` 的生成、匹配、资源仓库和缓存抽象使 Java 项目可以快速接入。
- 核心库可以脱离 Starter 使用，便于业务掌控协议和 Redis 行为。
- 同一基础能力可以扩展滑块、旋转、图片还原和文字点选。

### 实际问题与坑点

1. 默认背景资源过少，生产使用必须维护合法、足量、质量稳定的图片池。
2. Builder 要先设置资源仓库再加载默认模板，调用顺序属于不直观的隐式约束。
3. 滑块验证使用轨迹首尾相对位移；如果前端按固定滑块中心计算，会出现视觉上对齐但服务端判断失败。
4. 原图尺寸、Canvas 内部尺寸、CSS 展示尺寸、模板宽度、设备像素比和取整方式缺少明确统一的坐标协议。
5. 固定比例容差会随展示尺寸变化，不能只凭单个像素阈值判断体验是否合理。
6. 库只提供生成和匹配，二次验证、安全票据、业务绑定、重放防护、限流和故障策略仍由业务补齐。
7. 缓存抽象没有替业务解决一次性原子消费；在不支持 Redis `GETDEL` 的环境中，原项目使用 Lua 完成读取并删除。
8. 将第三方内部 `AnyMap` 直接序列化到 Redis，会让持久格式和库内部类型耦合。
9. 为满足弹层、CSP、Pointer Events、触控和项目协议，原项目最终自行实现 Canvas 前端，没有直接使用第三方 Web SDK。
10. 轨迹启发式规则不等于机器学习风控，客户端轨迹仍然可以伪造。
11. 依赖仓库的 LICENSE 展示 Apache-2.0，但 Maven POM 元数据声明 MulanPSL2，说明发布治理和许可证一致性必须纳入新项目质量底线。

原项目的生效设计位于：

- `E:/cloud/code/smart-manage/docs/architecture/login-protection.md`
- `E:/cloud/code/smart-manage/smart-manage-api/src/main/java/sm/domain/sys/base/login/service/CaptchaConfiguration.java`
- `E:/cloud/code/smart-manage/smart-manage-api/src/main/java/sm/domain/sys/base/login/service/RedisCaptchaCacheStore.java`
- `E:/cloud/code/smart-manage/smart-manage-api/src/main/java/sm/domain/sys/base/login/service/LoginRedisAccessor.java`
- `E:/cloud/code/smart-manage/smart-manage-web/public/login.html`

这些文件是实践参考，不是 ChalSense 的架构约束。ChalSense 应提炼通用能力，不能复制 `smart-manage` 的领域逻辑。

## 为什么建立独立项目

继续在 `smart-manage` 内部扩展会导致：

- 通用验证码能力与具体登录领域耦合。
- 其他项目无法稳定复用协议和组件。
- 安全设计、前端组件、图片资源和发布治理缺少独立生命周期。
- 修复第三方库问题只能以业务适配代码存在，无法形成可测试的通用方案。

因此决定建立独立 GitHub 项目 ChalSense，未来由 `smart-manage` 作为首个真实使用方接入，而不是让验证码项目反向依赖 `smart-manage`。

## 核心判断

开源项目可以较好地解决挑战生成、基本匹配和自托管，但普遍缺少完整安全协议、可观测性、误判评估和无障碍设计。云厂商真正的优势是大规模遥测、设备与网络信誉、动态风险模型、攻防迭代和运营平台，而不是拼图绘制本身。

ChalSense 首版不应宣称达到云级风控。它应成为一套可信、透明、可自托管的人机验证基础设施，并诚实说明其安全边界。

