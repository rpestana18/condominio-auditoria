import type { StatusArquivo as Status } from "../api/tipos";

const rotulos: Record<Status, string> = {
  PENDENTE: "Na fila",
  PROCESSANDO: "Processando",
  CONCLUIDO: "Concluído",
  PRECISA_REVISAO: "Precisa revisão",
  FALHOU: "Falhou",
};

export function StatusArquivo({ status }: { status: Status }) {
  return <span className={`status status-${status.toLowerCase()}`}>{rotulos[status]}</span>;
}
