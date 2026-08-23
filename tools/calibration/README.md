# ChalSense 容差校准工具

此目录是独立、明确同意、本地优先的研究工具，不进入生产 Widget，不连接 Server，也不采集完整轨迹或设备指纹。

从仓库根目录启动任意只提供静态文件的本地 HTTP server，例如：

```text
python -m http.server 8000 --directory tools/calibration
```

访问 `http://127.0.0.1:8000/`。参与者阅读并勾选同意后，每人建议完成不超过 10 次，然后自行下载聚合 JSON。研究人员不得要求浏览器上传或附带身份信息；公开报告的任一分组必须至少包含 20 次有效尝试。

关闭或刷新页面会清除尚未导出的内存数据。导出文件不含时间戳、用户/会话 ID、UA、IP、业务字段或完整轨迹。测试：

```text
node --test tools/calibration/tests/*.test.mjs
```

研究人员在隔离工作目录合并参与者主动交付的文件：

```text
node tools/calibration/merge-aggregates.mjs participant-*.json > merged.json
```

误差使用 1,000 个规范化单位分桶，因此候选容差只报告“确定接受/可能接受”的上下界，不伪造桶内精度。合并结果 `attempts < 20` 时 `publicationEligible=false`，不得公开为人群结论。
