import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { putJson, postForm, put, getJson, postJson } from "./client";
import type {
  FeatureChange,
  SourceFileDetail,
  SourceFileSummary,
  FileCategory,
  FileCategoryOption,
  AiConfiguration,
  CondominiumContext,
  FeatureEvent,
  CondominiumFeature,
  CategoryChange,
  Dashboard,
  DocumentSearchRequest,
  AiConfigurationRequest,
  ActivePeriod,
  AiProvider,
  DocumentChunk,
  PeriodUsage,
  CurrentUser,
  MonthlyEvolution,
  UtilityAccount,
  UtilityType,
} from "./types";

/** Processing or indexing still running: the screen keeps asking every 3 seconds. */
const inProgress = (f: SourceFileSummary) =>
  f.status === "PENDING" ||
  f.status === "PROCESSING" ||
  f.indexing?.status === "QUEUED" ||
  f.indexing?.status === "INDEXING";

export function useCurrentUser() {
  return useQuery({ queryKey: ["me"], queryFn: () => getJson<CurrentUser>("/me") });
}

export function useCategories() {
  return useQuery({ queryKey: ["categories"], queryFn: async () => (await getJson<FileCategoryOption[]>("/categories")) ?? [], staleTime: Infinity });
}

export function useFiles(condominiumId: string, category?: FileCategory) {
  const filter = category ? `?category=${category}` : "";
  return useQuery({
    queryKey: ["files", condominiumId, category ?? "all"],
    queryFn: async () => (await getJson<SourceFileSummary[]>(`/condominiums/${condominiumId}/files${filter}`)) ?? [],
    // While any file is queued or processing, refresh the list every 3 seconds
    refetchInterval: (query) => (query.state.data?.some(inProgress) ? 3000 : false),
  });
}

export function useLatestFile(condominiumId: string) {
  return useQuery({
    queryKey: ["files", condominiumId, "latest"],
    queryFn: () => getJson<SourceFileSummary>(`/condominiums/${condominiumId}/files/latest`),
    refetchInterval: (query) => (query.state.data && inProgress(query.state.data) ? 3000 : false),
  });
}

export function useFileDetail(condominiumId: string, id: string | null) {
  return useQuery({
    queryKey: ["file", condominiumId, id],
    queryFn: () => getJson<SourceFileDetail>(`/condominiums/${condominiumId}/files/${id}`),
    enabled: id !== null,
    refetchInterval: (query) => (query.state.data && inProgress(query.state.data.file) ? 3000 : false),
  });
}

export function useDashboard(condominiumId: string) {
  return useQuery({
    queryKey: ["dashboard", condominiumId],
    queryFn: () => getJson<Dashboard>(`/condominiums/${condominiumId}/dashboard`),
  });
}

/** After uploading, reprocessing or changing the category, everything that depends on files is reloaded. */
function useReloadFiles() {
  const client = useQueryClient();
  return () => {
    void client.invalidateQueries({ queryKey: ["files"] });
    void client.invalidateQueries({ queryKey: ["file"] });
    void client.invalidateQueries({ queryKey: ["dashboard"] });
    void client.invalidateQueries({ queryKey: ["monthly-evolution"] });
    void client.invalidateQueries({ queryKey: ["utility-accounts"] });
  };
}

export function useUploadFile(condominiumId: string) {
  const reload = useReloadFiles();
  return useMutation({
    mutationFn: ({ category, file }: { category: FileCategory; file: File }) => {
      const body = new FormData();
      body.append("file", file);
      return postForm<SourceFileSummary>(`/condominiums/${condominiumId}/files?category=${category}`, body);
    },
    onSuccess: reload,
  });
}

export function useReprocess(condominiumId: string) {
  const reload = useReloadFiles();
  return useMutation({
    mutationFn: (id: string) => postForm<SourceFileSummary>(`/condominiums/${condominiumId}/files/${id}/reprocess`),
    onSuccess: reload,
  });
}

/** Changes the category; the backend reprocesses the file with the new one (RF-01.7). */
export function useChangeCategory(condominiumId: string) {
  const reload = useReloadFiles();
  return useMutation({
    mutationFn: ({ id, category }: { id: string; category: FileCategory }) =>
      putJson<SourceFileSummary>(`/condominiums/${condominiumId}/files/${id}/category`, { category } satisfies CategoryChange),
    onSuccess: reload,
  });
}

export function useConfirmOperatingFund(condominiumId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (fundId: string) => put(`/condominiums/${condominiumId}/operating-fund`, { fundId }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["dashboard", condominiumId] });
      void client.invalidateQueries({ queryKey: ["monthly-evolution", condominiumId] });
    },
  });
}

// ---- Month-by-month evolution (RF-12) ----

/** The 12 months up to the last month with a cash flow. Null while no cash flow was read (204). */
export function useMonthlyEvolution(condominiumId: string) {
  return useQuery({
    queryKey: ["monthly-evolution", condominiumId],
    queryFn: () => getJson<MonthlyEvolution>(`/condominiums/${condominiumId}/dashboard/monthly-evolution`),
  });
}

