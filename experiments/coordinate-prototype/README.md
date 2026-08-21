# ChalSense 坐标确定性原型

## 目的

该目录验证 D-014 的坐标换算、舍入、clamp、CSS 缩放、Canvas backing store、DPR 和任意抓取点行为。它不是业务代码、正式 Widget 或 npm 项目，不包含生产依赖。

原型使用程序绘制的几何背景和拼图片，不引入外部图片、字体、轨迹样本或数据集。

## 文件

- `coordinate-core.mjs`：浏览器和 Node 共用的纯坐标函数。
- `verify-vectors.mjs`：执行已冻结的 `docs/test-vectors/coordinates-v1.json` 和额外矩阵不变量。
- `verify-vectors.ps1`：独立的 PowerShell/.NET 参考实现，用于避免 JavaScript 自证。
- `index.html`、`prototype.mjs`、`styles.css`：交互式 Canvas 页面。

## 运行

机器向量：

```text
node experiments/coordinate-prototype/verify-vectors.mjs
pwsh -File experiments/coordinate-prototype/verify-vectors.ps1
```

交互页面需要通过本地 HTTP 提供，避免浏览器对 `file:` ES module 的限制：

```text
python -m http.server 4173 --directory experiments/coordinate-prototype
```

然后访问 `http://127.0.0.1:4173/`。

## 验证范围

- CSS 宽度：240、320、333.3、480 px。
- 模拟 DPR：1、1.25、1.5、2、3。
- 抓取点：拼图片左侧、中心、右侧任意内部位置。
- 答案边界：目标中心、容差上下界和界外 1 个规范化单位。
- 交互中止：`pointercancel`、失去 pointer capture、页面隐藏和拖动中 resize。

本原型只能验证确定性和交互状态，不产生真实用户误拒率或攻击通过率。经验性容差校准属于 v0.1 发布前门禁。
