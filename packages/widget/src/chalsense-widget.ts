import {
  COORDINATE_SCALE,
  aspectRatioAccepted,
  backingStoreSize,
  normalizedToLogical,
  piecePosition,
  pointerDeltaToTrack,
  roundHalfAwayFromZero,
} from "./coordinate.js";
import { resolveMessages } from "./messages.js";
import type {
  ChalSenseErrorDetail,
  ChalSenseSuccessDetail,
  ChallengeResourceRole,
  CreatedChallenge,
  TrackPoint,
  WidgetConfiguration,
  WidgetMessages,
} from "./types.js";
import { challengeAccepted, configurationAccepted, ticketAccepted } from "./validation.js";

type Phase = "IDLE" | "LOADING" | "READY" | "DRAGGING" | "SUBMITTING" | "SUCCESS" | "FAILED";

interface DragState {
  readonly pointerId: number;
  readonly startClientX: number;
  readonly startClientY: number;
  readonly startTime: number;
  readonly rect: DOMRectReadOnly;
  readonly track: TrackPoint[];
}

interface KeyboardState {
  readonly startedAt: number;
  readonly track: TrackPoint[];
}

export class ChalSenseWidget extends HTMLElement {
  private configuration: WidgetConfiguration | undefined;
  private messages: WidgetMessages = resolveMessages("zh-CN", undefined);
  private phase: Phase = "IDLE";
  private challenge: CreatedChallenge | undefined;
  private backgroundImage: HTMLImageElement | undefined;
  private pieceImage: HTMLImageElement | undefined;
  private currentPieceX = 0;
  private drag: DragState | undefined;
  private keyboard: KeyboardState | undefined;
  private request: AbortController | undefined;
  private resizeObserver: ResizeObserver | undefined;
  private suppressLostCapture = false;

  constructor() {
    super();
    this.attachShadow({ mode: "open" });
  }

  connectedCallback(): void {
    this.renderFrame();
    document.addEventListener("visibilitychange", this.handleVisibilityChange);
  }

  disconnectedCallback(): void {
    this.resetLocalState();
    document.removeEventListener("visibilitychange", this.handleVisibilityChange);
  }

  configure(configuration: WidgetConfiguration): void {
    this.configuration = configuration;
    this.messages = resolveMessages(configuration.locale, configuration.messages);
    this.resetLocalState();
    this.renderFrame();
  }

  async start(): Promise<void> {
    if (this.shadowRoot?.querySelector(".card") === null) this.renderFrame();
    this.request?.abort();
    this.resetLocalState();
    if (!configurationAccepted(this.configuration)) {
      this.setPhase("FAILED", this.messages.invalidConfiguration, true);
      this.emitError("CONFIGURATION", false);
      return;
    }

    const configuration = this.configuration;
    const controller = new AbortController();
    this.request = controller;
    this.setPhase("LOADING", this.messages.loading);
    try {
      const challenge = await configuration.transport.createChallenge({
        protocolVersion: "1",
        siteKey: configuration.siteKey,
        action: configuration.action,
        contextDigest: configuration.contextDigest,
      }, controller.signal);
      if (controller.signal.aborted) return;
      if (!challengeAccepted(challenge)) {
        this.setPhase("FAILED", this.messages.failed, true);
        this.emitError("CREATE", true);
        return;
      }
      this.challenge = challenge;
      this.currentPieceX = challenge.geometry.pieceStartX;
      try {
        const [background, piece] = await Promise.all([
          this.loadImage(this.resourceUrl("BACKGROUND"), controller.signal),
          this.loadImage(this.resourceUrl("PIECE"), controller.signal),
        ]);
        if (controller.signal.aborted) return;
        const backgroundResource = challenge.resources.find((resource) => resource.role === "BACKGROUND");
        const pieceResource = challenge.resources.find((resource) => resource.role === "PIECE");
        if (backgroundResource === undefined || pieceResource === undefined
            || background.naturalWidth !== backgroundResource.pixelWidth
            || background.naturalHeight !== backgroundResource.pixelHeight
            || piece.naturalWidth !== pieceResource.pixelWidth
            || piece.naturalHeight !== pieceResource.pixelHeight) {
          throw new Error("resource dimensions do not match challenge metadata");
        }
        this.backgroundImage = background;
        this.pieceImage = piece;
      } catch {
        if (controller.signal.aborted) return;
        this.setPhase("FAILED", this.messages.resourceFailed, true);
        this.emitError("RESOURCE", true);
        return;
      }
      this.showChallenge();
      this.setPhase("READY", this.messages.ready);
    } catch {
      if (controller.signal.aborted) return;
      this.setPhase("FAILED", this.messages.failed, true);
      this.emitError("CREATE", true);
    }
  }

