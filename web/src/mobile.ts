import { App } from "@capacitor/app";
import { Capacitor, type PluginListenerHandle } from "@capacitor/core";
import { Device } from "@capacitor/device";
import { Haptics, ImpactStyle } from "@capacitor/haptics";
import { Keyboard, KeyboardResize } from "@capacitor/keyboard";
import { LocalNotifications } from "@capacitor/local-notifications";
import { Network, type ConnectionStatus } from "@capacitor/network";
import { PushNotifications } from "@capacitor/push-notifications";
import { SplashScreen } from "@capacitor/splash-screen";
import { StatusBar, Style } from "@capacitor/status-bar";
import { KeychainAccess, SecureStorage } from "@aparajita/capacitor-secure-storage";
import { desktopRuntime, isTauriRuntime } from "./desktop-runtime";

export type MobilePermission = "idle" | "prompt" | "granted" | "denied" | "unavailable" | "error";
export type MobileRuntimeState = {
  native: boolean;
  platform: string;
  connected: boolean;
  connectionType: string;
  appActive: boolean;
  push: MobilePermission;
  microphone: MobilePermission;
  lastRecoveryAt: string | null;
};

export const initialMobileState: MobileRuntimeState = {
  native: false,
  platform: "web",
  connected: true,
  connectionType: "unknown",
  appActive: true,
  push: "idle",
  microphone: "idle",
  lastRecoveryAt: null
};

export type DeviceContext = {
  installationId: string;
  platform: string;
  operatingSystem: string;
  appVersion: string;
  locale: string;
  timezone: string;
};

export type RecoverableDraft = {
  kind: "aurora" | "slow-letter";
  value: string;
  contextId: string | null;
  idempotencyKey: string;
  savedAt: number;
  expiresAt: number;
};

export type MobileRuntimeCallbacks = {
  onState: (state: MobileRuntimeState) => void;
  onResume: () => void | Promise<void>;
  onWakeIntent: (wakeIntentId: number) => void | Promise<void>;
  onPushToken?: (token: string) => void | Promise<void>;
};

/** Platform contract consumed by product code. Capacitor details stay behind this boundary. */
export interface PlatformRuntime {
  readonly kind: "web" | "capacitor" | "tauri";
  start(callbacks: MobileRuntimeCallbacks): Promise<() => Promise<void>>;
  requestPushRegistration(): Promise<MobilePermission>;
  requestMicrophonePermission(): Promise<MobilePermission>;
  saveDraft(kind: RecoverableDraft["kind"], value: string, contextId?: string | null,
    idempotencyKey?: string): Promise<RecoverableDraft | null>;
  loadDraft(kind: RecoverableDraft["kind"]): Promise<RecoverableDraft | null>;
  removeDraft(kind: RecoverableDraft["kind"]): Promise<void>;
  clearPrivateState(): Promise<void>;
  deviceContext(): Promise<DeviceContext>;
  scheduleWakeIntentNotification(input: { wakeIntentId: number; title: string; body: string; at: Date }): Promise<void>;
  cancelWakeIntentNotification(wakeIntentId: number): Promise<void>;
  impact(style?: "light" | "medium" | "heavy"): Promise<void>;
}

const TRUSTED_WEB_HOSTS = new Set(["app.innercosmos.sg", "localhost", "127.0.0.1"]);
const STORAGE_PREFIX = "inner-cosmos_";
const INSTALLATION_KEY = "installation-id";
const PUSH_TOKEN_KEY = "push-registration-token";
const WAKE_SCHEDULE_KEY = "wake-intent-schedule";
const DRAFT_TTL_MS = 24 * 60 * 60 * 1000;
const DRAFT_KEY = (kind: RecoverableDraft["kind"]) => `recoverable-draft:${kind}`;

export function parseWakeIntentDeepLink(raw: string): number | null {
  try {
    const url = new URL(raw);
    let candidate: string | null = null;
    if (url.protocol === "innercosmos:" && url.hostname === "aurora") {
      const match = url.pathname.match(/^\/wake\/(\d+)$/);
      candidate = match?.[1] ?? url.searchParams.get("wakeIntent");
    } else if (url.protocol === "https:" && TRUSTED_WEB_HOSTS.has(url.hostname)
      && url.pathname.startsWith("/app/aurora")) {
      candidate = url.searchParams.get("wakeIntent");
    }
    if (!candidate || !/^\d+$/.test(candidate)) return null;
    const id = Number(candidate);
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  } catch {
    return null;
  }
}

