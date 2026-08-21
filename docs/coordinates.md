# 坐标、渲染、轨迹与容差规范

## 文档状态

- 状态：D-014 已批准坐标线协议、舍入、clamp、Pointer Events 与 DPR 隔离规则；确定性原型已通过，详见 `docs/experiments/coordinate-prototype-report.md`。容差数值仍是待 v0.1 真实用户/攻击评估的工作参数。
- 适用对象：v0.1 `SLIDER_PUZZLE` 生成器、Java Core、HTTP 协议、Web Component 与跨语言实现。
- 已冻结机器向量：`docs/test-vectors/coordinates-v1.json`；原评审草案保留为 `coordinates-v1-draft.json`。
- 安全前提：客户端、坐标、轨迹、时间戳、显示尺寸、DPR 和成功回调全部不可信；本文解决一致性，不把客户端测量提升为可信证据。

## 设计目标

1. 同一挑战在源图尺寸、CSS 缩放、浏览器缩放、非整数布局和不同 DPR 下得到一致答案。
2. 轨迹从真实按下点计算，不依赖滑块中心或模板固定抓取点。
3. 线协议不传二进制浮点数，避免 Java、JavaScript 和其他语言在边界产生不同结果。
4. Canvas backing store 只影响清晰度，不影响协议坐标或服务端容差。
5. 容差由服务端权威策略生成；客户端不能指定或扩大。

## 坐标空间

| 名称 | 单位 | 原点与用途 | 是否进入协议 |
| --- | --- | --- | --- |
| Source Image Space (`S`) | 源图像素 | 解码后背景/模板的像素几何 | 否，仅生成器输入 |
| Normalized Coordinate Space (`N`) | 整数单位 | 背景左上角 `(0,0)` 到右下边界 `(1_000_000,1_000_000)` | 是，所有几何与轨迹 |
| Logical Canvas Space (`L`) | 逻辑单位 | 默认 `320 × 180`，供 Canvas 绘制 API | 响应提供尺寸，不提交答案 |
| CSS Box Space (`C`) | CSS px | Canvas 内容框在布局中的显示尺寸 | 否，仅客户端换算 |
| Backing Store Space (`B`) | 设备像素 | `canvas.width/height`，决定光栅清晰度 | 否 |
| Viewport Pointer Space (`V`) | CSS px | Pointer Events 的 `clientX/clientY` | 否，必须先换算 |

`N` 的常量 `coordinateScale = 1_000_000`。位置和尺寸均为整数；绝对背景位置通常在 `[0, coordinateScale]`，相对轨迹为了表示越界拖动允许受限负数和大于背景宽度的值。

## 确定性整数规则

### 舍入

`roundHalfAwayFromZero(z)`：取最接近的整数；恰好位于 `.5` 时远离零。

```text
  1.49 →  1
  1.50 →  2
 -1.49 → -1
 -1.50 → -2
```

服务端对整数分数 `numerator / denominator` 实现该规则时不得先转换为 `float`/`double`。中间乘法必须检查溢出。

### Clamp

```text
clamp(value, min, max) = min(max(value, min), max)
```

若 `min > max`，状态或请求无效，不交换参数。

## 生成器：Source → Normalized

对解码后的背景尺寸 `sourceWidth × sourceHeight`：

```text
normalizedX = roundHalfAwayFromZero(sourceX * coordinateScale / sourceWidth)
normalizedY = roundHalfAwayFromZero(sourceY * coordinateScale / sourceHeight)
normalizedWidth  = roundHalfAwayFromZero(sourceObjectWidth  * coordinateScale / sourceWidth)
normalizedHeight = roundHalfAwayFromZero(sourceObjectHeight * coordinateScale / sourceHeight)
```

要求：

- 使用解码后的固有像素尺寸，不使用 EXIF 未归一化方向下的宽高。资源进入生成器前必须完成方向归一化。
- 背景与拼图片的锚点统一为可见/模板包围盒左上角；透明 padding 必须在生成时明确计入或裁掉，不能由各渲染器猜测。
- `pieceStartX + pieceWidth <= coordinateScale`，目标包围盒也必须完全位于背景内。
- 服务端状态保存 `pieceTargetX` 和 `tolerance`；响应只公开渲染所需起点与尺寸。

## Widget：Viewport Pointer → Normalized Track

### 有效内容矩形

Widget 必须让 Canvas 内容框与背景宽高比一致，不使用会产生未知 letterbox 的 `object-fit`。每次 `pointerdown` 获取一次稳定的 `getBoundingClientRect()` 快照：

```text
rectLeft, rectTop, rectWidth, rectHeight
```

