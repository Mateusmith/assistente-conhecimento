package br.com.contextpilot.conversation;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import br.com.contextpilot.answer.AnswerModels.PerguntarRequest;
import br.com.contextpilot.configuration.ConversationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
class ConversationStreamingService {

    private final ConversationService conversas;
    private final ConversationProperties propriedades;
    private final ExecutorService executorConversas;
    private final MeterRegistry metricas;

    ConversationStreamingService(
            ConversationService conversas,
            ConversationProperties propriedades,
            ExecutorService executorConversas,
            MeterRegistry metricas) {
        this.conversas = conversas;
        this.propriedades = propriedades;
        this.executorConversas = executorConversas;
        this.metricas = metricas;
    }

    SseEmitter iniciar(
            UUID espacoId,
            UUID conversaId,
            PerguntarRequest requisicao,
            String usuarioId,
            String chaveIdempotencia) {
        var emissor = new SseEmitter(propriedades.timeoutStreaming().toMillis());
        var tarefa = new AtomicReference<Future<?>>();
        var finalizado = new AtomicBoolean();

        Runnable cancelar = () -> {
            Future<?> execucao = tarefa.get();
            if (!finalizado.get() && execucao != null) {
                execucao.cancel(true);
            }
        };
        emissor.onTimeout(cancelar);
        emissor.onError(erro -> cancelar.run());
        emissor.onCompletion(cancelar);

        tarefa.set(executorConversas.submit(() -> {
            try {
                enviar(emissor, "etapa", Map.of("estado", "PROCESSANDO", "conversaId", conversaId));
                var interacao = conversas.perguntar(
                        espacoId, conversaId, requisicao, usuarioId, chaveIdempotencia);
                enviar(emissor, "fontes", interacao.resposta().fontes());
                enviar(emissor, "resposta", interacao);
                enviar(emissor, "concluido", Map.of("estado", "CONCLUIDO", "consultaId",
                        interacao.resposta().consultaId()));
                metricas.counter("contextpilot.conversas.streaming", "resultado", "concluido").increment();
                finalizado.set(true);
                emissor.complete();
            } catch (Exception excecao) {
                metricas.counter("contextpilot.conversas.streaming", "resultado", "falhou").increment();
                try {
                    enviar(emissor, "erro", Map.of("codigo", "FALHA_STREAMING",
                            "mensagem", "Nao foi possivel concluir a resposta."));
                } catch (IOException ignorada) {
                    // O cliente ja encerrou a conexao.
                }
                finalizado.set(true);
                emissor.complete();
            }
        }));
        return emissor;
    }

    private void enviar(SseEmitter emissor, String nome, Object dados) throws IOException {
        emissor.send(SseEmitter.event().name(nome).data(dados));
    }
}
