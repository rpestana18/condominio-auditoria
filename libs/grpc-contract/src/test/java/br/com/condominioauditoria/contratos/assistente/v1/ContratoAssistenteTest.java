package br.com.condominioauditoria.contratos.assistente.v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import io.grpc.MethodDescriptor.MethodType;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Guarda de compatibilidade de contracts/grpc/assistente/v1. A v1 só aceita acréscimo: rpcs, mensagens e campos
 * novos. Se este teste falhar porque um número ou tipo de campo existente mudou, a mudança exige v2 (CLAUDE.md).
 */
class ContratoAssistenteTest {

    @Test
    void rpcsDoServico() {
        assertThat(AssistenteGrpc.getBuscarMethod().getType()).isEqualTo(MethodType.UNARY);
        assertThat(AssistenteGrpc.getPerguntarMethod().getType()).isEqualTo(MethodType.SERVER_STREAMING);
        assertThat(AssistenteGrpc.getListarProvedoresMethod().getType()).isEqualTo(MethodType.UNARY);
        assertThat(AssistenteGrpc.getServiceDescriptor().getMethods()).hasSize(3);
    }

    @Test
    void camposDaEntregaUmNaoMudam() {
        assertThat(campos(BuscarRequest.getDescriptor())).containsExactly(
                Map.entry("condominio_id", 1), Map.entry("texto", 2), Map.entry("modo", 3),
                Map.entry("filtros", 4), Map.entry("limite", 5), Map.entry("modelo_embeddings", 6));
        assertThat(campos(FiltrosBusca.getDescriptor())).containsExactly(
                Map.entry("categorias", 1), Map.entry("data_inicio", 2), Map.entry("data_fim", 3),
                Map.entry("arquivo_ids", 4));
        assertThat(campos(BuscarResponse.getDescriptor())).containsExactly(
                Map.entry("trechos", 1), Map.entry("modo_usado", 2));
        assertThat(campos(Trecho.getDescriptor())).containsExactly(
                Map.entry("trecho_id", 1), Map.entry("arquivo_id", 2), Map.entry("nome_arquivo", 3),
                Map.entry("categoria", 4), Map.entry("localizacao", 5), Map.entry("texto", 6),
                Map.entry("pontuacao", 7), Map.entry("sha256", 8));
        assertThat(campos(Localizacao.getDescriptor())).containsExactly(
                Map.entry("pagina", 1), Map.entry("planilha", 2), Map.entry("paragrafos", 3));
    }

    @Test
    void camposDaEntregaTres() {
        assertThat(campos(PerguntarRequest.getDescriptor())).containsExactly(
                Map.entry("condominio_id", 1), Map.entry("pergunta", 2), Map.entry("historico", 3),
                Map.entry("filtros", 4), Map.entry("configuracao", 5), Map.entry("limite_trechos", 6));
        assertThat(PerguntarRequest.getDescriptor().findFieldByName("filtros").getMessageType())
                .isEqualTo(FiltrosBusca.getDescriptor());
        assertThat(campos(ConfiguracaoPergunta.getDescriptor())).containsExactly(
                Map.entry("provedor", 1), Map.entry("modelo", 2), Map.entry("chave_cifrada", 3),
                Map.entry("modelo_embeddings", 4), Map.entry("modo_busca", 5));
        assertThat(ConfiguracaoPergunta.getDescriptor().findFieldByName("chave_cifrada").getType())
                .isEqualTo(FieldDescriptor.Type.BYTES);

        Descriptor evento = PerguntarEvento.getDescriptor();
        assertThat(evento.getOneofs()).hasSize(1);
        assertThat(evento.getOneofs().getFirst().getFields()).extracting(FieldDescriptor::getName)
                .containsExactly("andamento", "resposta");

        assertThat(campos(RespostaPergunta.getDescriptor())).containsExactly(
                Map.entry("situacao", 1), Map.entry("nos_documentos", 2), Map.entry("nos_dados_gravados", 3),
                Map.entry("trechos_citados", 4), Map.entry("sugestao", 5), Map.entry("aviso", 6),
                Map.entry("uso", 7));
        assertThat(RespostaPergunta.getDescriptor().findFieldByName("trechos_citados").getMessageType())
                .isEqualTo(Trecho.getDescriptor());
        assertThat(campos(UsoPergunta.getDescriptor())).containsExactly(
                Map.entry("tokens_entrada", 1), Map.entry("tokens_saida", 2), Map.entry("provedor", 3),
                Map.entry("modelo", 4), Map.entry("versao_prompt", 5), Map.entry("tentativas", 6));
        assertThat(campos(ListarProvedoresResponse.getDescriptor())).containsExactly(
                Map.entry("provedores", 1), Map.entry("chave_publica_pem", 2));
        assertThat(campos(ModeloProvedor.getDescriptor())).containsExactly(
                Map.entry("id", 1), Map.entry("nome", 2), Map.entry("padrao", 3),
                Map.entry("preco_entrada_milhao_usd", 4), Map.entry("preco_saida_milhao_usd", 5));
    }

    @Test
    void precoEDinheiroNuncaSaoPontoFlutuante() {
        // Preços e valores formatados são texto; o único double do contrato é Trecho.pontuacao (relevância).
        for (Descriptor mensagem : AssistenteProto.getDescriptor().getMessageTypes()) {
            for (FieldDescriptor campo : mensagem.getFields()) {
                if (campo.getJavaType() == FieldDescriptor.JavaType.DOUBLE
                        || campo.getJavaType() == FieldDescriptor.JavaType.FLOAT) {
                    assertThat(mensagem.getName() + "." + campo.getName()).isEqualTo("Trecho.pontuacao");
                }
            }
        }
    }

    private static Map<String, Integer> campos(Descriptor descritor) {
        Map<String, Integer> campos = new LinkedHashMap<>();
        descritor.getFields().forEach(campo -> campos.put(campo.getName(), campo.getNumber()));
        return campos;
    }
}
