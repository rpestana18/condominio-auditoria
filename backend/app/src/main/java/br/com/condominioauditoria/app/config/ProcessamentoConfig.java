package br.com.condominioauditoria.app.config;

import br.com.condominioauditoria.ingestao.contrato.ContratoLeitor;
import br.com.condominioauditoria.ingestao.fluxo.InterpretadorFluxoCaixa;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Processamento em segundo plano com @Async. Se o servidor cair no meio, nada se perde: o original está na pasta
 * e a recuperação na subida coloca o arquivo de volta na fila.
 */
@Configuration
@EnableAsync
class ProcessamentoConfig {

    public static final String EXECUTOR = "processamentoExecutor";

    @Bean(EXECUTOR)
    ThreadPoolTaskExecutor processamentoExecutor(PropriedadesCondominio propriedades) {
        var executor = new ThreadPoolTaskExecutor();
        // Limite de threads: um upload grande não trava o resto do sistema
        executor.setCorePoolSize(propriedades.processamento().threads());
        executor.setMaxPoolSize(propriedades.processamento().threads());
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("processamento-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean
    ContratoLeitor contratoLeitor() {
        return new ContratoLeitor();
    }

    @Bean
    InterpretadorFluxoCaixa interpretadorFluxoCaixa() {
        return new InterpretadorFluxoCaixa();
    }
}