  reset(): void {
    this.request?.abort();
    this.resetLocalState();
    this.setPhase("IDLE", "");
  }

  private renderFrame(): void {
    const root = this.shadowRoot;
    if (root === null) return;
    root.innerHTML = `
      <link rel="stylesheet">
      <section class="card" aria-labelledby="title">
        <header class="header"><h2 class="title" id="title"></h2><span class="badge">ChalSense</span></header>
        <div class="body">
          <div class="canvas-shell" hidden><canvas width="320" height="180" tabindex="0" role="slider"></canvas></div>
          <p class="status" role="status" aria-live="polite"></p>
          <p class="hint"></p>
          <div class="actions">
            <button class="primary" type="button" data-action="start"></button>
            <button class="link" type="button" data-action="alternative"></button>
          </div>
          <p class="hint alternative-hint"></p>
        </div>
      </section>`;
    this.required<HTMLLinkElement>("link[rel=stylesheet]").href = new URL("./widget.css", import.meta.url).href;
    this.text(".title", this.messages.title);
    this.text("[data-action=start]", this.messages.start);
    this.text("[data-action=alternative]", this.messages.alternative);
    this.text(".alternative-hint", this.messages.alternativeHint);
    this.text(".hint:not(.alternative-hint)", this.messages.keyboardHint);
    this.statusElement().textContent = this.phase === "IDLE" ? "" : this.messages.failed;
    this.startButton().addEventListener("click", () => void this.start());
    this.alternativeButton().addEventListener("click", () => {
      this.dispatchEvent(new CustomEvent("chalsense-alternative", { bubbles: true, composed: true }));
    });
    const canvas = this.canvas();
    canvas.setAttribute("aria-label", this.messages.sliderLabel);
    canvas.addEventListener("pointerdown", this.handlePointerDown);
    canvas.addEventListener("pointermove", this.handlePointerMove);
    canvas.addEventListener("pointerup", this.handlePointerUp);
    canvas.addEventListener("pointercancel", this.handlePointerCancel);
    canvas.addEventListener("lostpointercapture", this.handleLostPointerCapture);
    canvas.addEventListener("keydown", this.handleKeyDown);
  }

  private showChallenge(): void {
    const challenge = this.challenge;
    if (challenge === undefined) return;
    const shell = this.required<HTMLElement>(".canvas-shell");
    shell.hidden = false;
    const canvas = this.canvas();
    canvas.width = challenge.geometry.logicalWidth;
    canvas.height = challenge.geometry.logicalHeight;
    canvas.setAttribute("aria-valuemin", "0");
    canvas.setAttribute("aria-valuemax", String(COORDINATE_SCALE - challenge.geometry.pieceWidth));
    canvas.setAttribute("aria-orientation", "horizontal");
    this.updateAriaValue();
    this.resizeObserver?.disconnect();
    this.resizeObserver = new ResizeObserver(() => {
      const activeDrag = this.drag;
      const currentRect = canvas.getBoundingClientRect();
      if (activeDrag !== undefined
          && (Math.abs(currentRect.width - activeDrag.rect.width) > 0.01
            || Math.abs(currentRect.height - activeDrag.rect.height) > 0.01)) {
        this.cancelInteraction(this.messages.ready);
      }
      this.configureCanvas();
    });
    this.resizeObserver.observe(canvas);
    requestAnimationFrame(() => this.configureCanvas());
  }

  private configureCanvas(): void {
    const challenge = this.challenge;
    if (challenge === undefined || this.canvas().hidden) return;
    const canvas = this.canvas();
    const rect = canvas.getBoundingClientRect();
    if (!(rect.width > 0) || !(rect.height > 0)) return;
    const backing = backingStoreSize(rect.width, rect.height, window.devicePixelRatio);
    if (canvas.width !== backing.backingWidth) canvas.width = backing.backingWidth;
    if (canvas.height !== backing.backingHeight) canvas.height = backing.backingHeight;
    this.draw();
  }

