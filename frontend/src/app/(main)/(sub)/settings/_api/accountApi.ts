import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { http } from "@/shared/api/http";

/* 계정 설정: 약관 동의 · 푸시 알림 · 디바이스 · 탈퇴 */

export type TermsType = "SERVICE" | "PRIVACY" | "MARKETING";

export interface Agreement {
  type: TermsType;
  title: string;
  required: boolean;
  version: string;
  agreed: boolean;
  agreedAt: string | null;
}

export type NotificationItem = "like" | "comment" | "follow" | "message" | "planReminder" | "marketing";

export interface NotificationSettings {
  pushEnabled: boolean;
  items: Record<NotificationItem, boolean>;
  quietHours: { enabled: boolean; start: string; end: string };
}

export interface Device {
  id: string;
  name: string;
  platform: "IOS" | "ANDROID" | "WEB";
  appVersion: string;
  lastActiveAt: string;
  isCurrent: boolean;
  pushEnabled: boolean;
}

export const accountKeys = {
  agreements: ["me", "agreements"] as const,
  notifications: ["me", "notification-settings"] as const,
  devices: ["me", "devices"] as const,
};

export const useAgreements = () => useQuery({ queryKey: accountKeys.agreements, queryFn: () => http.get<Agreement[]>("/me/agreements") });

export function useUpdateAgreement() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { type: TermsType; agreed: boolean }) => http.patch<Agreement[]>("/me/agreements", input),
    onSuccess: (list) => {
      qc.setQueryData(accountKeys.agreements, list);
      // 마케팅 동의는 알림 설정의 ‘이벤트 · 혜택’과 같은 값입니다.
      qc.invalidateQueries({ queryKey: accountKeys.notifications });
    },
  });
}

export const useNotificationSettings = () =>
  useQuery({ queryKey: accountKeys.notifications, queryFn: () => http.get<NotificationSettings>("/me/notification-settings") });

export function useSaveNotificationSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (settings: NotificationSettings) => http.put<NotificationSettings>("/me/notification-settings", settings),
    onSuccess: (s) => {
      qc.setQueryData(accountKeys.notifications, s);
      qc.invalidateQueries({ queryKey: accountKeys.agreements });
    },
  });
}

export const useDevices = () => useQuery({ queryKey: accountKeys.devices, queryFn: () => http.get<Device[]>("/me/devices") });

export function useUpdateDevice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, pushEnabled }: { id: string; pushEnabled: boolean }) => http.patch<Device>(`/me/devices/${id}`, { pushEnabled }),
    onSuccess: () => qc.invalidateQueries({ queryKey: accountKeys.devices }),
  });
}

export function useRemoveDevice() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => http.delete<void>(`/me/devices/${id}`),
    onSuccess: () => qc.invalidateQueries({ queryKey: accountKeys.devices }),
  });
}

export const useWithdraw = () =>
  useMutation({ mutationFn: (input: { reason: string; detail: string; password: string }) => http.post<void>("/me/withdrawal", input) });
