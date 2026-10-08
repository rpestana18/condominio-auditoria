import { formatarMoedaOuTraco } from "../../formato";
import { ValorComFonte } from "../previsto/ValorComFonte";

interface Props {
  valor: number | null | undefined;
  /** Presente quando o valor tem alvo: o clique abre o previsto × realizado com a evidência. */
  aoAbrir?: () => void;
}

/** Valor da comparação: nulo vira "—" (nunca R$ 0,00); com alvo, vira link para os lançamentos. */
export function ValorOuTraco({ valor, aoAbrir }: Props) {
  if (valor === null || valor === undefined || !aoAbrir) return <>{formatarMoedaOuTraco(valor)}</>;
  return <ValorComFonte valor={valor} aoAbrir={aoAbrir} />;
}