  private draw(): void {
    const challenge = this.challenge;
    const background = this.backgroundImage;
    const piece = this.pieceImage;
    if (challenge === undefined || background === undefined || piece === undefined) return;
    const canvas = this.canvas();
    const context = canvas.getContext("2d");
    if (context === null) return;
    const geometry = challenge.geometry;
    context.setTransform(
      canvas.width / geometry.logicalWidth, 0, 0,
      canvas.height / geometry.logicalHeight, 0, 0,
    );
    context.clearRect(0, 0, geometry.logicalWidth, geometry.logicalHeight);
    context.drawImage(background, 0, 0, geometry.logicalWidth, geometry.logicalHeight);
    context.drawImage(
      piece,
      normalizedToLogical(this.currentPieceX, geometry.logicalWidth),
      normalizedToLogical(geometry.pieceStartY, geometry.logicalHeight),
      normalizedToLogical(geometry.pieceWidth, geometry.logicalWidth),
      normalizedToLogical(geometry.pieceHeight, geometry.logicalHeight),
    );
  }

  private readonly handlePointerDown = (event: PointerEvent): void => {
    const challenge = this.challenge;
    if (this.phase !== "READY" || challenge === undefined || !event.isPrimary || this.drag !== undefined) return;
    const canvas = this.canvas();
    const rect = canvas.getBoundingClientRect();
    const geometry = challenge.geometry;
    if (!aspectRatioAccepted(rect.width, rect.height, geometry.logicalWidth, geometry.logicalHeight)) return;
    const x = ((event.clientX - rect.left) * COORDINATE_SCALE) / rect.width;
    const y = ((event.clientY - rect.top) * COORDINATE_SCALE) / rect.height;
    if (x < this.currentPieceX || x > this.currentPieceX + geometry.pieceWidth
        || y < geometry.pieceStartY || y > geometry.pieceStartY + geometry.pieceHeight) return;
    canvas.setPointerCapture(event.pointerId);
    this.drag = {
      pointerId: event.pointerId,
      startClientX: event.clientX,
      startClientY: event.clientY,
      startTime: event.timeStamp,
      rect,
      track: [{ x: 0, y: 0, t: 0, event: "START" }],
    };
    canvas.classList.add("dragging");
    this.setPhase("DRAGGING", this.messages.dragging);
  };

  private readonly handlePointerMove = (event: PointerEvent): void => {
    const drag = this.drag;
    const challenge = this.challenge;
    if (drag === undefined || challenge === undefined || event.pointerId !== drag.pointerId) return;
    const point = this.pointerPoint(event, drag);
    if (point === undefined) {
      this.cancelInteraction(this.messages.ready);
      return;
    }
    this.currentPieceX = piecePosition(challenge.geometry.pieceStartX, point.x, challenge.geometry.pieceWidth);
    if (drag.track.length < 255) drag.track.push({ ...point, event: "MOVE" });
    this.updateAriaValue();
    this.draw();
  };

  private readonly handlePointerUp = (event: PointerEvent): void => {
    const drag = this.drag;
    const challenge = this.challenge;
    if (drag === undefined || challenge === undefined || event.pointerId !== drag.pointerId) return;
    const point = this.pointerPoint(event, drag);
    if (point === undefined) {
      this.cancelInteraction(this.messages.ready);
      return;
    }
    this.currentPieceX = piecePosition(challenge.geometry.pieceStartX, point.x, challenge.geometry.pieceWidth);
    drag.track.push({ ...point, event: "END" });
    this.releasePointer(event.pointerId);
    const track = [...drag.track];
    this.drag = undefined;
    this.canvas().classList.remove("dragging");
    this.updateAriaValue();
    this.draw();
    void this.submit(track);
  };

  private readonly handlePointerCancel = (): void => this.cancelInteraction(this.messages.ready);

  private readonly handleLostPointerCapture = (): void => {
    if (!this.suppressLostCapture && this.drag !== undefined) this.cancelInteraction(this.messages.ready);
  };

  private readonly handleVisibilityChange = (): void => {
    if (document.hidden) this.cancelInteraction(this.messages.ready);
  };

