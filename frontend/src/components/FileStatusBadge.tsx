import type { FileStatus } from "../api/types";

const labels: Record<FileStatus, string> = {
  PENDING: "Na fila",
  PROCESSING: "Processando",
  COMPLETED: "Concluído",
  NEEDS_REVIEW: "Precisa revisão",
  FAILED: "Falhou",
};

export function FileStatusBadge({ status }: { status: FileStatus }) {
  return <span className={`status status-${status.toLowerCase()}`}>{labels[status]}</span>;
}
