package br.com.condominioauditoria.backend.grpc;

import br.com.condominioauditoria.backend.config.PropriedadesCondominio;
import br.com.condominioauditoria.contratos.assistente.v1.AssistenteGrpc;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarRequest;
import br.com.condominioauditoria.contratos.assistente.v1.BuscarResponse;
import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Cliente do serviço Assistente do rag (contracts/grpc/assistente/v1). Repassa o token do usuário no metadado
 * "authorization", como o mcp faz com o backend, e toda chamada tem prazo (deadline) configurável.
 */
@Component
class ClienteAssistente {

    private final Channel canal;
    private final long prazoSegundos;

    ClienteAssistente(Channel canalRag, PropriedadesCondominio propriedades) {
        this.canal = canalRag;
        this.prazoSegundos = propriedades.rag().prazoSegundos();
    }

    BuscarResponse buscar(BuscarRequest pedido, String autorizacao) {
        var cabecalhos = new Metadata();
        cabecalhos.put(AutenticacaoGrpc.AUTORIZACAO, autorizacao);
        return AssistenteGrpc.newBlockingStub(canal)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(cabecalhos))
                .withDeadlineAfter(prazoSegundos, TimeUnit.SECONDS)
                .buscar(pedido);
    }
}
