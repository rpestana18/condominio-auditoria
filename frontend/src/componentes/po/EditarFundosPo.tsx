import { useState } from "react";
import { ErroApi } from "../../api/cliente";
import { useAlterarFundosPo, useFundos } from "../../api/consultasOrcamento";
import type { PrevisaoDetalhe } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarMoeda } from "../../formato";

/**
 * Admin altera, depois da confirmação, qual fundo do fluxo atende cada linha 1.9.x (RF-03.1.9).
 * Envia a ligação completa; a trilha da PO registra o fundo anterior e o novo. O backend recusa
 * fundo repetido e o fundo ordinário.
 */
export function EditarFundosPo({ detalhe }: { detalhe: PrevisaoDetalhe }) {
  const { condominioId } = useSessao();
  const { data: fundos = [] } = useFundos(condominioId);
  const alterar = useAlterarFundosPo(condominioId, detalhe.budget.id);
  const linhasDeFundo = detalhe.lines.filter((l) => l.fundLine && l.type === "LINE");
  const atuais = Object.fromEntries(detalhe.funds.map((f) => [f.lineId, f.fundId]));
  const [escolhidos, setEscolhidos] = useState<Record<string, string>>(atuais);
  const mudou = linhasDeFundo.some((l) => (escolhidos[l.id] ?? "") !== (atuais[l.id] ?? ""));
  const motivos = alterar.error instanceof ErroApi ? (alterar.error.problema?.reasons ?? []) : [];

  return (
    <section className="bloco formulario">
      <h2>Fundos ligados às linhas de fundos</h2>
      <p className="discreto">Nomes como impressos no fluxo. Cada fundo atende no máximo uma linha.</p>
      <fieldset>
        {linhasDeFundo.map((l) => (
          <label key={l.id} className="campo">
            {l.effectiveCode} {l.description} · {formatarMoeda(l.budgeted)}/mês
            <select value={escolhidos[l.id] ?? ""} onChange={(e) => setEscolhidos((a) => ({ ...a, [l.id]: e.target.value }))}>
              <option value="">Sem fundo ligado</option>
              {fundos
                .filter((f) => !f.operating)
                .map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.name}
                  </option>
                ))}
            </select>
          </label>
        ))}
      </fieldset>
      {alterar.isError && (
        <div className="aviso erro">
          {alterar.error.message}
          {motivos.length > 0 && (
            <ul>
              {motivos.map((m) => (
                <li key={m}>{m}</li>
              ))}
            </ul>
          )}
        </div>
      )}
      {alterar.isSuccess && !mudou && <p className="aviso ok">Ligação gravada.</p>}
      <button
        className="botao"
        disabled={!mudou || alterar.isPending}
        onClick={() => alterar.mutate(linhasDeFundo.map((l) => ({ linhaId: l.id, fundoId: escolhidos[l.id] || null })))}
      >
        {alterar.isPending ? "Salvando…" : "Salvar ligação dos fundos"}
      </button>
    </section>
  );
}
