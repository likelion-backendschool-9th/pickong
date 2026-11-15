import { useMutation } from "@tanstack/react-query";
import { useEffect, useRef } from "react";
import { importApi } from "./importApi";
import type { ImportJob } from "./mapper";

export const useStartImport = () => useMutation({ mutationFn: importApi.start });

/** 분석 작업 상태 polling. 진행 단계를 알리고, 끝나면(DONE/FAILED) 멈춥니다. */
export function useImportPolling(
  jobId: string | null,
  handlers: { onProgress: (job: ImportJob) => void; onFinish: (job: ImportJob) => void; onError: () => void },
) {
  const ref = useRef(handlers);
  useEffect(() => {
    ref.current = handlers;
  });

  useEffect(() => {
    if (!jobId) return;
    let cancelled = false;
    let timer: ReturnType<typeof setTimeout>;
    const tick = async () => {
      try {
        const job = await importApi.get(jobId);
        if (cancelled) return;
        if (job.status === "RUNNING") {
          ref.current.onProgress(job);
          timer = setTimeout(tick, 500);
        } else {
          ref.current.onFinish(job);
        }
      } catch {
        if (!cancelled) ref.current.onError();
      }
    };
    tick();
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [jobId]);
}
