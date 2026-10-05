package br.com.condominioauditoria.backend.modulo;

/**
 * Quem executou a parte de IA de uma operação (RF-09.6). LOCAL = modelo na infraestrutura do sistema (ex.: embeddings
 * no Ollama); DESLIGADO = sem modelo nenhum (ex.: busca só por palavra).
 */
public enum ModoIa {
    API_KEY, MCP_EXTERNO, LOCAL, DESLIGADO
}
