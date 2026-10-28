package br.com.condominioauditoria.rag.service;

import br.com.condominioauditoria.logging.LogContext;
import br.com.condominioauditoria.logging.QueueLogContext;
import br.com.condominioauditoria.rag.client.DocumentReaderClient;
import br.com.condominioauditoria.rag.config.QueueConfig;
import br.com.condominioauditoria.rag.messaging.FileReceivedMessage;
import br.com.condominioauditoria.rag.messaging.MessageContract;
import br.com.condominioauditoria.rag.messaging.ProcessingResultMessage;
import br.com.condominioauditoria.rag.model.budget.Budget;
import br.com.condominioauditoria.rag.model.budget.BudgetLineOrigins;
import br.com.condominioauditoria.rag.model.cashflow.CashFlow;
import br.com.condominioauditoria.rag.model.document.ReadDocument;
import br.com.condominioauditoria.rag.parser.budget.ProtestBudgetParser;
import br.com.condominioauditoria.rag.parser.budget.SupportSheetParser;
import br.com.condominioauditoria.rag.parser.cashflow.CashFlowParser;
import br.com.condominioauditoria.rag.service.calculator.BudgetCheck;
import br.com.condominioauditoria.rag.service.calculator.CashFlowCheck;
import br.com.condominioauditoria.rag.service.calculator.ZeroFundPositions;
import br.com.condominioauditoria.storage.Storage;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

/**
 * Consumes the files received queue. For each file: reports that it started, reads the original, calls the Python
 * reader, parses the layout (Protest cash flow, budget or the budget's support sheet, chosen by the content), checks the sums and returns
 * everything in a single message. The api decides whether the budget read is valid, by the file category (ADR 0004,
 * Decision 3).
 *
 * The message only leaves the queue (ack) when the result has been published. If the rag goes down midway, RabbitMQ
 * delivers the file again; the api discards a repeated or old result by the processamentoId.
 */
@Service
public class FileProcessingService {

    static final String CASH_FLOW_PARSER = "fluxo-caixa-protest";
    static final String BUDGET_PARSER = "po-protest";
    /** Support sheet of the budget (.xlsx; ADR 0009, Decision 3 B). */
    static final String SUPPORT_SHEET_PARSER = "po-planilha-apoio";
    /** Category of the budget file in the api; only used to explain the scanned budget failure. */
    static final String BUDGET_CATEGORY = "PO";

    private static final Logger log = LoggerFactory.getLogger(FileProcessingService.class);

    private final MessageContract contract;
    private final RabbitTemplate rabbit;
    private final Storage storage;
    private final DocumentReaderClient reader;
    private final CashFlowParser cashFlowParser;
    private final ProtestBudgetParser budgetParser;
    private final SupportSheetParser supportSheetParser;

    public FileProcessingService(MessageContract contract, RabbitTemplate rabbit, Storage storage,
            DocumentReaderClient reader, CashFlowParser cashFlowParser,
            ProtestBudgetParser budgetParser, SupportSheetParser supportSheetParser) {
        this.supportSheetParser = supportSheetParser;
        this.contract = contract;
        this.rabbit = rabbit;
        this.storage = storage;
        this.reader = reader;
        this.cashFlowParser = cashFlowParser;
        this.budgetParser = budgetParser;
    }

    /**
     * Logs by fileId, processingId and condominiumId (MDC, with the request id of the AMQP header), never by the
     * original name: it is free text of the user and may carry a name or a CPF (ADR 0008, B1 and E1).
     */
    @RabbitListener(queues = QueueConfig.FILES_RECEIVED)
    public void onReceive(Message message) {
        try (LogContext.Scope ignored = QueueLogContext.enter(message.getBody(),
                message.getMessageProperties().getHeader(LogContext.AMQP_HEADER))) {
            FileReceivedMessage file = contract.readFileReceived(message.getBody());
            publish(ProcessingResultMessage.started(file));
            ProcessingResultMessage result;
            try {
                result = process(file);
                if (result.status() == ProcessingResultMessage.Status.FAILED) {
                    log.warn("Leitura do arquivo {} falhou: {}", file.fileId(), result.reason());
                } else {
                    log.info("Arquivo {} lido ({}, categoria {})", file.fileId(),
                            result.parser() == null ? "layout ainda sem leitor" : result.parser(), file.category());
                }
            } catch (Exception error) {
                result = ProcessingResultMessage.failed(file, readableReason(error));
                // A file problem or a service down is expected (WARN); the reason goes to the api and to the screen
                log.warn("Leitura do arquivo {} falhou: {}", file.fileId(), result.reason(), error);
            }
            publish(result);
        }
    }

    private ProcessingResultMessage process(FileReceivedMessage file) throws Exception {
        byte[] content;
        try (InputStream input = storage.open(file.path())) {
            content = input.readAllBytes();
        }
        return parse(file, reader.read(file.originalName(), content));
    }

    public ProcessingResultMessage parse(FileReceivedMessage file, ReadDocument document) {
        int pages = document.pages().size();
        if (cashFlowParser.recognizes(document)) {
            CashFlow cashFlow = cashFlowParser.parse(document);
            // The checks see the Posição as printed; the message also carries the 0,00 line of the funds where nothing
            // happened, explained by the notice of the FUND_CLOSING_BALANCE check.
            return ProcessingResultMessage.completed(file, CASH_FLOW_PARSER, pages,
                    ZeroFundPositions.complete(cashFlow), CashFlowCheck.check(cashFlow));
        }
        if (budgetParser.recognizes(document)) {
            Budget budget = budgetParser.parse(document);
            return ProcessingResultMessage.completedBudget(file, BUDGET_PARSER, pages, budget,
                    BudgetCheck.check(budget));
        }
        if (supportSheetParser.recognizes(document)) {
            BudgetLineOrigins origins = supportSheetParser.parse(document);
            return ProcessingResultMessage.completedSupportSheet(file, SUPPORT_SHEET_PARSER, pages, origins,
                    SupportSheetParser.check(origins));
        }
        if (BUDGET_CATEGORY.equals(file.category()) && ProtestBudgetParser.noText(document)) {
            return ProcessingResultMessage.failed(file, ProtestBudgetParser.NO_TEXT);
        }
        return ProcessingResultMessage.completed(file, null, pages, null, null);
    }

    private void publish(ProcessingResultMessage result) {
        var properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding("UTF-8");
        rabbit.send("", QueueConfig.PROCESSING_RESULTS, new Message(contract.write(result), properties));
    }

    private static String readableReason(Exception error) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (error instanceof ResourceAccessException) {
            return "O leitor de documentos não respondeu. Ele está rodando? (" + message + ")";
        }
        return message.length() > 1000 ? message.substring(0, 1000) + "…" : message;
    }
}
