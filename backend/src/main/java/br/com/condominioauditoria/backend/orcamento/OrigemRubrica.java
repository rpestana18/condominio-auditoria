package br.com.condominioauditoria.backend.orcamento;

/**
 * De onde veio a rubrica da linha (ADR 0005, Decisão 1): a primeira PO confirmada do condomínio (a linha vira rubrica),
 * a mesma conta da PO no mesmo grupo, a linha igual da versão anterior do mesmo exercício ou a escolha do Admin.
 */
public enum OrigemRubrica {
    PRIMEIRA_PO, CONTA_PO, VERSAO_ANTERIOR, MANUAL
}
