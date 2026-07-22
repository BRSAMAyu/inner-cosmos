import { useCallback, useState } from "react";
import {
  api, type AdminAbTestConfig, type AdminAbTestStats, type AdminAiHealth, type AdminAiLog,
  type AdminAuditLog, type AdminCapsuleRow, type AdminModelConfigRow, type AdminOverview,
  type AdminReport, type AdminSafetyEvent, type AdminUserRow
} from "../api";

// Data-fetching/action state for the admin console (Phase 3 port of the legacy static
// /pages/admin.html, 8 tabs -- see web/src/components/admin/AdminConsole.tsx for the shell that
// composes the per-tab components using this hook's state). Kept separate from AuroraApp.tsx's own
// status banner (unlike useConnectionsAndLetters, which writes into the app-wide `setStatus`)
// because AdminConsole is a standalone route (like SafetyHarborPage), not one of the five
// ProductShell spaces, and should be usable/testable without wiring the whole app shell.
export function useAdminConsole() {
  const [overview, setOverview] = useState<AdminOverview | null>(null);
  const [users, setUsers] = useState<AdminUserRow[]>([]);
  const [capsules, setCapsules] = useState<AdminCapsuleRow[]>([]);
  const [reports, setReports] = useState<AdminReport[]>([]);
  const [reportStatusFilter, setReportStatusFilter] = useState("");
  const [auditLogs, setAuditLogs] = useState<AdminAuditLog[]>([]);
  const [safetyEvents, setSafetyEvents] = useState<AdminSafetyEvent[]>([]);
  const [modelConfig, setModelConfig] = useState<AdminModelConfigRow[]>([]);
  const [aiHealth, setAiHealth] = useState<AdminAiHealth | null>(null);
  const [aiLogs, setAiLogs] = useState<AdminAiLog[]>([]);
  const [abtestConfig, setAbtestConfig] = useState<AdminAbTestConfig | null>(null);
  const [abtestStats, setAbtestStats] = useState<AdminAbTestStats | null>(null);
  const [loading, setLoading] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [status, setStatus] = useState<string | null>(null);

  const loadOverview = useCallback(() => api.adminOverview().then(setOverview).catch(() => undefined), []);
  const loadUsers = useCallback(() => api.adminUsers().then(setUsers).catch(() => undefined), []);
  const loadCapsules = useCallback(() => api.adminCapsules().then(setCapsules).catch(() => undefined), []);
  const loadReports = useCallback((status?: string) =>
    api.adminReports((status ?? reportStatusFilter) || undefined).then(setReports).catch(() => undefined), [reportStatusFilter]);
  const loadAuditLogs = useCallback(() => api.adminAuditLogs().then(setAuditLogs).catch(() => undefined), []);
  const loadSafetyEvents = useCallback(() => api.adminSafetyEvents().then(setSafetyEvents).catch(() => undefined), []);
  const loadModelConfig = useCallback(() => api.adminModelConfig().then(setModelConfig).catch(() => undefined), []);
  const loadAiHealth = useCallback(() => api.aiHealth().then(setAiHealth).catch(() => undefined), []);
  const loadAiLogs = useCallback(() => api.aiLogs().then(setAiLogs).catch(() => undefined), []);
  const loadAbTestStats = useCallback((testName: string) =>
    api.abtestStats(testName).then(setAbtestStats).catch(() => setAbtestStats(null)), []);
  const loadAbTest = useCallback(async () => {
    const config = await api.abtestActive().catch(() => null);
    setAbtestConfig(config);
    if (config) await loadAbTestStats(config.testName);
    else setAbtestStats(null);
  }, [loadAbTestStats]);

  const loadAll = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([
        loadOverview(), loadUsers(), loadCapsules(), loadReports(), loadAuditLogs(),
        loadSafetyEvents(), loadModelConfig(), loadAiHealth(), loadAiLogs(), loadAbTest()
      ]);
    } finally { setLoading(false); }
  }, [loadOverview, loadUsers, loadCapsules, loadReports, loadAuditLogs, loadSafetyEvents,
    loadModelConfig, loadAiHealth, loadAiLogs, loadAbTest]);

  const hideCapsule = useCallback(async (id: number, reason: string) => {
    setBusyId(id);
    try {
      await api.adminHideCapsule(id, reason);
      await Promise.all([loadCapsules(), loadAuditLogs()]);
      setStatus("已隐藏");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法隐藏这个共鸣体"); }
    finally { setBusyId(null); }
  }, [loadCapsules, loadAuditLogs]);

  const restoreCapsule = useCallback(async (id: number, reason: string) => {
    setBusyId(id);
    try {
      await api.adminRestoreCapsule(id, reason);
      await Promise.all([loadCapsules(), loadAuditLogs()]);
      setStatus("已恢复");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法恢复这个共鸣体"); }
    finally { setBusyId(null); }
  }, [loadCapsules, loadAuditLogs]);

  const resolveReport = useCallback(async (id: number, action: string, reason: string) => {
    setBusyId(id);
    try {
      await api.adminResolveReport(id, action, reason);
      await Promise.all([loadReports(), loadCapsules(), loadAuditLogs()]);
      setStatus("举报已处理");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法处理这条举报"); }
    finally { setBusyId(null); }
  }, [loadReports, loadCapsules, loadAuditLogs]);

  const changeReportStatusFilter = useCallback((next: string) => {
    setReportStatusFilter(next);
    void loadReports(next);
  }, [loadReports]);

  const toggleAbTest = useCallback(async (id: number, enabled: boolean) => {
    setBusyId(id);
    try {
      await api.abtestToggle(id, enabled);
      await loadAbTest();
      setStatus(enabled ? "测试已启用" : "测试已暂停");
    } catch (error) { setStatus(error instanceof Error ? error.message : "暂时无法切换这个测试"); }
    finally { setBusyId(null); }
  }, [loadAbTest]);

  return {
    overview, users, capsules, reports, reportStatusFilter, auditLogs, safetyEvents,
    modelConfig, aiHealth, aiLogs, abtestConfig, abtestStats, loading, busyId, status,
    loadAll, loadCapsules, loadReports, loadAbTestStats,
    hideCapsule, restoreCapsule, resolveReport, changeReportStatusFilter, toggleAbTest,
    clearStatus: () => setStatus(null)
  };
}
