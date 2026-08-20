package br.com.contextpilot.document;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.net.InetAddress;

import br.com.contextpilot.document.DocumentModels.TarefaIngestao;
import br.com.contextpilot.reindex.EmbeddingIndexService;
import br.com.contextpilot.governance.GovernanceService;
import br.com.contextpilot.retrieval.EmbeddingProviderRegistry;
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
    private final DocumentContentStorage conteudos;
    private final TextChunker fragmentador;
    private final EmbeddingProviderRegistry provedores;
    private final EmbeddingIndexService indices;
    private final PromptInjectionDetector detectorPrompt;
    private final GovernanceService governanca;
    private final DocumentIngestionTransaction transacao;
    private final MeterRegistry metricas;
    private final Clock relogio;
    private final Duration duracaoLease;
    private final String trabalhadorId;

    public DocumentIngestionService(
            DocumentRepository repositorio,
            TextExtractor extrator,
            DocumentContentStorage conteudos,
            TextChunker fragmentador,
            EmbeddingProviderRegistry provedores,
            EmbeddingIndexService indices,
            PromptInjectionDetector detectorPrompt,
            GovernanceService governanca,
            DocumentIngestionTransaction transacao,
            MeterRegistry metricas,
            Clock relogio,
            @org.springframework.beans.factory.annotation.Value("${contextpilot.ingestao.duracao-lease:5m}") Duration duracaoLease,
            @org.springframework.beans.factory.annotation.Value("${contextpilot.instancia-id:}") String instanciaId) {
        this.repositorio = repositorio;
        this.extrator = extrator;
        this.conteudos = conteudos;
        this.fragmentador = fragmentador;
        this.provedores = provedores;
        this.indices = indices;
        this.detectorPrompt = detectorPrompt;
        this.governanca = governanca;
        this.transacao = transacao;
        this.metricas = metricas;
        this.relogio = relogio;
        this.duracaoLease = duracaoLease;
        this.trabalhadorId = instanciaId == null || instanciaId.isBlank() ? nomeMaquina() : instanciaId;
    }

    @Scheduled(initialDelayString = "${contextpilot.ingestao.atraso-inicial-ms:1000}",
            fixedDelayString = "${contextpilot.ingestao.intervalo-ms:800}")
    public void consumirFila() {
        for (int contador = 0; contador < 5; contador++) {
            Instant agora = Instant.now(relogio);
            var tarefa = repositorio.reivindicarProximaTarefa(
                    agora, agora.plus(duracaoLease), trabalhadorId);
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
            byte[] conteudo = conteudos.obter(documento.referenciaConteudo());
            var textoExtraido = extrator.extrair(documento.tipoMime(), conteudo);
            List<String> trechos = fragmentador.dividir(textoExtraido.texto());
            var indice = indices.obterAtivo(documento.espacoId());
            var embeddings = provedores.obter(indice.modelo());
            var resultadosEmbedding = trechos.stream()
                    .map(embeddings::gerarComUso)
                    .toList();
            List<String> vetores = resultadosEmbedding.stream()
                    .map(resultado -> VectorText.serializar(resultado.vetor()))
                    .toList();
            List<Boolean> riscosPrompt = trechos.stream().map(detectorPrompt::suspeito).toList();

            transacao.concluir(tarefa, documento.espacoId(), indice.id(), trechos, vetores, riscosPrompt,
                    textoExtraido.origem(), textoExtraido.paginasOcr());
            int tokens = resultadosEmbedding.stream().mapToInt(resultado -> resultado.tokensEntrada()).sum();
            java.math.BigDecimal custo = resultadosEmbedding.stream()
                    .map(resultado -> resultado.custoEstimadoUsd())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            governanca.registrarConsumoIa(documento.espacoId(), embeddings.provedor(), embeddings.nome(),
                    "EMBEDDING", resultadosEmbedding.size(), tokens, 0, custo);
            metricas.counter("contextpilot.ingestao.total", "resultado", "sucesso").increment();
            metricas.counter("contextpilot.extracao.total", "origem", textoExtraido.origem().name().toLowerCase())
                    .increment();
            long bloqueados = riscosPrompt.stream().filter(Boolean::booleanValue).count();
            if (bloqueados > 0) {
                metricas.counter("contextpilot.seguranca.prompt_injection", "resultado", "trecho_bloqueado")
                        .increment(bloqueados);
            }
            logger.info("Documento {} indexado em {} trechos pelo provedor {} com extracao {}.",
                    documento.id(), trechos.size(), embeddings.nome(), textoExtraido.origem());
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

    private String nomeMaquina() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException excecao) {
            return "instancia-desconhecida";
        }
    }

}
