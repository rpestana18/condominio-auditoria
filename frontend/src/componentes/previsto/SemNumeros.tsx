import { Link } from "react-router";
import type { PrevistoRealizado } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarDataHora, formatarMes, hashCurto } from "../../formato";

/**
 * Quando o backend não calcula (sem PO, PO não confirmada, sem fundo ordinário, sem fluxo ou dois fluxos),
 * a tela mostra o porquê no lugar dos números. Nunca mostra zero.
 */
export function SemNumeros({ resultado }: { resultado: PrevistoRealizado }) {
  const { pode } = useSessao();
  const { situacao, po } = resultado;
  const doisFluxos = resultado.meses.filter((m) => m.situacao === "DOIS_FLUXOS");

  return (
    <section className="bloco sem-numeros">
      <h2>{titulos[situacao]}</h2>
      {resultado.mensagem && <p>{resultado.mensagem}</p>}

      {situacao === "PO_NAO_CONFIRMADA" && po && (
        <Link className={pode("ADMIN") ? "botao" : "botao-link"} to={`/previsoes/${po.id}`}>
          {pode("ADMIN") ? "Conferir e confirmar a PO" : "Ver a PO lida"}
        </Link>
      )}
      {situacao === "SEM_PO" && (
        <Link className="botao-link" to="/previsoes">
          Ver as POs enviadas
        </Link>
      )}
      {situacao === "SEM_FUNDO_ORDINARIO" && pode("GESTOR", "ADMIN") && (
        <Link className="botao-link" to="/">
          Confirmar o fundo ordinário na tela inicial
        </Link>
      )}

      {doisFluxos.map((m) => (
        <div key={m.mes}>
          <h3>Dois fluxos para {formatarMes(m.mes)}</h3>
          <ul className="lista-simples">
            {m.fluxos.map((f) => (
              <li key={f.arquivoId}>
                <strong>{f.nome}</strong>{" "}
                <span className="discreto">
                  {f.enviadoEm && `enviado em ${formatarDataHora(f.enviadoEm)}`} {f.enviadoPor && `por ${f.enviadoPor}`} ·{" "}
                  <span title={f.sha256}>SHA-256 {hashCurto(f.sha256)}…</span>
                </span>
              </li>
            ))}
          </ul>
          <Link className="botao-link" to="/arquivos">
            Ir para Arquivos
          </Link>
        </div>
      ))}
    </section>
  );
}

const titulos: Record<PrevistoRealizado["situacao"], string> = {
  CALCULADO: "",
  SEM_PO: "Sem PO aprovada para este período",
  PO_NAO_CONFIRMADA: "PO não confirmada",
  SEM_FUNDO_ORDINARIO: "Fundo ordinário ainda não confirmado",
  SEM_FLUXO: "Sem fluxo carregado",
  DOIS_FLUXOS: "Dois fluxos para o mesmo mês",
};
