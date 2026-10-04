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
  const abrirARealocar = () => aoAbrirEvidencia({ alvo: "A_REALOCAR", titulo: "A realocar" });
  return (
    <div className="duas-colunas">
      <Bloco
        titulo="Sem linha da PO"
        dica="Contas do fluxo sem de-para confirmado. Entram na despesa realizada, mas em nenhuma linha da PO."
        bloco={resultado.semLinhaPo}
        aoAbrir={() => aoAbrirEvidencia({ alvo: "SEM_LINHA_PO", titulo: "Sem linha da PO" })}
      />
      <Bloco
        titulo="A realocar"
        dica="Compras por meio de pagamento (RF-02B) que ainda não foram realocadas para uma linha da PO."
        bloco={resultado.aRealocar}
        aoAbrir={abrirARealocar}
      >
        {/* Gestor e Admin escolhem a linha de cada compra na evidência; o backend barra os demais */}
        {pode("GESTOR", "ADMIN") && (resultado.aRealocar?.total ?? 0) !== 0 && (
          <button className="botao secundario" onClick={abrirARealocar}>
            Realocar compras
          </button>
        )}
        {resultado.po && <ListaRealocacoes poId={resultado.po.id} />}
      </Bloco>
      <Bloco
        titulo="Ajustes (não são despesa)"
        dica="Estornos e repasses: ficam fora da despesa realizada."
        bloco={resultado.ajustes}
        aoAbrir={() => aoAbrirEvidencia({ alvo: "AJUSTES", titulo: "Ajustes (não são despesa)" })}
      />
      {resultado.conferencia && <Conferencia conferencia={resultado.conferencia} aoAbrirEvidencia={aoAbrirEvidencia} />}
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
      {bloco && bloco.contas.length > 0 && (
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
            {bloco.contas.map((c, i) => (
              <tr key={`${c.conta}-${i}`}>
                <td>
                  {c.conta} {c.nome}
                </td>
                <td className="discreto">{c.detalhe}</td>
                <td className="numero">{c.lancamentos}</td>
                <td className="numero">{formatarMoeda(c.valor)}</td>
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
        <span className={conferencia.confere ? "selo ok" : "selo alerta"}>{conferencia.confere ? "Confere" : "Não confere"}</span>
      </header>
      <dl className="lista-numeros">
        <dt>Débitos do fundo no fluxo</dt>
        <dd>
          {formatarMoeda(conferencia.debitosDoFundo)} ({conferencia.lancamentos} lançamentos)
        </dd>
        <dt>Despesa realizada</dt>
        <dd>{formatarMoeda(conferencia.despesaRealizada)}</dd>
        <dt>Ajustes</dt>
        <dd>
          <ValorComFonte
            valor={conferencia.ajustes}
            aoAbrir={() => aoAbrirEvidencia({ alvo: "AJUSTES", titulo: "Ajustes (não são despesa)" })}
          />
        </dd>
        <dt>Transferências entre fundos</dt>
        <dd>
          <ValorComFonte
            valor={conferencia.transferencias}
            aoAbrir={() => aoAbrirEvidencia({ alvo: "TRANSFERENCIAS", titulo: "Transferências entre fundos" })}
          />
        </dd>
      </dl>
    </section>
  );
}
