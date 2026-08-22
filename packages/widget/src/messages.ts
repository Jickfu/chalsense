import type { WidgetMessages } from "./types.js";

const ZH_CN: WidgetMessages = Object.freeze({
  title: "安全验证",
  start: "开始验证",
  loading: "正在加载验证题…",
  ready: "拖动拼图片完成验证",
  dragging: "拖动中",
  keyboardHint: "也可聚焦拼图后使用左右方向键移动，按 Enter 提交。",
  submitting: "正在验证，请勿重复提交…",
  success: "已取得验证票据，仍需由业务服务端确认。",
  failed: "本次挑战不可继续，请获取新挑战。",
  resourceFailed: "验证资源加载失败，请获取新挑战。",
  invalidConfiguration: "验证组件配置无效。",
  newChallenge: "获取新挑战",
  alternative: "使用其他验证方式",
  alternativeHint: "可由接入方转入 MFA、邮件验证或人工协助。",
  sliderLabel: "滑动拼图片",
});

const EN: WidgetMessages = Object.freeze({
  title: "Security verification",
  start: "Start verification",
  loading: "Loading challenge…",
  ready: "Drag the puzzle piece into place",
  dragging: "Dragging",
  keyboardHint: "You can also focus the puzzle, use Left and Right Arrow, then press Enter.",
  submitting: "Verifying. Do not submit again…",
  success: "A verification ticket was received. Your server must still validate it.",
  failed: "This challenge cannot continue. Request a new challenge.",
  resourceFailed: "Challenge resources failed to load. Request a new challenge.",
  invalidConfiguration: "The verification widget configuration is invalid.",
  newChallenge: "New challenge",
  alternative: "Use another verification method",
  alternativeHint: "The integrator can provide MFA, email verification, or human assistance.",
  sliderLabel: "Move the puzzle piece",
});

export function resolveMessages(
  locale: "zh-CN" | "en" | undefined,
  overrides: Partial<WidgetMessages> | undefined,
): WidgetMessages {
  return Object.freeze({ ...(locale === "en" ? EN : ZH_CN), ...overrides });
}
