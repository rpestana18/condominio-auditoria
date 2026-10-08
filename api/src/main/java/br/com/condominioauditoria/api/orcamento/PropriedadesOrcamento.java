package br.com.condominioauditoria.api.orcamento;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parâmetros da leitura da PO (bloco "condominio.orcamento" do application.yml).
 *
 * @param toleranciaArredondamento diferença máxima (em reais) entre o impresso e a soma que conta como arredondamento
 *     na origem: a conferência vira aviso e não deixa a PO "lida com divergência". Os cálculos usam sempre a soma das
 *     linhas. O valor fica só no application.yml (pendente de confirmação do usuário; 0.00 desliga a tolerância).
 */
@ConfigurationProperties(prefix = "condominio.orcamento")
public record PropriedadesOrcamento(BigDecimal toleranciaArredondamento) {

    public PropriedadesOrcamento {
        if (toleranciaArredondamento == null || toleranciaArredondamento.signum() < 0) {
            throw new IllegalArgumentException("Informe condominio.orcamento.tolerancia-arredondamento (zero ou mais)");
        }
        toleranciaArredondamento = toleranciaArredondamento.setScale(2, java.math.RoundingMode.UNNECESSARY);
    }
}
