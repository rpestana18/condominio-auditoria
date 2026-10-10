import type { StatusArquivo as Status } from "../api/tipos";

const rotulos: Record<Status, string> = {
  PENDING: "Na fila",
  PROCESSING: "Processando",
  COMPLETED: "Concluído",
  NEEDS_REVIEW: "Precisa revisão",
  FAILED: "Falhou",
};

export function StatusArquivo({ status }: { status: Status }) {
  return <span className={`status status-${status.toLowerCase()}`}>{rotulos[status]}</span>;
}
