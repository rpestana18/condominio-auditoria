import { useState } from "react";
import { baixarArquivo } from "../../api/cliente";
import { caminhoExportacao, type FormatoExportacao, type Periodo } from "../../api/consultasOrcamento";
import { useSessao } from "../../contexto";

interface Props {
  periodo: Periodo;
  poId: string | null;
  /** Mesmo filtro de fundo da tela; sem ele, todos os fundos. */
  fundoId: string | null;
}

/**
 * Exportação PDF e Excel da visão escolhida (RF-03.1.14). Só acontece quando a pessoa clica:
 * o arquivo é gerado no backend a partir do mesmo cálculo da tela.
 */
export function BotoesExportacao({ periodo, poId, fundoId }: Props) {
  const { condominioId } = useSessao();
  const [gerando, setGerando] = useState<FormatoExportacao | null>(null);
  const [erro, setErro] = useState<string | null>(null);

  const exportar = async (formato: FormatoExportacao) => {
    setGerando(formato);
    setErro(null);
    try {
      await baixarArquivo(caminhoExportacao(condominioId, formato, periodo, poId, fundoId), `previsto-realizado-${periodo}.${formato}`);
    } catch (e) {
      setErro(e instanceof Error ? e.message : "Não foi possível exportar");
    } finally {
      setGerando(null);
    }
  };

  return (
    <div className="acoes-exportacao">
      <button className="botao secundario" disabled={gerando !== null} onClick={() => void exportar("pdf")}>
        {gerando === "pdf" ? "Gerando PDF…" : "Exportar PDF"}
      </button>
      <button className="botao secundario" disabled={gerando !== null} onClick={() => void exportar("xlsx")}>
        {gerando === "xlsx" ? "Gerando Excel…" : "Exportar Excel"}
      </button>
      {erro && <span className="aviso erro">{erro}</span>}
    </div>
  );
}
