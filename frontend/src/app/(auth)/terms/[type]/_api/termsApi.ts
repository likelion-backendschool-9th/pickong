import { useQuery } from "@tanstack/react-query";
import { http } from "@/shared/api/http";

export interface Terms {
  type: "SERVICE" | "PRIVACY" | "MARKETING";
  title: string;
  required: boolean;
  version: string;
  effectiveDate: string;
  sections: { heading: string; body: string }[];
}

export const useTerms = (type: string) =>
  useQuery({ queryKey: ["terms", type], queryFn: () => http.get<Terms>(`/terms/${type}`), staleTime: 60 * 60_000 });
