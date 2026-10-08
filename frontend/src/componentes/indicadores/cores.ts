/**
 * Cores neutras para várias séries (grupos, exercícios, previsto × arrecadado), definidas em estilos.css
 * com versão para o tema escuro. Nenhuma é vermelha: o vermelho é só do excesso acima de 20% (RF-11.13).
 */
const QUANTIDADE = 8;

export const corDaSerie = (indice: number) => `var(--serie-${(indice % QUANTIDADE) + 1})`;