- `rectWidth > 0`、`rectHeight > 0`，且宽高比相对 `logicalWidth/logicalHeight` 的误差不超过实现规定的小阈值，否则不开始交互。
- 一次拖动中不因布局抖动重新读取 rect；`ResizeObserver` 变化应取消本次拖动，避免同一轨迹混用两个比例。
- 不支持在 Canvas 或其祖先上使用旋转、斜切、透视或非轴对齐 CSS transform。仅浏览器缩放和轴对齐等比例缩放属于 v0.1 保证范围。

### 真实按下点与相对轨迹

在 `pointerdown` 保存 `startClientX/startClientY/startTime` 并调用 `setPointerCapture(pointerId)`。第 i 个点：

```text
track[i].x = roundHalfAwayFromZero(
  (clientX[i] - startClientX) * coordinateScale / rectWidth)

track[i].y = roundHalfAwayFromZero(
  (clientY[i] - startClientY) * coordinateScale / rectHeight)

track[i].t = max(0, roundHalfAwayFromZero(eventTime[i] - startTime))
```

首点固定为 `{x:0,y:0,t:0,event:"START"}`。这一定义与抓取点在拼图片左侧、中心或右侧无关。

不要先分别舍入两个绝对坐标再相减；直接对视口差值换算，避免双重舍入。

### 拼图片位置

水平方向允许范围：

```text
minPieceX = 0
maxPieceX = coordinateScale - pieceWidth
pieceX[i] = clamp(pieceStartX + track[i].x, minPieceX, maxPieceX)
```

拼图片垂直位置固定为 `pieceStartY`；`track[i].y` 只用于有限轨迹启发式。提交值：

```text
finalPieceX = pieceX[last]
```

服务端必须按同一 clamp 公式重新计算，并要求提交的 `finalPieceX` 与重算结果相差不超过 1 个规范化单位。1 单位只吸收协议换算的整数边界，不是答案容差。

### Pointer Events 行为

- 只跟踪发起拖动的 primary pointer 和相同 `pointerId`；忽略其他 pointer。
- 支持 `mouse`、`pen`、`touch`，并设置与组件手势相符的 `touch-action`，防止滚动手势与拖动同时解释。
- `pointerup` 产生 `END`。`pointercancel`、失去 capture、窗口隐藏或内容矩形变化取消本次本地轨迹，不调用 `verify`；challenge 可重新开始或自然过期，但网络上已发出的 verify 不得重试。
- 可读取 `getCoalescedEvents()` 提高显示平滑度，但协议点数上限仍为 256；降采样必须保留首点、末点、方向极值和时间顺序。
- `event.timeStamp` 仅生成相对 `t`；服务端不相信其速度真实性，也不使用客户端绝对时间判断过期。

## Normalized → Logical Canvas

绘制位置：

```text
logicalX = normalizedX * logicalWidth  / coordinateScale
logicalY = normalizedY * logicalHeight / coordinateScale
```

绘制可以使用 Canvas 浮点逻辑坐标以获得平滑画面，但协议状态始终保留原整数 `N` 值；不得把渲染后的像素位置回读为答案。

`logicalWidth/logicalHeight` 由 challenge 响应提供并受 Widget 上下限约束。v0.1 建议生成器固定 `320 × 180`，以减少布局和基准变量；CSS 可以等比例显示为其他大小。

## Canvas、CSS 与 DPR

令 Canvas CSS 内容框为 `cssWidth × cssHeight`，有效 DPR 为：

```text
effectiveDpr = clamp(window.devicePixelRatio, 1, implementationMaxDpr)
backingWidth  = max(1, roundHalfAwayFromZero(cssWidth  * effectiveDpr))
backingHeight = max(1, roundHalfAwayFromZero(cssHeight * effectiveDpr))
```

建议 `implementationMaxDpr = 3`，防止异常 DPR 或超大组件分配过多内存。设置：

```text
canvas.width  = backingWidth
canvas.height = backingHeight
canvas.style.width/height = CSS 布局值
ctx.setTransform(backingWidth / logicalWidth, 0,
                 0, backingHeight / logicalHeight, 0, 0)
```

之后所有绘制使用 `L`。Pointer 换算只使用 `getBoundingClientRect()` 与 `N`，绝不乘除 DPR。浏览器 zoom 改变 CSS 几何或 DPR 时重新配置 backing store，但不能改变当前 challenge 的 `N` 几何。

## 答案匹配与容差

服务端权威判断：

```text
recomputedFinalPieceX = clamp(pieceStartX + lastTrackX,
                              0,
                              coordinateScale - pieceWidth)

structurallyConsistent = abs(submittedFinalPieceX - recomputedFinalPieceX) <= 1
positionAccepted = abs(recomputedFinalPieceX - pieceTargetX) <= tolerance
```

只有两个条件都成立且轨迹结构/启发式通过，位置部分才成功。

### v0.1 容差建议

工作假设：

