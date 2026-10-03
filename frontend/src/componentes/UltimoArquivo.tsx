import { Link } from "react-router";
import { useUltimoArquivo } from "../api/consultas";
import { useSessao } from "../contexto";
import { formatarDataHora } from "../formato";

/** Indicador discreto, fixo no canto, em todas as telas. */
export function UltimoArquivo() {
  const { condominioId } = useSessao();
  const { data: ultimo } = useUltimoArquivo(condominioId);
  if (!ultimo) return null;
  return (
    <Link to="/arquivos" className="ultimo-arquivo" title={`Enviado por ${ultimo.enviadoPor}`}>
      Último arquivo: {ultimo.nome} · {ultimo.categoriaRotulo} · {formatarDataHora(ultimo.enviadoEm)}
    </Link>
  );
}
