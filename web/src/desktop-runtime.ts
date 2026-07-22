import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { appDataDir } from "@tauri-apps/api/path";
import { getCurrent, onOpenUrl } from "@tauri-apps/plugin-deep-link";
import { isPermissionGranted, requestPermission, sendNotification } from "@tauri-apps/plugin-notification";
import { openUrl } from "@tauri-apps/plugin-opener";
import { Client, Stronghold } from "@tauri-apps/plugin-stronghold";

export function isTauriRuntime(): boolean {
  return typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;
}

class DesktopRuntimeBridge {
  private stronghold: Stronghold | null = null;
  private client: Client | null = null;

  async initialize(): Promise<void> {
    if (!isTauriRuntime() || this.client) return;
    const [directory, password] = await Promise.all([appDataDir(), invoke<string>("desktop_vault_password")]);
    this.stronghold = await Stronghold.load(`${directory}inner-cosmos-vault.hold`, password);
    try { this.client = await this.stronghold.loadClient("inner-cosmos"); }
    catch { this.client = await this.stronghold.createClient("inner-cosmos"); await this.stronghold.save(); }
  }

  async get(key: string): Promise<string | null> {
    await this.initialize();
    const bytes = await this.client?.getStore().get(key);
    return bytes ? new TextDecoder().decode(new Uint8Array(bytes)) : null;
  }

  async set(key: string, value: string): Promise<void> {
    await this.initialize();
    await this.client?.getStore().insert(key, [...new TextEncoder().encode(value)]);
    await this.stronghold?.save();
  }

  async remove(key: string): Promise<void> {
    await this.initialize();
    await this.client?.getStore().remove(key).catch(() => undefined);
    await this.stronghold?.save();
  }

  async clear(keys: string[]): Promise<void> { await Promise.all(keys.map(key => this.remove(key))); }

  openSystemBrowser(url: string): Promise<void> { return openUrl(url); }

  async listenDeepLinks(handler: (url: string) => void | Promise<void>): Promise<() => void> {
    const current = await getCurrent();
    for (const url of current ?? []) await handler(url);
    const [unlistenPlugin, unlistenSingleInstance] = await Promise.all([
      onOpenUrl(urls => urls.forEach(url => void handler(url))),
      listen<string[]>("inner-cosmos-deep-link", event => event.payload.forEach(url => void handler(url)))
    ]);
    return () => { unlistenPlugin(); unlistenSingleInstance(); };
  }

  async notify(title: string, body: string): Promise<void> {
    let granted = await isPermissionGranted();
    if (!granted) granted = (await requestPermission()) === "granted";
    if (!granted) throw new Error("Desktop notification permission was not granted");
    sendNotification({ title, body });
  }
}

export const desktopRuntime = new DesktopRuntimeBridge();
