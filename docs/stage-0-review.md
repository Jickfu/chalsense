# 阶段 0 评审记录

## 当前结论

**结论：阶段 0 通过。D-001～D-018、威胁模型、协议状态机、模块边界和坐标确定性已具备进入 v0.1 最小协议/Core 实现的证据。**

D-014 无依赖原型已完成，冻结坐标向量由 JavaScript 与 PowerShell/.NET 两个独立实现执行，共各 78 项、0 失败，并完成实际浏览器拖动、非整数 CSS 尺寸、DPR clamp 和窄屏验证。经验性容差校准明确移至 v0.1 发布前，不阻塞阶段 0。没有发现要求推翻 D-001～D-018 的新事实或安全问题。

评审日期：2026-08-22。

## 已完成证据

| 阶段 0 要求 | 证据 | 判断 |
| --- | --- | --- |
| 项目定位、目标与非目标 | `docs/project-context.md`、`docs/product-scope.md` | 满足 |
| 独立威胁模型 | `docs/threat-model.md` | 安全基线已由 D-013～D-018 支撑 |
| challenge / verify / ticket 状态机 | `docs/protocol.md` | 核心语义已批准，覆盖正常、失败、过期、重放、并发和基础设施故障 |
| 跨实现测试向量 | `docs/protocol-test-vectors.md`、`docs/test-vectors/protocol-v1.json` | 评审时协议向量为草案；现已由 Java Core runner 验证并依 D-023 冻结，HTTP/其他语言执行仍属于后续验收 |
| 坐标、缩放、Canvas、CSS、DPR、Pointer Events、轨迹与容差 | `docs/coordinates.md`、`docs/test-vectors/coordinates-v1.json`、`docs/experiments/coordinate-prototype-report.md` | 冻结向量由两个独立实现通过；浏览器原型通过；经验性容差留到 v0.1 发布前 |
| 模块边界 | `docs/architecture.md`、D-003、D-011、D-012 | 满足当前设计深度 |
| 隐私和安全主张 | `docs/product-scope.md`、`docs/threat-model.md` | 明确不采集设备指纹，不声称证明真人 |
| 无障碍替代路径 | D-012、`docs/product-scope.md`、`docs/coordinates.md` | 产品要求明确；具体接入验收仍需 v0.1 测试 |
| 素材与许可证 | D-006、`docs/product-scope.md` | 原则已批准；素材清单待 v0.1 资源进入仓库时建立 |
| 技术决策 | `docs/remaining-decisions.md`、D-013～D-018 | 已批准；仅最终容差数值保留 Q-004 |

## 已关闭的阶段 0 门禁

### 已完成的所有者决策

Q-005～Q-009 已分别由 D-015～D-018、D-013 解决；D-014 已解决 Q-004 的坐标线协议和原型启动条件。任何后续变更都必须先更新决策状态和理由，再同步协议、威胁模型、向量和坐标文档。

### 已取得验证证据

1. `experiments/coordinate-prototype/` 已验证任意抓取点、非整数 CSS 尺寸、缩放、DPR、clamp 和容差边界。
2. Node.js/JavaScript 与 PowerShell/.NET 两个独立 runner 均通过 18 个规范向量和 60 个组合矩阵。
3. 本地浏览器页面内矩阵、实际 Pointer 拖动、DPR 上限和窄屏布局通过，控制台无错误。

协议向量在未来 Java Core 与 HTTP 适配器中的执行，特别是并发和 State Store 结果未知路径，属于 v0.1 实现验收，不再阻塞设计基线。

### 发布治理遗留

- GitHub 组织、npm scope、Maven 坐标、域名和商标尚未最终核验。
- 尚无实际素材，因此许可证清单、SBOM 和构件签名只能在引入对应资产/依赖时验证。
- 这些事项不阻塞设计基线，但会阻塞相应构件命名或公开发布。

## 一致性检查结果

- D-001～D-018 均有可追溯的结论、理由和验证边界。
- v0.1 仍只有滑块拼图；PoW、设备指纹、管理后台和多语言 SDK 未进入首版范围。
- Core 仍不依赖 Spring；服务层与 Starter 只做适配。
- challenge 任何已受理验证尝试均单次消费；ticket 只通过 Core 统一原子消费。
- 客户端、坐标、轨迹、时间戳和成功回调在全部新增文档中均标为不可信。
- ticket 绑定 `siteKey`、`action`、`contextDigest`，且业务最终授权仍需组合限流、MFA 和风险策略。
- Redis 故障和结果未知均不回退内存、不绕过验证、不透明重试同一凭据。

## v0.1 下一门禁

阶段 0 通过只解除满足 D-010 的最小协议/Core 测试脚手架门禁；开始该工作仍应由项目所有者明确确认。首个实现里程碑必须让 Java Core 执行冻结坐标向量和协议状态向量，再实现内存 State Store 的并发与故障注入。Q-004 的最终经验性容差校准是 v0.1 发布前门禁。

本结论不授权管理后台、额外验证类型、设备指纹、生产数据采集、Spring 依赖进入 Core 或其他范围扩张。
