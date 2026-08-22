# Widget 设计、公开 API 与测试

## 文档状态

- **已批准决策：** D-014 固定坐标与 Pointer Events 语义，D-015 固定资源交付，D-032 固定 Widget 工具链、公开边界与无障碍基线。
- **实现事实：** `packages/widget` 已实现 `@chalsense/widget` 0.1.0 和 `<chalsense-widget>`，无运行时依赖；HTTP Server 尚未实现。
- **安全前提：** 浏览器、组件、资源、坐标、轨迹、时间戳和全部 DOM 事件都是不可信输入。Widget 只改善一致性与体验，不证明用户一定是真人。

## 工具链

调研日期：2026-08-22。构建基线为 Node.js 24 LTS 与 npm lockfile；开发依赖精确固定为 TypeScript 7.0.2、Vitest 4.1.11 和 Playwright 1.62.1。三者不进入发布包运行时依赖。

主要参考：

- [Node.js 发布与 LTS 状态](https://nodejs.org/en/about/previous-releases)
- [TypeScript 官方文档](https://www.typescriptlang.org/docs/)
- [Vitest 官方指南](https://vitest.dev/guide/)
- [Playwright 官方安装与测试指南](https://playwright.dev/docs/intro)

### 依赖审查与替代方案

- TypeScript 只负责编译和声明文件；没有使用 Babel 或生产 bundler。当前多文件 ESM 足以发布，避免为了单文件产物引入 Vite/Rollup 配置和插件供应链。
- Vitest 只执行纯坐标向量和快速单元测试；Jest 是可行替代，但会增加另一套 ESM/TypeScript 转换配置，且项目没有既有 Jest 生态需要兼容。
- Playwright 负责真实 Chromium Pointer Events、触摸、笔、键盘、Canvas 和 CSP；DOM 模拟器不能提供这些证据。未来兼容声明扩展到 Firefox/WebKit 前必须在 CI 增加相应项目。
- `package-lock.json` 固定全部传递依赖；`npm audit` 和无生产依赖检查进入每次升级评审。当前 npm scope 尚未发布，不宣称已有供应链签名或 provenance。

## 公共边界

接入方创建组件并注入 `WidgetConfiguration`：

```ts
import "@chalsense/widget";

const widget = document.querySelector("chalsense-widget");
widget.configure({
  transport,
  siteKey: "site_demo_01",
  action: "login",
  contextDigest: "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  locale: "zh-CN",
});
await widget.start();
```

`ChalSenseTransport` 只有两个逻辑方法：

```ts
interface ChalSenseTransport {
  createChallenge(request, signal): Promise<CreatedChallenge>;
  verifyChallenge(request, signal): Promise<VerificationTicketResult>;
}
```

transport 由接入方或未来官方 HTTP adapter 提供。Widget 不拼接 URL、不持有 service credential、不调用 ticket consume，也不冻结 HTTP 路径、状态码、认证或 CORS。

## 事件

| 事件 | detail | 语义 |
| --- | --- | --- |
| `chalsense-success` | `verificationTicket`、`protocolVersion`、服务端 `issuedAt/expiresAt`、`challengeId` | 仅表示拿到 ticket；业务后端仍须消费并结合其他风控决策 |
| `chalsense-error` | 低基数 `stage` 与是否可获取新 challenge | 不暴露答案距离、容差、轨迹原因或 transport 原始异常 |
| `chalsense-alternative` | 无 | 请求接入方转入 MFA、邮件验证或人工协助 |

事件均可被脚本伪造，业务后端不得信任 DOM 成功事件。

## 交互与隐私

- Pointer 轨迹从真实按下点计算；一次拖动固定使用按下时的内容矩形。
- DPR 只决定 Canvas backing store，最大为 3，不进入协议。
- `pointercancel`、活动拖动中真实 resize、页面隐藏或失去 capture 会取消本地轨迹，不调用 verify。
- 每个 challenge 最多调用一次 verify；网络失败或结果未知只允许获取新 challenge。
- 键盘使用左右方向键移动，Shift 提供细粒度，Enter 提交，Escape 重置本地移动。
- 只提交 `x/y/t/event`；不采集 pressure、倾角、设备指纹或持久标识。
- 资源必须是同源相对 URL或绝对 HTTPS URL，角色、媒体类型、声明尺寸和解码尺寸必须匹配；失败时放弃 challenge，不使用占位图提交。
- Shadow DOM 样式由包内同源 `widget.css` 加载；生产组件不要求 `style-src 'unsafe-inline'` 或脚本 `eval`。fixture 在严格的 `default-src 'self'` CSP 下执行浏览器测试。

键盘支持不能让视觉拼图自动适用于盲人或所有认知/运动障碍用户，因此替代验证事件和接入方替代流程是必需能力，不是可选主题功能。

## Fixture 与测试

`demo/` 使用内存 transport；`scripts/serve-demo.mjs` 在运行时生成自有 PNG fixture，不引入第三方图片、字体或数据集。它只验证 Widget，不代表生产 challenge 生成器或 HTTP API。

Windows 手工体验时双击 `packages/widget/demo/open-demo.cmd`；启动器会按 lockfile 安装缺失的开发依赖、构建 Widget、启动仅监听 `127.0.0.1` 的 HTTP fixture，并打开浏览器。也可在仓库根目录执行 `npm run demo:widget`。直接双击 `index.html` 会使用 `file://` 的不透明来源，浏览器会按预期阻止 ES module；复制三个 demo 文件也会遗漏 `dist/` 构建产物，因此两种方式均不受支持。不得为支持 `file://` 而放宽生产组件的 CSP 或模块安全边界。

```text
npm ci
npm run verify:widget
```

Vitest 执行 D-014 冻结坐标向量和纯函数边界；Playwright Chromium 覆盖 mouse/touch/pen 原生输入、真实拖动、任意抓取点、键盘、取消不提交、严格 challenge/resource 失败、verify 单次调用、成功事件和替代流程。CI 使用 Node.js 24 执行相同集合。