export function wakeIntentFromNotification(data: unknown): number | null {
  if (typeof data === "string") {
    try { return wakeIntentFromNotification(JSON.parse(data)); }
    catch { return null; }
  }
  if (!data || typeof data !== "object") return null;
  const value = (data as Record<string, unknown>).wakeIntent;
  const id = typeof value === "number" ? value : Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

export function wakeIntentFromLocalNotificationId(notificationId: unknown): number | null {
  const id = typeof notificationId === "number" ? notificationId : Number(notificationId);
  if (!Number.isSafeInteger(id) || id <= 1_000_000) return null;
  const wakeIntentId = id - 1_000_000;
  return wakeIntentId > 0 && wakeNotificationId(wakeIntentId) === id ? wakeIntentId : null;
}

export function wakeNotificationId(wakeIntentId: number): number {
  if (!Number.isSafeInteger(wakeIntentId) || wakeIntentId <= 0) throw new Error("Invalid WakeIntent notification identifier");
  return 1_000_000 + (wakeIntentId % 1_000_000_000);
}

export function dueWakeIntentIds(schedule: Record<string, number>, now = Date.now()): number[] {
  return Object.entries(schedule)
    .filter(([, at]) => Number.isFinite(at) && at <= now)
    .map(([id]) => Number(id))
    .filter(id => Number.isSafeInteger(id) && id > 0)
    .sort((left, right) => schedule[String(left)] - schedule[String(right)]);
}

function randomId(): string {
  return crypto.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

class BrowserDraftStore {
  private readonly databaseName = "inner-cosmos-recovery";

  private open(): Promise<IDBDatabase> {
    return new Promise((resolve, reject) => {
      const request = indexedDB.open(this.databaseName, 1);
      request.onupgradeneeded = () => request.result.createObjectStore("private-state");
      request.onsuccess = () => resolve(request.result);
      request.onerror = () => reject(request.error);
    });
  }

  async get(key: string): Promise<string | null> {
    const db = await this.open();
    try {
      return await new Promise((resolve, reject) => {
        const request = db.transaction("private-state", "readonly").objectStore("private-state").get(key);
        request.onsuccess = () => resolve(typeof request.result === "string" ? request.result : null);
        request.onerror = () => reject(request.error);
      });
    } finally { db.close(); }
  }

  async set(key: string, value: string): Promise<void> {
    const db = await this.open();
    try {
      await new Promise<void>((resolve, reject) => {
        const request = db.transaction("private-state", "readwrite").objectStore("private-state").put(value, key);
        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
      });
    } finally { db.close(); }
  }

  async remove(key: string): Promise<void> {
    const db = await this.open();
    try {
      await new Promise<void>((resolve, reject) => {
        const request = db.transaction("private-state", "readwrite").objectStore("private-state").delete(key);
        request.onsuccess = () => resolve();
        request.onerror = () => reject(request.error);
      });
    } finally { db.close(); }
  }

  clear(): Promise<void> {
    return new Promise((resolve, reject) => {
      const request = indexedDB.deleteDatabase(this.databaseName);
      request.onsuccess = () => resolve();
      request.onerror = () => reject(request.error);
      request.onblocked = () => reject(new Error("Private recovery storage is still open"));
    });
  }
}

export class MobileRuntime implements PlatformRuntime {
  readonly kind = Capacitor.isNativePlatform() ? "capacitor" as const : isTauriRuntime() ? "tauri" as const : "web" as const;
  private state: MobileRuntimeState = initialMobileState;
  private handles: PluginListenerHandle[] = [];
  private callbacks: MobileRuntimeCallbacks | null = null;
  private wakeTimers = new Map<number, number>();
  private deliveredWakeIntents = new Set<number>();
  private generation = 0;
  private readonly browserStore = typeof indexedDB === "undefined" ? null : new BrowserDraftStore();

  async start(callbacks: MobileRuntimeCallbacks): Promise<() => Promise<void>> {
    const generation = ++this.generation;
    await this.removeHandles();
    if (generation !== this.generation) return async () => undefined;
    this.callbacks = callbacks;
    const localHandles: PluginListenerHandle[] = [];
    const native = Capacitor.isNativePlatform();
    const desktop = isTauriRuntime();
    const status = await Network.getStatus().catch((): ConnectionStatus => ({ connected: true, connectionType: "unknown" }));
    if (generation !== this.generation) return async () => undefined;
    this.state = { ...initialMobileState, native: native || desktop, platform: Capacitor.getPlatform(), connected: status.connected,
      connectionType: status.connectionType };
    if (native) {
      await this.configureSecureStore();
      await Promise.all([
        Keyboard.setResizeMode({ mode: KeyboardResize.Native }).catch(() => undefined),
        StatusBar.setOverlaysWebView({ overlay: true }).catch(() => undefined),
        this.applySystemTheme(),
        SplashScreen.hide({ fadeOutDuration: 250 }).catch(() => undefined)
      ]);
      const permission = await PushNotifications.checkPermissions().catch(() => ({ receive: "prompt" as const }));
      if (generation !== this.generation) return async () => undefined;
      this.state.push = permission.receive === "granted" ? "granted" : permission.receive === "denied" ? "denied" : "prompt";
    } else if (desktop) {
      await desktopRuntime.initialize();
      this.state.platform = navigator.platform.toLowerCase().includes("mac") ? "macos" : "windows";
      const removeDeepLinks = await desktopRuntime.listenDeepLinks(url => this.openDeepLink(url));
      localHandles.push({ remove: async () => removeDeepLinks() });
    }
    this.emit();

    if (!native) {
      const updateBrowserConnection = (connected: boolean) => {
        const recovered = !this.state.connected && connected;
        this.patch({ connected, connectionType: connected ? "unknown" : "none",
          lastRecoveryAt: recovered ? new Date().toISOString() : this.state.lastRecoveryAt });
        if (recovered) void this.callbacks?.onResume();
      };
      const online = () => updateBrowserConnection(true);
      const offline = () => updateBrowserConnection(false);
      window.addEventListener("online", online);
      window.addEventListener("offline", offline);
      localHandles.push({ remove: async () => {
        window.removeEventListener("online", online);
        window.removeEventListener("offline", offline);
      } });
    }

    localHandles.push(await Network.addListener("networkStatusChange", statusChange => {
      const recovered = !this.state.connected && statusChange.connected;
      this.patch({ connected: statusChange.connected, connectionType: statusChange.connectionType,
        lastRecoveryAt: recovered ? new Date().toISOString() : this.state.lastRecoveryAt });
      if (recovered) void this.callbacks?.onResume();
    }));
    localHandles.push(await App.addListener("appStateChange", ({ isActive }) => {
      const resumed = !this.state.appActive && isActive;
      this.patch({ appActive: isActive });
      if (resumed) void this.resumeFromNativeBoundary();
    }));

    if (native) {
      const ownedAndroidWake = (event: Event) => {
        const id = Number((event as CustomEvent<{ id?: unknown }>).detail?.id);
        if (Number.isSafeInteger(id) && id > 0) void this.dispatchWakeIntent(id);
      };
      window.addEventListener("innercosmos:wake-intent", ownedAndroidWake);
      localHandles.push({ remove: async () => window.removeEventListener("innercosmos:wake-intent", ownedAndroidWake) });
      localHandles.push(await App.addListener("appUrlOpen", event => void this.openDeepLink(event.url)));
      localHandles.push(await App.addListener("backButton", () => void this.handleAndroidBack()));
      localHandles.push(await PushNotifications.addListener("registration", token => {
        this.patch({ push: "granted" });
        void SecureStorage.set(PUSH_TOKEN_KEY, token.value);
        void this.callbacks?.onPushToken?.(token.value);
      }));
      localHandles.push(await PushNotifications.addListener("registrationError", () => this.patch({ push: "error" })));
      localHandles.push(await PushNotifications.addListener("pushNotificationActionPerformed", action => {
        const id = wakeIntentFromNotification(action.notification.data);
        if (id) void this.dispatchWakeIntent(id);
      }));
      localHandles.push(await LocalNotifications.addListener("localNotificationActionPerformed", action => {
        // Capacitor/Android versions differ in whether `extra` returns as an object, JSON text,
        // or only the stable notification id when an existing Activity is resumed. Decode all
        // three forms, while restricting the id fallback to our WakeIntent namespace.
        const id = wakeIntentFromNotification(action.notification.extra)
          ?? wakeIntentFromNotification(action.notification)
          ?? wakeIntentFromLocalNotificationId(action.notification.id);
        if (id) void this.dispatchWakeIntent(id);
      }));
      const launch = await App.getLaunchUrl();
      if (launch?.url) await this.openDeepLink(launch.url);
      await this.consumeDueWakeIntents();
    }
    if (generation !== this.generation) {
      await Promise.all(localHandles.map(handle => handle.remove()));
      return async () => undefined;
    }
    this.handles = localHandles;
    return () => this.stop(generation);
  }

  async requestPushRegistration(): Promise<MobilePermission> {
    if (isTauriRuntime()) {
      try { await desktopRuntime.notify("Inner Cosmos", "Desktop notifications are ready."); this.patch({ push: "granted" }); return "granted"; }
      catch { this.patch({ push: "denied" }); return "denied"; }
    }
    if (!this.state.native) return "unavailable";
    try {
      // Firebase's native SDK terminates the app if register() is invoked without a packaged
      // google-services configuration. Local-complete builds deliberately have no Firebase
      // credential, so they request the same OS notification permission through the local
      // notification plugin and prove WakeIntent delivery without pretending FCM succeeded.
      if (import.meta.env.VITE_REMOTE_PUSH_ENABLED !== "true") {
        let localPermission = await LocalNotifications.checkPermissions();
        if (localPermission.display === "prompt") localPermission = await LocalNotifications.requestPermissions();
        const result = localPermission.display === "granted" ? "granted" : "denied";
        this.patch({ push: result });
        return result;
      }
      let permission = await PushNotifications.checkPermissions();
      if (permission.receive === "prompt") permission = await PushNotifications.requestPermissions();
      if (permission.receive !== "granted") {
        this.patch({ push: "denied" });
        return "denied";
      }
      this.patch({ push: "granted" });
      await PushNotifications.register();
      return "granted";
    } catch {
      this.patch({ push: "error" });
      return "error";
    }
  }

  async requestMicrophonePermission(): Promise<MobilePermission> {
    if (!navigator.mediaDevices?.getUserMedia) {
      this.patch({ microphone: "unavailable" });
      return "unavailable";
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      stream.getTracks().forEach(track => track.stop());
      this.patch({ microphone: "granted" });
      return "granted";
    } catch (error) {
      const denied = error instanceof DOMException && ["NotAllowedError", "SecurityError"].includes(error.name);
      this.patch({ microphone: denied ? "denied" : "error" });
      return denied ? "denied" : "error";
    }
  }

  async saveDraft(kind: RecoverableDraft["kind"], value: string, contextId: string | null = null,
    idempotencyKey = randomId()): Promise<RecoverableDraft | null> {
    if (!value.trim()) { await this.removeDraft(kind); return null; }
    const now = Date.now();
    const draft: RecoverableDraft = { kind, value, contextId, idempotencyKey, savedAt: now, expiresAt: now + DRAFT_TTL_MS };
    await this.store(DRAFT_KEY(kind), JSON.stringify(draft));
    return draft;
  }

  async loadDraft(kind: RecoverableDraft["kind"]): Promise<RecoverableDraft | null> {
    const raw = await this.read(DRAFT_KEY(kind));
    if (!raw) return null;
    try {
      const draft = JSON.parse(raw) as RecoverableDraft;
      if (draft.kind !== kind || typeof draft.value !== "string" || draft.expiresAt <= Date.now()
        || typeof draft.idempotencyKey !== "string") {
        await this.removeDraft(kind);
        return null;
      }
      return draft;
    } catch {
      await this.removeDraft(kind);
      return null;
    }
  }

  removeDraft(kind: RecoverableDraft["kind"]): Promise<void> { return this.remove(DRAFT_KEY(kind)); }

  async clearPrivateState(): Promise<void> {
    if (Capacitor.isNativePlatform()) {
      await Promise.all([DRAFT_KEY("aurora"), DRAFT_KEY("slow-letter"), PUSH_TOKEN_KEY, WAKE_SCHEDULE_KEY]
        .map(key => SecureStorage.remove(key).catch(() => undefined)));
    } else if (isTauriRuntime()) {
      await desktopRuntime.clear([DRAFT_KEY("aurora"), DRAFT_KEY("slow-letter"), PUSH_TOKEN_KEY, WAKE_SCHEDULE_KEY]);
    } else if (this.browserStore) await this.browserStore.clear().catch(() => undefined);
  }

  async deviceContext(): Promise<DeviceContext> {
    const native = Capacitor.isNativePlatform();
    const desktop = isTauriRuntime();
    let installationId = await this.read(INSTALLATION_KEY);
    if (!installationId) { installationId = randomId(); await this.store(INSTALLATION_KEY, installationId); }
    const [info, language, appInfo] = native
      ? await Promise.all([Device.getInfo(), Device.getLanguageCode(), App.getInfo()])
      : [{ platform: desktop ? (navigator.platform.toLowerCase().includes("mac") ? "macos" : "windows") : "web",
          operatingSystem: navigator.platform }, { value: navigator.language }, { version: desktop ? "0.1.0" : "web" }];
    return {
      installationId,
      platform: String(info.platform),
      operatingSystem: String(info.operatingSystem),
      appVersion: String(appInfo.version ?? "unknown"),
      locale: language.value,
      timezone: Intl.DateTimeFormat().resolvedOptions().timeZone
    };
  }

  async scheduleWakeIntentNotification(input: { wakeIntentId: number; title: string; body: string; at: Date }): Promise<void> {
    await this.rememberWakeIntent(input.wakeIntentId, input.at);
    if (isTauriRuntime()) {
      const delay = Math.max(0, input.at.getTime() - Date.now());
      const previous = this.wakeTimers.get(input.wakeIntentId);
      if (previous !== undefined) window.clearTimeout(previous);
      const handle = window.setTimeout(() => {
        this.wakeTimers.delete(input.wakeIntentId);
        void desktopRuntime.notify(input.title, input.body);
      }, Math.min(delay, 2_147_483_647));
      this.wakeTimers.set(input.wakeIntentId, handle);
      return;
    }
    if (!Capacitor.isNativePlatform()) return;
    let permission = await LocalNotifications.checkPermissions();
    if (permission.display === "prompt") permission = await LocalNotifications.requestPermissions();
    if (permission.display !== "granted") throw new Error("Local notification permission was not granted");
    const notificationId = wakeNotificationId(input.wakeIntentId);
    await LocalNotifications.schedule({ notifications: [{
      id: notificationId, title: input.title, body: input.body,
      schedule: { at: input.at, allowWhileIdle: true }, extra: { wakeIntent: input.wakeIntentId }
    }] });
  }

  async cancelWakeIntentNotification(wakeIntentId: number): Promise<void> {
    await this.forgetWakeIntent(wakeIntentId);
    const timer = this.wakeTimers.get(wakeIntentId);
    if (timer !== undefined) {
      window.clearTimeout(timer);
      this.wakeTimers.delete(wakeIntentId);
    }
    if (!Capacitor.isNativePlatform()) return;
    const id = wakeNotificationId(wakeIntentId);
    await LocalNotifications.cancel({ notifications: [{ id }] }).catch(() => undefined);
  }

  async impact(style: "light" | "medium" | "heavy" = "light"): Promise<void> {
    if (!Capacitor.isNativePlatform()) return;
    const mapped = style === "heavy" ? ImpactStyle.Heavy : style === "medium" ? ImpactStyle.Medium : ImpactStyle.Light;
    await Haptics.impact({ style: mapped }).catch(() => undefined);
  }

  async stop(expectedGeneration?: number): Promise<void> {
    if (expectedGeneration !== undefined && expectedGeneration !== this.generation) return;
    this.generation++;
    await this.removeHandles();
    this.callbacks = null;
  }

  private configureSecureStore(): Promise<void[]> {
    return Promise.all([
      SecureStorage.setKeyPrefix(STORAGE_PREFIX), SecureStorage.setSynchronize(false),
      SecureStorage.setDefaultKeychainAccess(KeychainAccess.whenUnlockedThisDeviceOnly)
    ]);
  }

  private async read(key: string): Promise<string | null> {
    if (Capacitor.isNativePlatform()) {
      await this.configureSecureStore();
      const value = await SecureStorage.get(key).catch(() => null);
      return typeof value === "string" ? value : null;
    }
    if (isTauriRuntime()) return desktopRuntime.get(key);
    return this.browserStore?.get(key) ?? null;
  }

  private async store(key: string, value: string): Promise<void> {
    if (Capacitor.isNativePlatform()) { await this.configureSecureStore(); await SecureStorage.set(key, value); }
    else if (isTauriRuntime()) await desktopRuntime.set(key, value);
    else if (this.browserStore) await this.browserStore.set(key, value);
  }

  private async remove(key: string): Promise<void> {
    if (Capacitor.isNativePlatform()) { await this.configureSecureStore(); await SecureStorage.remove(key).catch(() => undefined); }
    else if (isTauriRuntime()) await desktopRuntime.remove(key);
    else if (this.browserStore) await this.browserStore.remove(key);
  }

  private async removeHandles(): Promise<void> {
    const handles = this.handles.splice(0);
    await Promise.all(handles.map(handle => handle.remove()));
  }

  private async openDeepLink(raw: string): Promise<void> {
    const id = parseWakeIntentDeepLink(raw);
    if (id) await this.dispatchWakeIntent(id);
  }

  private async resumeFromNativeBoundary(): Promise<void> {
    await this.consumeDueWakeIntents();
    await this.callbacks?.onResume();
  }

  private async readWakeSchedule(): Promise<Record<string, number>> {
    const raw = await this.read(WAKE_SCHEDULE_KEY);
    if (!raw) return {};
    try {
      const parsed = JSON.parse(raw) as Record<string, unknown>;
      return Object.fromEntries(Object.entries(parsed)
        .filter(([id, at]) => /^\d+$/.test(id) && Number.isFinite(at))
        .map(([id, at]) => [id, Number(at)]));
    } catch { return {}; }
  }

  private async rememberWakeIntent(wakeIntentId: number, at: Date): Promise<void> {
    const schedule = await this.readWakeSchedule();
    schedule[String(wakeIntentId)] = at.getTime();
    this.deliveredWakeIntents.delete(wakeIntentId);
    await this.store(WAKE_SCHEDULE_KEY, JSON.stringify(schedule));
  }

  private async forgetWakeIntent(wakeIntentId: number): Promise<void> {
    const schedule = await this.readWakeSchedule();
    delete schedule[String(wakeIntentId)];
    if (Object.keys(schedule).length) await this.store(WAKE_SCHEDULE_KEY, JSON.stringify(schedule));
    else await this.remove(WAKE_SCHEDULE_KEY);
  }

  private async consumeDueWakeIntents(): Promise<void> {
    const schedule = await this.readWakeSchedule();
    for (const id of dueWakeIntentIds(schedule)) await this.dispatchWakeIntent(id);
  }

  private async dispatchWakeIntent(wakeIntentId: number): Promise<void> {
    if (this.deliveredWakeIntents.has(wakeIntentId)) return;
    this.deliveredWakeIntents.add(wakeIntentId);
    await this.forgetWakeIntent(wakeIntentId);
    await this.callbacks?.onWakeIntent(wakeIntentId);
  }

  private async applySystemTheme(): Promise<void> {
    const dark = window.matchMedia?.("(prefers-color-scheme: dark)").matches ?? true;
    await StatusBar.setStyle({ style: dark ? Style.Dark : Style.Light }).catch(() => undefined);
  }

  private async handleAndroidBack(): Promise<void> {
    const close = document.querySelector<HTMLElement>("[data-native-back-close]:not([hidden])");
    if (close) { close.click(); return; }
    if (window.location.hash && !window.location.hash.endsWith("/aurora")) { history.back(); return; }
    await App.minimizeApp();
  }

  private patch(patch: Partial<MobileRuntimeState>): void {
    this.state = { ...this.state, ...patch };
    this.emit();
  }

  private emit(): void { this.callbacks?.onState({ ...this.state }); }
}

export const mobileRuntime = new MobileRuntime();
