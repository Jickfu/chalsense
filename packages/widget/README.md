# `@chalsense/widget`

ChalSense 的无框架验证 Web Component。当前包处于 `0.1.0` 开发阶段，HTTP API 尚未冻结。

```html
<chalsense-widget></chalsense-widget>
```

```ts
import "@chalsense/widget";

const widget = document.querySelector("chalsense-widget");
widget.configure({ transport, siteKey, action, contextDigest, locale: "zh-CN" });
widget.addEventListener("chalsense-success", ({ detail }) => {
  // detail 只是待业务后端消费的 verificationTicket，不代表业务授权成功。
});
await widget.start();
```

组件不包含 HTTP URL 或服务端凭据。请实现 `ChalSenseTransport`，并为不能完成视觉拼图的用户处理 `chalsense-alternative` 事件。

## 运行本地 Demo

Windows 可直接双击 `demo/open-demo.cmd`。也可以在仓库根目录运行：

```text
npm ci
npm run demo:widget
```

不要直接双击 `demo/index.html`，也不要把 `demo/` 中的文件单独复制到其他目录。该页面使用 ES module、构建产物和严格 CSP，必须通过启动器提供的本地 HTTP 地址访问。

完整设计与安全边界见仓库的 `docs/widget.md`。
