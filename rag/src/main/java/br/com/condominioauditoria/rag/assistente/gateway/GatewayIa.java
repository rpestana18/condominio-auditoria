package br.com.condominioauditoria.rag.assistente.gateway;

import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.Parametros;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.ResultadoFerramenta;
import br.com.condominioauditoria.rag.assistente.gateway.ContratoModelo.Turno;
import br.com.condominioauditoria.rag.config.PropriedadesRag;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Único ponto do sistema que fala com modelo de IA (RF-09.3, "ai-gateway do rag"). Quem chama passa
 * {@link Parametros} com a chave de API já aberta e recebe {@link Turno} neutros; o resto do rag não conhece SDK de
 * provedor nenhum.
 *
 * Na fase de nuvem, é aqui que entra o mascaramento de dado pessoal antes de sair qualquer texto.
 */
@Component
public class GatewayIa {

    private final PropriedadesRag.Assistente config;

    public GatewayIa(PropriedadesRag propriedades) {
        this.config = propriedades.assistente();
    }

    /**
     * Abre uma conversa com o provedor. O cliente HTTP é criado aqui, para esta conversa, com a chave do condomínio;
     * nada é reaproveitado entre condomínios e nada da chave fica em memória depois do {@code close()}.
     */
    public Conversa abrir(Parametros parametros) {
        return switch (parametros.tipoProvedor()) {
            case "anthropic" -> new ConversaAnthropic(parametros, config);
            default -> throw new IllegalStateException(
                    "tipo de provedor de respostas não implementado neste rag: " + parametros.tipoProvedor());
        };
    }

    /** Conversa em andamento com o modelo: o laço de ferramentas vive fora, aqui só vai e volta. */
    public interface Conversa extends AutoCloseable {

        /** Acrescenta uma mensagem do usuário (a pergunta montada, ou o motivo da nova tentativa). */
        void adicionarPergunta(String texto);

        /** Acrescenta uma troca anterior da conversa (histórico do frontend). */
        void adicionarTrocaAnterior(String pergunta, String resposta);

        /** Devolve ao modelo o resultado das ferramentas pedidas no último turno. */
        void adicionarResultados(List<ResultadoFerramenta> resultados);

        /** Uma chamada ao provedor. Lança {@link ContratoModelo.ErroProvedorException} em falha. */
        Turno enviar();

        /** Somado em todas as chamadas desta conversa. */
        long tokensEntrada();

        long tokensSaida();

        @Override
        void close();
    }
}
