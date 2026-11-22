import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { http } from "@/shared/api/http";
import { providerSlug, type SocialProvider } from "@/shared/auth/socialProviders";

/* 계정 설정: 로그인 수단 · 약관 동의 · 푸시 알림 · 디바이스 · 탈퇴 */

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

/** 연결된 소셜 로그인 수단. 이메일은 제공자가 주지 않으면 null */
export interface SocialAccount {
  provider: SocialProvider;
  email: string | null;
  connectedAt: string;
}

export const accountKeys = {
  socialAccounts: ["me", "social-accounts"] as const,
  agreements: ["me", "agreements"] as const,
  notifications: ["me", "notification-settings"] as const,
  devices: ["me", "devices"] as const,
};

export const useSocialAccounts = () =>
  useQuery({ queryKey: accountKeys.socialAccounts, queryFn: () => http.get<SocialAccount[]>("/me/social-accounts") });

/**
 * 계정 추가 연결 시작. 로그인한 상태에서 서버가 이 회원 전용의 1회용 연결 주소를 만들어 주고,
 * 그 주소로 이동해 제공자 인증(추가 인증)을 마치면 서버가 /settings/login-methods?linked= 또는 ?error=로 돌려보냅니다.
 */
export const useStartSocialLink = () =>
  useMutation({
    mutationFn: (provider: SocialProvider) =>
      http.post<{ authorizationUrl: string }>(`/me/social-accounts/${providerSlug(provider)}`),
  });

/** 연결 해제. 마지막 로그인 수단이면 서버가 409 LAST_LOGIN_METHOD로 거절합니다. */
export function useUnlinkSocialAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (provider: SocialProvider) => http.delete<void>(`/me/social-accounts/${providerSlug(provider)}`),
    onSettled: () => qc.invalidateQueries({ queryKey: accountKeys.socialAccounts }),
  });
}

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
  useMutation({ mutationFn: (input: { reason: string; detail: string }) => http.post<void>("/me/withdrawal", input) });
