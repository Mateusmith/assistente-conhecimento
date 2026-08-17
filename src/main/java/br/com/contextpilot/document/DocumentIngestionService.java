package br.com.contextpilot.document;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import br.com.contextpilot.document.DocumentModels.TarefaIngestao;
import br.com.contextpilot.retrieval.EmbeddingProvider;
import br.com.contextpilot.retrieval.VectorText;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentRepository repositorio;
    private final TextExtractor extrator;
    private final TextChunker fragmentador;
    private final EmbeddingProvider embeddings;
    private final DocumentIngestionTransaction transacao;
    private final MeterRegistry metricas;
    private final Clock relogio;

    public DocumentIngestionService(
            DocumentRepository repositorio,
            TextExtractor extrator,
            TextChunker fragmentador,
            EmbeddingProvider embeddings,
            DocumentIngestionTransaction transacao,
            MeterRegistry metricas,
            Clock relogio) {
        this.repositorio = repositorio;
        this.extrator = extrator;
        this.fragmentador = fragmentador;
        this.embeddings = embeddings;
        this.transacao = transacao;
        this.metricas = metricas;
        this.relogio = relogio;
    }

    @Scheduled(initialDelayString = "${contextpilot.ingestao.atraso-inicial-ms:1000}",
            fixedDelayString = "${contextpilot.ingestao.intervalo-ms:800}")
    public void consumirFila() {
        for (int contador = 0; contador < 5; contador++) {
            var tarefa = repositorio.reivindicarProximaTarefa(Instant.now(relogio));
            if (tarefa.isEmpty()) {
                return;
            }
            processar(tarefa.get());
        }
    }

    void processar(TarefaIngestao tarefa) {
        long inicio = System.nanoTime();
        try {
            repositorio.marcarDocumentoProcessando(tarefa.documentoId());
            var documento = repositorio.buscarParaIngestao(tarefa.documentoId())
                    .orElseThrow(() -> new IllegalStateException("Documento da tarefa nao foi encontrado."));
            String texto = extrator.extrair(documento.tipoMime(), documento.conteudoOriginal());
            List<String> trechos = fragmentador.dividir(texto);
            List<String> vetores = trechos.stream()
                    .map(embeddings::gerar)
                    .map(VectorText::serializar)
                    .toList();

            transacao.concluir(tarefa, documento.espacoId(), trechos, vetores);
            metricas.counter("contextpilot.ingestao.total", "resultado", "sucesso").increment();
            logger.info("Documento {} indexado em {} trechos pelo provedor {}.",
                    documento.id(), trechos.size(), embeddings.nome());
        } catch (RuntimeException excecao) {
            transacao.falhar(tarefa, excecao);
            metricas.counter("contextpilot.ingestao.total", "resultado", "falha").increment();
            logger.warn("Falha na tentativa {} de ingestao do documento {}: {}",
                    tarefa.tentativa(), tarefa.documentoId(), excecao.getMessage());
        } finally {
            metricas.timer("contextpilot.ingestao.duracao")
                    .record(System.nanoTime() - inicio, TimeUnit.NANOSECONDS);
        }
    }

}