export function useUtilityAccounts(condominiumId: string) {
  return useQuery({
    queryKey: ["utility-accounts", condominiumId],
    queryFn: () => getJson<UtilityAccount[]>(`/condominiums/${condominiumId}/utility-accounts`),
  });
}

/** Confirms the account as a utility, or rejects it with null (RF-12.1). The charts reload. */
export function useDecideUtilityAccount(condominiumId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ accountCode, utilityType }: { accountCode: string; utilityType: UtilityType | null }) =>
      put(`/condominiums/${condominiumId}/utility-accounts/${encodeURIComponent(accountCode)}`, { utilityType }),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["utility-accounts", condominiumId] });
      void client.invalidateQueries({ queryKey: ["monthly-evolution", condominiumId] });
    },
  });
}

// ---- Contractable features (RF-10) and usage (RF-09.7) ----

/** Features enabled in the condominium. Used to show or hide menus (the backend is the one that blocks). */
export function useCondominiumContext(condominiumId: string) {
  return useQuery({
    queryKey: ["context", condominiumId],
    queryFn: () => getJson<CondominiumContext>(`/condominiums/${condominiumId}/context`),
  });
}

export function useFeatures(condominiumId: string) {
  return useQuery({
    queryKey: ["features", condominiumId],
    queryFn: async () => (await getJson<CondominiumFeature[]>(`/condominiums/${condominiumId}/features`)) ?? [],
  });
}

/** Enable/disable trail of a feature (ADMIN only). */
export function useFeatureEvents(condominiumId: string, code: string) {
  return useQuery({
    queryKey: ["features", condominiumId, code, "events"],
    queryFn: async () => (await getJson<FeatureEvent[]>(`/condominiums/${condominiumId}/features/${code}/events`)) ?? [],
  });
}

/** Active periods, calculated by the backend from the trail (ADMIN only). */
export function useFeaturePeriods(condominiumId: string, code: string) {
  return useQuery({
    queryKey: ["features", condominiumId, code, "periods"],
    queryFn: async () => (await getJson<ActivePeriod[]>(`/condominiums/${condominiumId}/features/${code}/periods`)) ?? [],
  });
}

/** Enables or disables a feature (ADMIN only). Enabling the Assistant puts the files in the indexing queue. */
export function useChangeFeature(condominiumId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: ({ code, change }: { code: string; change: FeatureChange }) =>
      putJson<CondominiumFeature>(`/condominiums/${condominiumId}/features/${code}`, change),
    onSuccess: () => {
      void client.invalidateQueries({ queryKey: ["features", condominiumId] });
      void client.invalidateQueries({ queryKey: ["context", condominiumId] });
      void client.invalidateQueries({ queryKey: ["files", condominiumId] });
      void client.invalidateQueries({ queryKey: ["file", condominiumId] });
    },
  });
}

/** Path of the usage in the period; the export uses the same filter. Dates as YYYY-MM-DD. */
export const usagePath = (condominiumId: string, start: string, end: string, isExport = false) =>
  `/condominiums/${condominiumId}/usage${isExport ? "/export" : ""}?start=${start}&end=${end}`;

/** Only asks the API with both dates and the start before the end (YYYY-MM-DD compares as text). */
export const isPeriodFilled = (start: string, end: string) => start !== "" && end !== "" && start <= end;

export function useUsage(condominiumId: string, start: string, end: string) {
  return useQuery({
    queryKey: ["usage", condominiumId, start, end],
    queryFn: () => getJson<PeriodUsage>(usagePath(condominiumId, start, end)),
    enabled: isPeriodFilled(start, end),
  });
}

// ---- AI configuration (RF-09.6) and Assistant (RF-04) ----

/** The condominium's AI configuration (ADMIN only). The key never comes back: only "registered" and its last 4 characters. */
export function useAiConfiguration(condominiumId: string) {
  return useQuery({
    queryKey: ["ai", condominiumId],
    queryFn: () => getJson<AiConfiguration>(`/condominiums/${condominiumId}/ai`),
  });
}

/** Catalog of providers and models, read from rag by the backend (ADMIN only). */
export function useAiProviders() {
  return useQuery({
    queryKey: ["ai", "providers"],
    queryFn: async () => (await getJson<AiProvider[]>("/ai/providers")) ?? [],
    staleTime: 5 * 60_000,
  });
}

/**
 * Saves the AI configuration. Then reloads the context: the menu and the "Assistente" screen start reflecting
 * the new mode without reloading the page (RF-04.16, last criterion).
 */
export function useSaveAiConfiguration(condominiumId: string) {
  const client = useQueryClient();
  return useMutation({
    mutationFn: (request: AiConfigurationRequest) => putJson<AiConfiguration>(`/condominiums/${condominiumId}/ai`, request),
    onSuccess: (configuration) => {
      client.setQueryData(["ai", condominiumId], configuration);
      void client.invalidateQueries({ queryKey: ["context", condominiumId] });
    },
  });
}

/** Keyword search in the documents, without AI (RF-04.18). It is a user action, hence a mutation and not a query. */
export function useSearchDocuments(condominiumId: string) {
  return useMutation({
    mutationFn: async (request: DocumentSearchRequest) =>
      (await postJson<DocumentChunk[]>(`/condominiums/${condominiumId}/assistant/search`, request)) ?? [],
  });
}
