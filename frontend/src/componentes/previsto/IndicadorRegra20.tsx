import type { Regra20 } from "../../api/tipos";
import { formatarMoeda, formatarPercentual } from "../../formato";

/**
 * Regra dos 20% da Conv. 16.2 (RF-03.1.11). Mostra o excesso, o percentual, o limite e as linhas
 * que compõem o excesso. "A realocar" e "sem linha da PO" ficam à parte, no cenário máximo.
 */
export function IndicadorRegra20({ regra }: { regra: Regra20 }) {
  // Largura da barra: o percentual do excesso em relação ao limite (só desenho, sem passar de 100%)
  const largura = Math.min(100, (regra.percentual / regra.limitePercentual) * 100);
  return (
    <section className="bloco">
      <header className="titulo-bloco">
        <h2>
          Regra dos {regra.limitePercentual}% (Conv. 16.2)
        </h2>
        <span className={regra.acimaDoLimite ? "selo critico" : "selo ok"}>
          {regra.acimaDoLimite ? "Acima do limite: verificar ata de AGE" : "Dentro do limite"}
        </span>
        {regra.provisorio && <span className="selo alerta">Provisório</span>}
      </header>
      <div className="barra-limite" aria-hidden>
        <div className={regra.acimaDoLimite ? "preenchido critico" : "preenchido"} style={{ width: `${largura}%` }} />
      </div>
      <dl className="lista-numeros">
        <dt>Excesso do mês</dt>
        <dd>
          {formatarMoeda(regra.excesso)} ({formatarPercentual(regra.percentual)} do previsto do mês)
        </dd>
        <dt>Limite</dt>
        <dd>
          {formatarMoeda(regra.limite)} ({regra.limitePercentual}% de {formatarMoeda(regra.previstoMes)})
        </dd>
        <dt>Linhas acima do previsto</dt>
        <dd>{regra.linhasAcima}</dd>
        {(regra.aRealocar > 0 || regra.semLinhaPo > 0) && (
          <>
            <dt>Fora do excesso</dt>
            <dd>
              a realocar {formatarMoeda(regra.aRealocar)} · sem linha da PO {formatarMoeda(regra.semLinhaPo)}
            </dd>
            <dt>Cenário máximo</dt>
            <dd>
              {formatarMoeda(regra.cenarioMaximo)} ({formatarPercentual(regra.percentualCenarioMaximo)})
            </dd>
          </>
        )}
      </dl>
      {regra.linhas.length > 0 && (
        <details>
          <summary>Linhas que compõem o excesso ({regra.linhas.length})</summary>
          <table className="tabela compacta">
            <tbody>
              {regra.linhas.map((l) => (
                <tr key={l.linhaId}>
                  <td>{l.codigo}</td>
                  <td>{l.descricao}</td>
                  <td className="numero">{formatarMoeda(l.excesso)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      )}
    </section>
  );
}
