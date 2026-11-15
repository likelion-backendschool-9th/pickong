import { http } from "@/shared/api/http";
import type { ImportJobDto } from "./dto";
import { toImportJob } from "./mapper";

export const importApi = {
  start: async (input: { url: string } | { text: string }) => toImportJob(await http.post<ImportJobDto>("/imports", input)),
  get: async (id: string) => toImportJob(await http.get<ImportJobDto>(`/imports/${id}`)),
  cancel: (id: string) => http.delete<void>(`/imports/${id}`),
};