  private readonly handleKeyDown = (event: KeyboardEvent): void => {
    const challenge = this.challenge;
    if ((this.phase !== "READY" && this.phase !== "DRAGGING") || challenge === undefined) return;
    if (event.key === "Escape") {
      event.preventDefault();
      this.keyboard = undefined;
      this.currentPieceX = challenge.geometry.pieceStartX;
      this.setPhase("READY", this.messages.ready);
      this.updateAriaValue();
      this.draw();
      return;
    }
    if (event.key === "Enter") {
      event.preventDefault();
      const keyboard = this.keyboard ?? { startedAt: performance.now(), track: [{ x: 0, y: 0, t: 0, event: "START" }] };
      const x = this.currentPieceX - challenge.geometry.pieceStartX;
      const t = this.relativeKeyboardTime(keyboard);
      void this.submit([...keyboard.track, { x, y: 0, t, event: "END" }]);
      this.keyboard = undefined;
      return;
    }
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    const keyboard = this.keyboard ?? {
      startedAt: performance.now(),
      track: [{ x: 0, y: 0, t: 0, event: "START" } satisfies TrackPoint],
    };
    this.keyboard = keyboard;
    const normalStep = Math.max(1_000, roundHalfAwayFromZero(challenge.geometry.pieceWidth / 20));
    const fineStep = Math.max(1, roundHalfAwayFromZero(challenge.geometry.pieceWidth / 100));
    const direction = event.key === "ArrowRight" ? 1 : -1;
    this.currentPieceX = piecePosition(this.currentPieceX, direction * (event.shiftKey ? fineStep : normalStep), challenge.geometry.pieceWidth);
    if (keyboard.track.length < 255) {
      keyboard.track.push({
        x: this.currentPieceX - challenge.geometry.pieceStartX,
        y: 0,
        t: this.relativeKeyboardTime(keyboard),
        event: "MOVE",
      });
    }
    this.setPhase("DRAGGING", this.messages.dragging);
    this.updateAriaValue();
    this.draw();
  };

  private pointerPoint(event: PointerEvent, drag: DragState): Omit<TrackPoint, "event"> | undefined {
    const delta = pointerDeltaToTrack(
      { clientX: drag.startClientX, clientY: drag.startClientY }, event, drag.rect,
    );
    const previous = drag.track.at(-1)?.t ?? 0;
    const t = Math.max(previous, roundHalfAwayFromZero(event.timeStamp - drag.startTime));
    if (t > 30_000 || Math.abs(delta.x) > 2_000_000 || Math.abs(delta.y) > 2_000_000) return undefined;
    return { x: delta.x, y: delta.y, t };
  }

  private relativeKeyboardTime(state: KeyboardState): number {
    const previous = state.track.at(-1)?.t ?? 0;
    return Math.min(30_000, Math.max(previous, roundHalfAwayFromZero(performance.now() - state.startedAt)));
  }

  private async submit(track: readonly TrackPoint[]): Promise<void> {
    const challenge = this.challenge;
    const configuration = this.configuration;
    if (challenge === undefined || !configurationAccepted(configuration) || this.phase === "SUBMITTING") return;
    const controller = new AbortController();
    this.request?.abort();
    this.request = controller;
    this.setPhase("SUBMITTING", this.messages.submitting);
    try {
      const result = await configuration.transport.verifyChallenge({
        protocolVersion: "1",
        siteKey: configuration.siteKey,
        challengeId: challenge.challengeId,
        solution: { finalPieceX: this.currentPieceX, track },
      }, controller.signal);
      if (controller.signal.aborted) return;
      if (!ticketAccepted(result)) throw new Error("invalid ticket response");
      this.setPhase("SUCCESS", this.messages.success);
      const detail: ChalSenseSuccessDetail = {
        protocolVersion: result.protocolVersion,
        verificationTicket: result.verificationTicket,
        issuedAt: result.issuedAt,
        expiresAt: result.expiresAt,
        challengeId: challenge.challengeId,
      };
      this.dispatchEvent(new CustomEvent<ChalSenseSuccessDetail>("chalsense-success", {
        detail, bubbles: true, composed: true,
      }));
    } catch {
      if (controller.signal.aborted) return;
      this.setPhase("FAILED", this.messages.failed, true);
      this.emitError("VERIFY", true);
    }
  }

