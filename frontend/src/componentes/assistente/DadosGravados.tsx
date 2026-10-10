import { Link } from "react-router";
import type { DadoGravado } from "../../api/tipos";

/** Nome legível de cada consulta numérica (as ferramentas que o rag chama no backend). */
const nomesConsultas: Record<string, string> = {
  fund_summary: "Resumo dos fundos",
  find_entries: "Lançamentos",
  list_files: "Arquivos enviados",
  file_checks: "Conferências do arquivo",
};

/** Nome legível de cada filtro das consultas (o rag manda os nomes do contrato, em inglês). */
const nomesParametros: Record<string, string> = {
  dateFrom: "data inicial",
  dateTo: "data final",
  fund: "fundo",
  text: "texto",
  outflowsOnly: "só saídas",
  limit: "limite",
  category: "categoria",
  fileId: "arquivo",
};

const valorParametro = (dado: DadoGravado, ...nomes: string[]) =>
  dado.parameters.find((p) => nomes.includes(p.name))?.value;

/**
 * Tela onde a pessoa confere o número (RF-04.14). Só rotas que existem hoje; sem tela, sem link.
 * Lançamentos ainda não têm tela própria: aparece só o nome da consulta.
 */
function linkDaConsulta(dado: DadoGravado): { para: string; rotulo: string } | null {
  switch (dado.query) {
    case "fund_summary":
      return { para: "/", rotulo: "Ver saldo por fundo no Início" };
    case "list_files":
      return { para: "/arquivos", rotulo: "Ver em Arquivos" };
    case "file_checks": {
      const arquivoId = valorParametro(dado, "fileId");
      return { para: arquivoId ? `/arquivos?arquivo=${encodeURIComponent(arquivoId)}` : "/arquivos", rotulo: "Ver conferências do arquivo" };
    }
    default:
      return null;
  }
}

/** Bloco "Nos dados gravados": números vindos das consultas ao banco, já formatados pela API (nada é calculado aqui). */
export function DadosGravados({ dados }: { dados: DadoGravado[] }) {
  return (
    <section className="bloco-resposta" aria-label="Nos dados gravados">
      <h3>Nos dados gravados</h3>
      {dados.map((dado, i) => {
        const link = linkDaConsulta(dado);
        return (
          <div key={i} className="dado-gravado">
            <p className="discreto">
              Consulta: <strong>{nomesConsultas[dado.query] ?? dado.query}</strong>
              {dado.parameters.length > 0 && ` (${dado.parameters.map((p) => `${nomesParametros[p.name] ?? p.name}: ${p.value}`).join("; ")})`}
            </p>
            {dado.rows.length > 0 && (
              <table className="tabela compacta">
                <tbody>
                  {dado.rows.map((l, j) => (
                    <tr key={j}>
                      <td>{l.label}</td>
                      <td className="numero">{l.value}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            {dado.comment && <p>{dado.comment}</p>}
            {link && (
              <Link to={link.para} className="botao-link">
                {link.rotulo}
              </Link>
            )}
          </div>
        );
      })}
    </section>
  );
}
