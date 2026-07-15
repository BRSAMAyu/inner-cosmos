import { App } from "@capacitor/app";
import { Capacitor, type PluginListenerHandle } from "@capacitor/core";
import { Network, type ConnectionStatus } from "@capacitor/network";
import { PushNotifications } from "@capacitor/push-notifications";
import { KeychainAccess, SecureStorage } from "@aparajita/capacitor-secure-storage";

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

export type MobileRuntimeCallbacks = {
  onState: (state: MobileRuntimeState) => void;
  onResume: () => void | Promise<void>;
  onWakeIntent: (wakeIntentId: number) => void | Promise<void>;
  onPushToken?: (token: string) => void | Promise<void>;
};

const TRUSTED_WEB_HOSTS = new Set(["app.innercosmos.sg", "localhost", "127.0.0.1"]);

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

function wakeIntentFromNotification(data: unknown): number | null {
  if (!data || typeof data !== "object") return null;
  const value = (data as Record<string, unknown>).wakeIntent;
  const id = typeof value === "number" ? value : Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

export class MobileRuntime {
  private state: MobileRuntimeState = initialMobileState;
  private handles: PluginListenerHandle[] = [];
  private callbacks: MobileRuntimeCallbacks | null = null;
  private generation = 0;

  async start(callbacks: MobileRuntimeCallbacks): Promise<() => Promise<void>> {
    const generation = ++this.generation;
    await this.removeHandles();
    if (generation !== this.generation) return async () => undefined;
    this.callbacks = callbacks;
    const localHandles: PluginListenerHandle[] = [];
    const native = Capacitor.isNativePlatform();
    const status = await Network.getStatus().catch((): ConnectionStatus => ({ connected: true, connectionType: "unknown" }));
    if (generation !== this.generation) return async () => undefined;
    this.state = {
      ...initialMobileState,
      native,
      platform: Capacitor.getPlatform(),
      connected: status.connected,
      connectionType: status.connectionType
    };
    if (native) {
      await SecureStorage.setKeyPrefix("inner-cosmos_");
      await SecureStorage.setSynchronize(false);
      await SecureStorage.setDefaultKeychainAccess(KeychainAccess.whenUnlockedThisDeviceOnly);
      const permission = await PushNotifications.checkPermissions().catch(() => ({ receive: "prompt" as const }));
      if (generation !== this.generation) return async () => undefined;
      this.state.push = permission.receive === "granted" ? "granted" : permission.receive === "denied" ? "denied" : "prompt";
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
      if (resumed) void this.callbacks?.onResume();
    }));

    if (native) {
      localHandles.push(await App.addListener("appUrlOpen", event => void this.openDeepLink(event.url)));
      localHandles.push(await PushNotifications.addListener("registration", token => {
        this.patch({ push: "granted" });
        void SecureStorage.set("push-registration-token", token.value);
        void this.callbacks?.onPushToken?.(token.value);
      }));
      localHandles.push(await PushNotifications.addListener("registrationError", () => this.patch({ push: "error" })));
      localHandles.push(await PushNotifications.addListener("pushNotificationActionPerformed", action => {
        const id = wakeIntentFromNotification(action.notification.data);
        if (id) void this.callbacks?.onWakeIntent(id);
      }));
      const launch = await App.getLaunchUrl();
      if (launch?.url) await this.openDeepLink(launch.url);
    }
    if (generation !== this.generation) {
      await Promise.all(localHandles.map(handle => handle.remove()));
      return async () => undefined;
    }
    this.handles = localHandles;
    return () => this.stop(generation);
  }

  async requestPushRegistration(): Promise<MobilePermission> {
    if (!this.state.native) return "unavailable";
    try {
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

  async storeCredential(key: string, value: string): Promise<void> {
    if (!this.state.native) throw new Error("Secure credentials are never persisted by the web fallback");
    await SecureStorage.set(key, value);
  }

  async removeCredential(key: string): Promise<void> {
    if (!this.state.native) return;
    await SecureStorage.remove(key);
  }

  async stop(expectedGeneration?: number): Promise<void> {
    if (expectedGeneration !== undefined && expectedGeneration !== this.generation) return;
    this.generation++;
    await this.removeHandles();
    this.callbacks = null;
  }

  private async removeHandles(): Promise<void> {
    const handles = this.handles.splice(0);
    await Promise.all(handles.map(handle => handle.remove()));
  }

  private async openDeepLink(raw: string): Promise<void> {
    const id = parseWakeIntentDeepLink(raw);
    if (id) await this.callbacks?.onWakeIntent(id);
  }

  private patch(patch: Partial<MobileRuntimeState>): void {
    this.state = { ...this.state, ...patch };
    this.emit();
  }

  private emit(): void { this.callbacks?.onState({ ...this.state }); }
}

export const mobileRuntime = new MobileRuntime();
