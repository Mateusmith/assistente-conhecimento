package br.com.contextpilot.reindex;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import br.com.contextpilot.reindex.EmbeddingIndexModels.IndiceParaProcessar;
import br.com.contextpilot.reindex.EmbeddingIndexModels.TrechoParaIndexar;
import br.com.contextpilot.governance.GovernanceService;
import br.com.contextpilot.retrieval.EmbeddingProvider.ResultadoEmbedding;
import br.com.contextpilot.retrieval.EmbeddingProviderRegistry;
import br.com.contextpilot.retrieval.VectorText;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingIndexWorker {

    private static final Logger logger = LoggerFactory.getLogger(EmbeddingIndexWorker.class);

    private final EmbeddingIndexRepository repositorio;
    private final EmbeddingIndexTransaction transacao;
    private final EmbeddingIndexService indices;
    private final EmbeddingProviderRegistry provedores;
    private final MeterRegistry metricas;
    private final GovernanceService governanca;
    private final Clock relogio;
    private final int tamanhoLote;
    private final Duration duracaoLease;
    private final String trabalhadorId;

    public EmbeddingIndexWorker(
            EmbeddingIndexRepository repositorio,
            EmbeddingIndexTransaction transacao,
            EmbeddingIndexService indices,
            EmbeddingProviderRegistry provedores,
            MeterRegistry metricas,
            GovernanceService governanca,
            Clock relogio,
            @Value("${contextpilot.reindexacao.tamanho-lote:40}") int tamanhoLote,
            @Value("${contextpilot.reindexacao.duracao-lease:2m}") Duration duracaoLease,
            @Value("${contextpilot.instancia-id:}") String instanciaId) {
        this.repositorio = repositorio;
        this.transacao = transacao;
        this.indices = indices;
        this.provedores = provedores;
        this.metricas = metricas;
        this.governanca = governanca;
        this.relogio = relogio;
        this.tamanhoLote = tamanhoLote;
        this.duracaoLease = duracaoLease;
        this.trabalhadorId = instanciaId == null || instanciaId.isBlank() ? nomeMaquina() : instanciaId;
    }

    @Scheduled(initialDelayString = "${contextpilot.reindexacao.atraso-inicial-ms:2500}",
            fixedDelayString = "${contextpilot.reindexacao.intervalo-ms:1000}")
    public void consumir() {
        Instant agora = Instant.now(relogio);
        repositorio.reivindicar(agora, agora.plus(duracaoLease), trabalhadorId).ifPresent(this::processar);
    }

    private void processar(IndiceParaProcessar indice) {
        long inicio = System.nanoTime();
        try {
            var provedor = provedores.obter(indice.modelo());
            var pendentes = repositorio.listarPendentes(indice.id(), indice.espacoId(), tamanhoLote);
            if (pendentes.isEmpty()) {
                indices.ativarConcluido(indice.espacoId(), indice.id());
                return;
            }
            List<ResultadoTrecho> resultados = pendentes.stream()
                    .map(trecho -> new ResultadoTrecho(trecho, provedor.gerarComUso(trecho.conteudo())))
                    .toList();
            List<EmbeddingIndexTransaction.TrechoVetor> vetores = resultados.stream()
                    .map(resultado -> new EmbeddingIndexTransaction.TrechoVetor(
                            resultado.trecho(), VectorText.serializar(resultado.embedding().vetor())))
                    .toList();
            transacao.salvarLote(indice, vetores);
            int tokens = resultados.stream().mapToInt(resultado -> resultado.embedding().tokensEntrada()).sum();
            java.math.BigDecimal custo = resultados.stream().map(resultado -> resultado.embedding().custoEstimadoUsd())
                    .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            governanca.registrarConsumoIa(indice.espacoId(), provedor.provedor(), provedor.nome(),
                    "EMBEDDING", resultados.size(), tokens, 0, custo);
            metricas.counter("contextpilot.reindexacao.trechos", "modelo", indice.modelo()).increment(vetores.size());
        } catch (RuntimeException excecao) {
            int tentativas = repositorio.obterTentativas(indice.id()) + 1;
            String mensagem = mensagem(excecao);
            repositorio.registrarFalha(indice.id(), mensagem, tentativas >= 3);
            metricas.counter("contextpilot.reindexacao.total", "resultado", "falha").increment();
            logger.warn("Falha na reindexacao {} pelo trabalhador {}: {}", indice.id(), trabalhadorId, mensagem);
        } finally {
            metricas.timer("contextpilot.reindexacao.duracao_lote")
                    .record(System.nanoTime() - inicio, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    private String mensagem(RuntimeException excecao) {
        String valor = excecao.getMessage() == null ? excecao.getClass().getSimpleName() : excecao.getMessage();
        return valor.substring(0, Math.min(valor.length(), 1000));
    }

    private String nomeMaquina() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException excecao) {
            return "instancia-desconhecida";
        }
    }

    private record ResultadoTrecho(TrechoParaIndexar trecho, ResultadoEmbedding embedding) {
    }
}
