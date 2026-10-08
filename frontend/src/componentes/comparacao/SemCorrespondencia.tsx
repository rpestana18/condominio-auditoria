import type { ExercicioComparado, LinhaSemCorrespondencia } from "../../api/tipos";
import type { AbridorEvidencia } from "./navegacao";
import { ValorOuTraco } from "./ValorOuTraco";

interface Props {
  exercicios: ExercicioComparado[];
  linhas: LinhaSemCorrespondencia[];
  abridor: AbridorEvidencia;
}

/**
 * Bloco "sem correspondência" do RF-11.6 (visão 3): linhas sem rubrica confirmada, cada uma com o valor
 * do seu exercício. Nunca são somadas a outra linha; a correspondência é feita pelo Admin na tela da PO.
 */
export function SemCorrespondencia({ exercicios, linhas, abridor }: Props) {
  const rotulo = (exercicioId: string) => exercicios.find((e) => e.id === exercicioId)?.rotulo ?? exercicioId;
  return (
    <section className="bloco">
      <h2>Sem correspondência ({linhas.length})</h2>
      <p className="discreto">
        Linhas sem rubrica confirmada. Aparecem com o valor do próprio exercício e não entram na comparação por linha.
      </p>
      {linhas.length === 0 ? (
        <p className="aviso">Todas as linhas têm correspondência confirmada.</p>
      ) : (
        <div className="rolagem">
          <table className="tabela compacta">
            <thead>
              <tr>
                <th>Exercício</th>
                <th>Código</th>
                <th>Conta da PO</th>
                <th>Descrição</th>
                <th>Grupo</th>
                <th className="numero">Previsto do mês</th>
                <th className="numero">Previsto</th>
                <th className="numero">Realizado</th>
              </tr>
            </thead>
            <tbody>
              {linhas.map((l) => {
                const abrir = abridor(l.exercicioId, l.alvo);
                return (
                  <tr key={`${l.exercicioId}-${l.linhaId}`}>
                    <td>{rotulo(l.exercicioId)}</td>
                    <td>{l.codigo}</td>
                    <td className="discreto">{l.conta ?? "—"}</td>
                    <td>{l.descricao}</td>
                    <td className="discreto">{l.grupo ?? "—"}</td>
                    <td className="numero">
                      <ValorOuTraco valor={l.previstoMes} aoAbrir={abrir} />
                    </td>
                    <td className="numero">
                      <ValorOuTraco valor={l.previsto} />
                    </td>
                    <td className="numero">
                      <ValorOuTraco valor={l.realizado} aoAbrir={abrir} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
