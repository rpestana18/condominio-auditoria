import type { ModoIa } from "../../api/tipos";

/** Modos que o Admin pode escolher nesta fase. LOCAL de respostas está previsto, mas ainda sem provedor (o backend recusa). */
export const descricoesModo: Record<Exclude<ModoIa, "LOCAL">, { rotulo: string; descricao: string }> = {
  EXTERNAL_MCP: {
    rotulo: "Claude do usuário (MCP)",
    descricao: "Cada pessoa usa o próprio Claude conectado ao MCP do sistema. O sistema não chama IA. Padrão do piloto.",
  },
  API_KEY: {
    rotulo: "Chave do condomínio",
    descricao: "O sistema chama o provedor de IA com a chave cadastrada abaixo. O uso fica registrado.",
  },
  OFF: {
    rotulo: "Desligado",
    descricao: "Nenhuma IA de respostas; só regras, cálculos e a busca por palavra.",
  },
};

type Opcao<T extends string> = { valor: T; rotulo: string; descricao: string };

interface Props<T extends string> {
  nome: string;
  legenda: string;
  opcoes: Opcao<T>[];
  valor: T;
  aoMudar: (valor: T) => void;
}

/** Grupo de botões de opção (radio) com descrição curta de cada escolha. */
export function EscolhaModo<T extends string>({ nome, legenda, opcoes, valor, aoMudar }: Props<T>) {
  return (
    <fieldset className="escolha-modo">
      <legend>{legenda}</legend>
      {opcoes.map((o) => (
        <label key={o.valor}>
          <input type="radio" name={nome} value={o.valor} checked={valor === o.valor} onChange={() => aoMudar(o.valor)} />
          <span>
            <strong>{o.rotulo}</strong>
            <small className="discreto bloco-texto">{o.descricao}</small>
          </span>
        </label>
      ))}
    </fieldset>
  );
}

/** As três opções gerais, na ordem da tela. */
export const opcoesModo = (Object.keys(descricoesModo) as Exclude<ModoIa, "LOCAL">[]).map((valor) => ({
  valor,
  ...descricoesModo[valor],
}));
