import type { FundoFluxo } from "../../api/tipos";

interface Props {
  /** Fundos do fluxo (GET /fundos). O filtro vai para a API; a tela não separa nada sozinha. */
  fundos: FundoFluxo[];
  fundoId: string | null;
  aoTrocar: (fundoId: string | null) => void;
}

/** Filtro de fundo usado no previsto × realizado e em "Comparar exercícios". */
export function SeletorFundo({ fundos, fundoId, aoTrocar }: Props) {
  // O fundo ordinário (fundo Condomínio) vem primeiro; os demais, na ordem de nome da API
  const ordenados = [...fundos.filter((f) => f.operating), ...fundos.filter((f) => !f.operating)];
  return (
    <label>
      Fundo
      <select value={fundoId ?? ""} onChange={(e) => aoTrocar(e.target.value || null)}>
        <option value="">Todos os fundos</option>
        {ordenados.map((f) => (
          <option key={f.id} value={f.id}>
            {f.operating ? `Fundo Condomínio (${f.name})` : f.name}
          </option>
        ))}
      </select>
    </label>
  );
}
