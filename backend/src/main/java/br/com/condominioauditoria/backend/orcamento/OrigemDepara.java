package br.com.condominioauditoria.backend.orcamento;

/**
 * De onde veio o destino (ADR 0004, Decisão 4): cópia da versão anterior da PO, planilha carregada pelo Admin,
 * comparação de nomes (sem IA) ou escolha do Admin na lista de linhas.
 */
public enum OrigemDepara {
    VERSAO_ANTERIOR, PLANILHA, NOME, ADMIN
}