```text
rawTolerance = roundHalfAwayFromZero(pieceWidth * 8 / 100)
tolerance = clamp(rawTolerance, 6250, 18750)
```

对于推荐逻辑宽度 320，这相当于约 2～6 logical px；fixture 中 `pieceWidth=156250` 得到 `tolerance=12500`，即 4 logical px。闭区间边界算成功。

该数值不是安全事实，必须由小型原型和真实用户/攻击回归共同校准。D-014 只批准它作为原型工作假设；客户端永远不能提交 `tolerance`。若未来按站点或难度调整，Challenge State 必须固化实际值和 `policyVersion`，保证一次挑战生命周期中规则不漂移。

## 轨迹规范与启发式边界

### 结构规则

- 点数 2～256；首点 `START`，末点 `END`，中间仅 `MOVE`。
- 首点严格为 `(0,0,0)`；`t` 非递减且最终 `t <= 30000`。
- `x/y` 是整数，绝对值不超过 `2_000_000`；`finalPieceX` 在 `[0, coordinateScale - pieceWidth]`。
- 请求解码后总大小由协议层限制；不得通过压缩炸弹绕过。

### 启发式规则

首版只能使用已版本化、可解释、可独立关闭的有限规则，例如不可能的事件顺序、零时长巨大位移、完全相同轨迹重放的短期摘要。不得把固定线性、平滑度或 y 抖动阈值描述为真人证明。

**建议：** 阶段 0 只冻结结构规则和原因码类别，不冻结未经样本评估的“人类行为阈值”。真实用户误拒与攻击通过率可测、可回滚之前，启发式失败应有清晰观测和分阶段上线策略。

完整轨迹默认仅在本次验证内存中存在，不进入普通日志、Ticket State 或长期数据集。任何样本留存必须另行完成隐私、许可、保留期和访问控制决策。

## 小型原型方案

原型用于验证规范，不是业务脚手架，也不引入框架、打包器或生产依赖。D-014 确定性原型已经完成；本节保留为复现和后续浏览器自动化依据。

### 组成

- 一个静态 HTML + 原生 JavaScript 页面：固定使用可程序生成的几何图形，不引入外部图片或字体许可问题。
- 一个纯函数参考实现：Source/N/L/C/B/V 换算、舍入、clamp、容差计算。
- 一个浏览器测试页：显示背景、拼图片、实际按下点、实时 `N` 坐标和最终误差。
- 一组 Playwright 参数矩阵；若尚未引入测试工具，可先手工运行并把自动化留到脚手架批准后。

### 参数矩阵

| 维度 | 样本 |
| --- | --- |
| CSS 宽度 | 240、320、333.3、480 px |
| DPR | 1、1.25、1.5、2、3 |
| 浏览器 zoom | 80%、100%、125%、175%、200% |
| 抓取点 | 拼图片左缘、中心、右缘、任意内部点 |
| 输入 | mouse、touch、pen；Pointer capture 与 cancel |
| 目标 | 容差下界、中心、上界、界外 1 个 `N` 单位 |
| 布局变化 | 拖动前 resize、拖动中 resize、隐藏/恢复 |

### 通过条件

1. 同一源几何在所有 CSS/DPR 组合中生成相同 `N` 答案，DPR 不出现在提交协议中。
2. 任意抓取点完成同一位移时 `finalPieceX` 相同。
3. 机器向量在 JavaScript 参考实现与未来 Java Core 中逐项一致。
4. 容差边界不因浮点显示误差翻转：边界成功，界外 1 单位失败。
5. 拖动中 resize/cancel 不提交；超大 DPR 被 clamp，Canvas 内存有明确上限。
6. 键盘与替代流程的交互需求形成独立无障碍测试项；原型不得把 pointer-only 当成完成无障碍支持。

### 原型要回答的问题

- `8% pieceWidth` 且 clamp 为 2～6 logical px 是否兼顾真实用户误拒和基础攻击成本？
- 320×180 固定逻辑画布在移动端最小可用宽度是否清晰、可操作？
- 256 点与 30 秒上限在低端触控设备、合并事件和辅助技术下是否足够？
- 非整数 CSS 尺寸和浏览器 zoom 下是否存在稳定的 ±1 `N` 结构误差？如不存在，应把该宽限收紧为严格相等。

## 明确不支持与接入要求

- v0.1 不保证旋转/透视/斜切 Canvas、非等比拉伸、跨 iframe 坐标拼接或自定义渲染器仍能正确提交。
- 自定义 Widget 必须通过相同坐标向量，不得使用 `offsetX`、Canvas backing pixel 或固定滑块中心替代规范。
- 接入方必须提供视觉挑战之外的 MFA、邮件验证或人工协助路径；坐标规范正确不等于无障碍要求已经满足。