  private cancelInteraction(status: string): void {
    if (this.drag !== undefined) this.releasePointer(this.drag.pointerId);
    this.drag = undefined;
    this.keyboard = undefined;
    this.canvas().classList.remove("dragging");
    if (this.challenge !== undefined && this.phase !== "SUBMITTING" && this.phase !== "SUCCESS") {
      this.currentPieceX = this.challenge.geometry.pieceStartX;
      this.setPhase("READY", status);
      this.updateAriaValue();
      this.draw();
    }
  }

  private releasePointer(pointerId: number): void {
    const canvas = this.canvas();
    if (!canvas.hasPointerCapture(pointerId)) return;
    this.suppressLostCapture = true;
    canvas.releasePointerCapture(pointerId);
    queueMicrotask(() => { this.suppressLostCapture = false; });
  }

  private loadImage(url: string, signal: AbortSignal): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
      const image = new Image();
      const abort = (): void => {
        image.src = "";
        reject(new DOMException("aborted", "AbortError"));
      };
      signal.addEventListener("abort", abort, { once: true });
      image.addEventListener("load", () => {
        signal.removeEventListener("abort", abort);
        resolve(image);
      }, { once: true });
      image.addEventListener("error", () => {
        signal.removeEventListener("abort", abort);
        reject(new Error("resource load failed"));
      }, { once: true });
      image.src = new URL(url, document.baseURI).href;
    });
  }

  private resourceUrl(role: ChallengeResourceRole): string {
    const resource = this.challenge?.resources.find((candidate) => candidate.role === role);
    if (resource === undefined) throw new Error("missing resource");
    return resource.url;
  }

  private resetLocalState(): void {
    this.request?.abort();
    this.request = undefined;
    this.resizeObserver?.disconnect();
    this.challenge = undefined;
    this.backgroundImage = undefined;
    this.pieceImage = undefined;
    this.currentPieceX = 0;
    this.drag = undefined;
    this.keyboard = undefined;
    this.phase = "IDLE";
    const shell = this.shadowRoot?.querySelector<HTMLElement>(".canvas-shell");
    if (shell !== null && shell !== undefined) shell.hidden = true;
  }

  private setPhase(phase: Phase, status: string, showRestart = false): void {
    this.phase = phase;
    const statusElement = this.statusElement();
    statusElement.textContent = status;
    statusElement.className = `status${phase === "SUCCESS" ? " success" : phase === "FAILED" ? " failure" : ""}`;
    const start = this.startButton();
    start.hidden = phase === "READY" || phase === "DRAGGING" || phase === "SUBMITTING" || phase === "SUCCESS";
    start.disabled = phase === "LOADING" || phase === "SUBMITTING";
    start.textContent = showRestart ? this.messages.newChallenge : this.messages.start;
    this.canvas().setAttribute("aria-disabled", String(phase !== "READY" && phase !== "DRAGGING"));
  }

  private updateAriaValue(): void {
    this.canvas().setAttribute("aria-valuenow", String(this.currentPieceX));
  }

  private emitError(stage: ChalSenseErrorDetail["stage"], recoverableWithNewChallenge: boolean): void {
    const detail: ChalSenseErrorDetail = { stage, recoverableWithNewChallenge };
    this.dispatchEvent(new CustomEvent<ChalSenseErrorDetail>("chalsense-error", {
      detail, bubbles: true, composed: true,
    }));
  }

  private text(selector: string, value: string): void {
    this.required<HTMLElement>(selector).textContent = value;
  }

  private canvas(): HTMLCanvasElement { return this.required<HTMLCanvasElement>("canvas"); }
  private statusElement(): HTMLParagraphElement { return this.required<HTMLParagraphElement>(".status"); }
  private startButton(): HTMLButtonElement { return this.required<HTMLButtonElement>("[data-action=start]"); }
  private alternativeButton(): HTMLButtonElement { return this.required<HTMLButtonElement>("[data-action=alternative]"); }

  private required<T extends Element>(selector: string): T {
    const element = this.shadowRoot?.querySelector<T>(selector);
    if (element === null || element === undefined) throw new Error(`missing internal element: ${selector}`);
    return element;
  }
}
