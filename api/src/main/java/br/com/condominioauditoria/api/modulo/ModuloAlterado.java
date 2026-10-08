package br.com.condominioauditoria.api.modulo;

import java.util.UUID;

/**
 * Publicado dentro da transação que liga ou desliga um módulo. Quem precisa reagir (ex.: reindexar ao ligar o
 * Assistente, RF-10.4) escuta este evento sem que o pacote de módulos conheça os arquivos.
 */
public record ModuloAlterado(UUID condominioId, String modulo, boolean ligado, String usuario) {
}
