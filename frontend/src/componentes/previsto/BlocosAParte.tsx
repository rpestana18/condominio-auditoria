import type { ReactNode } from "react";
import { useSessao } from "../../contexto";
import type { BlocoPrevistoRealizado, ConferenciaFluxo, PrevistoRealizado } from "../../api/tipos";
import { formatarMoeda } from "../../formato";
import type { AbrirEvidencia } from "./evidencia";
import { ListaRealocacoes } from "./Realocacao";
import { ValorComFonte } from "./ValorComFonte";

interface Props {
  resultado: PrevistoRealizado;
  aoAbrirEvidencia: AbrirEvidencia;
}

/**
 * Blocos fora das linhas da PO (RF-03.1.6 e RF-03.1.13): sem linha da PO, a realocar, ajustes e a
 * conferência com o fluxo.
 */
export function BlocosAParte({ resultado, aoAbrirEvidencia }: Props) {
  const { pode } = useSessao();
  const abrirARealocar = () => aoAbrirEvidencia({ alvo: "TO_REALLOCATE", titulo: "A realocar" });
  return (
    <div className="duas-colunas">
      <Bloco
        titulo="Sem linha da PO"
        dica="Contas do fluxo sem de-para confirmado. Entram na despesa realizada, mas em nenhuma linha da PO."
        bloco={resultado.withoutBudgetLine}
        aoAbrir={() => aoAbrirEvidencia({ alvo: "NO_BUDGET_LINE", titulo: "Sem linha da PO" })}
      />
      <Bloco
        titulo="A realocar"
        dica="Compras por meio de pagamento (RF-02B) que ainda não foram realocadas para uma linha da PO."
        bloco={resultado.toReallocate}
        aoAbrir={abrirARealocar}
      >
        {/* Gestor e Admin escolhem a linha de cada compra na evidência; o backend barra os demais */}
        {pode("GESTOR", "ADMIN") && (resultado.toReallocate?.total ?? 0) !== 0 && (
          <button className="botao secundario" onClick={abrirARealocar}>
            Realocar compras
          </button>
        )}
        {resultado.budget && <ListaRealocacoes poId={resultado.budget.id} />}
      </Bloco>
      <Bloco
        titulo="Ajustes (não são despesa)"
        dica="Estornos e repasses: ficam fora da despesa realizada."
        bloco={resultado.adjustments}
        aoAbrir={() => aoAbrirEvidencia({ alvo: "ADJUSTMENTS", titulo: "Ajustes (não são despesa)" })}
      />
      {resultado.cashFlowCheck && <Conferencia conferencia={resultado.cashFlowCheck} aoAbrirEvidencia={aoAbrirEvidencia} />}
    </div>
  );
}

interface PropsBloco {
  titulo: string;
  dica: string;
  bloco: BlocoPrevistoRealizado | null | undefined;
  aoAbrir: () => void;
  children?: ReactNode;
}

function Bloco({ titulo, dica, bloco, aoAbrir, children }: PropsBloco) {
  return (
    <section className="bloco">
      <header className="titulo-bloco">
        <h2 title={dica}>{titulo}</h2>
        {bloco ? <ValorComFonte valor={bloco.total} aoAbrir={aoAbrir} className="destaque" /> : <strong>{formatarMoeda(0)}</strong>}
      </header>
      <p className="discreto">{dica}</p>
      {bloco && bloco.accounts.length > 0 && (
        <table className="tabela compacta">
          <thead>
            <tr>
              <th>Conta do fluxo</th>
              <th>Situação</th>
              <th className="numero">Lançamentos</th>
              <th className="numero">Valor</th>
            </tr>
          </thead>
          <tbody>
            {bloco.accounts.map((c, i) => (
              <tr key={`${c.account}-${i}`}>
                <td>
                  {c.account} {c.name}
                </td>
                <td className="discreto">{c.detail}</td>
                <td className="numero">{c.entries}</td>
                <td className="numero">{formatarMoeda(c.amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {children}
    </section>
  );
}

function Conferencia({ conferencia, aoAbrirEvidencia }: { conferencia: ConferenciaFluxo; aoAbrirEvidencia: AbrirEvidencia }) {
  return (
    <section className="bloco">
      <header className="titulo-bloco">
        <h2>Conferência com o fluxo</h2>
        <span className={conferencia.matches ? "selo ok" : "selo alerta"}>{conferencia.matches ? "Confere" : "Não confere"}</span>
      </header>
      <dl className="lista-numeros">
        <dt>Débitos do fundo no fluxo</dt>
        <dd>
          {formatarMoeda(conferencia.fundDebits)} ({conferencia.entries} lançamentos)
        </dd>
        <dt>Despesa realizada</dt>
        <dd>{formatarMoeda(conferencia.actualExpense)}</dd>
        <dt>Ajustes</dt>
        <dd>
          <ValorComFonte
            valor={conferencia.adjustments}
            aoAbrir={() => aoAbrirEvidencia({ alvo: "ADJUSTMENTS", titulo: "Ajustes (não são despesa)" })}
          />
        </dd>
        <dt>Transferências entre fundos</dt>
        <dd>
          <ValorComFonte
            valor={conferencia.transfers}
            aoAbrir={() => aoAbrirEvidencia({ alvo: "TRANSFERS", titulo: "Transferências entre fundos" })}
          />
        </dd>
      </dl>
    </section>
  );
}
