package br.com.contextpilot.evaluation;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import br.com.contextpilot.answer.AnswerModels.RespostaRag;
import br.com.contextpilot.answer.AnswerService;
import br.com.contextpilot.audit.AuditService;
import br.com.contextpilot.evaluation.EvaluationModels.CasoAvaliacao;
import br.com.contextpilot.evaluation.EvaluationModels.ComparacaoExecucoes;
import br.com.contextpilot.evaluation.EvaluationModels.ConjuntoAvaliacao;
import br.com.contextpilot.evaluation.EvaluationModels.CriarCasoRequest;
import br.com.contextpilot.evaluation.EvaluationModels.CriarConjuntoRequest;
import br.com.contextpilot.evaluation.EvaluationModels.ExecucaoAvaliacao;
import br.com.contextpilot.evaluation.EvaluationModels.ResultadoCaso;
import br.com.contextpilot.evaluation.EvaluationModels.TrabalhoAvaliacao;
import br.com.contextpilot.shared.domain.BusinessRuleException;
import br.com.contextpilot.shared.domain.ResourceNotFoundException;
import br.com.contextpilot.workspace.WorkspaceAccessService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

@Service
public class EvaluationService {

    private final EvaluationRepository repositorio;
    private final WorkspaceAccessService acessoEspaco;
    private final AnswerService respostas;
    private final AuditService auditoria;
    private final EvaluationMetrics metricasAvaliacao;
    private final MeterRegistry metricas;
    private final Clock relogio;
    private final Duration tempoLease;

    public EvaluationService(
            EvaluationRepository repositorio,
            WorkspaceAccessService acessoEspaco,
            AnswerService respostas,
            AuditService auditoria,
            EvaluationMetrics metricasAvaliacao,
            MeterRegistry metricas,
            Clock relogio,
            @Value("${contextpilot.avaliacoes.tempo-lease:5m}") Duration tempoLease) {
        this.repositorio = repositorio;
        this.acessoEspaco = acessoEspaco;
        this.respostas = respostas;
        this.auditoria = auditoria;
        this.metricasAvaliacao = metricasAvaliacao;
        this.metricas = metricas;
        this.relogio = relogio;
        this.tempoLease = tempoLease;
    }

    @Transactional
    public ConjuntoAvaliacao criarConjunto(UUID espacoId, CriarConjuntoRequest requisicao, String usuarioId) {
        acessoEspaco.exigirCuradoria(espacoId, usuarioId);
        UUID id = UUID.randomUUID();
        repositorio.criarConjunto(id, espacoId, requisicao.nome().trim(), limpar(requisicao.descricao()),
                usuarioId, Instant.now(relogio));
        auditoria.registrar(espacoId, usuarioId, "CONJUNTO_AVALIACAO_CRIADO", "CONJUNTO_AVALIACAO",
                id.toString(), Map.of("nome", requisicao.nome().trim()));
        return repositorio.listarConjuntos(espacoId).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    public List<ConjuntoAvaliacao> listarConjuntos(UUID espacoId, String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        return repositorio.listarConjuntos(espacoId);
    }

    @Transactional
    public CasoAvaliacao adicionarCaso(
            UUID espacoId,
            UUID conjuntoId,
            CriarCasoRequest requisicao,
            String usuarioId) {
        acessoEspaco.exigirCuradoria(espacoId, usuarioId);
        exigirConjunto(espacoId, conjuntoId);
        if (!requisicao.deveRecusar() && requisicao.termosEsperados().isEmpty()
                && requisicao.documentosEsperados().isEmpty()) {
            throw new BusinessRuleException("Informe termos ou documentos esperados para um caso que deve responder.");
        }
        UUID id = UUID.randomUUID();
        List<String> termos = requisicao.termosEsperados().stream().map(String::trim).distinct().toList();
        repositorio.criarCaso(id, conjuntoId, requisicao.pergunta().trim(), termos,
                requisicao.documentosEsperados().stream().distinct().toList(), requisicao.deveRecusar(),
                requisicao.latenciaMaximaMs(), requisicao.custoMaximoUsd(), Instant.now(relogio));
        return repositorio.listarCasos(conjuntoId).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    public List<CasoAvaliacao> listarCasos(UUID espacoId, UUID conjuntoId, String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        exigirConjunto(espacoId, conjuntoId);
        return repositorio.listarCasos(conjuntoId);
    }

    @Transactional
    public ExecucaoAvaliacao executar(UUID espacoId, UUID conjuntoId, String usuarioId) {
        acessoEspaco.exigirCuradoria(espacoId, usuarioId);
        exigirConjunto(espacoId, conjuntoId);
        List<CasoAvaliacao> casos = repositorio.listarCasos(conjuntoId);
        if (casos.isEmpty()) {
            throw new BusinessRuleException("Adicione pelo menos um caso antes de executar a avaliacao.");
        }

        UUID execucaoId = UUID.randomUUID();
        repositorio.agendarExecucao(execucaoId, conjuntoId, usuarioId, casos.size(), Instant.now(relogio));
        metricas.counter("contextpilot.avaliacao.execucoes", "resultado", "agendada").increment();
        auditoria.registrar(espacoId, usuarioId, "AVALIACAO_AGENDADA", "EXECUCAO_AVALIACAO",
                execucaoId.toString(), Map.of("casos", casos.size()));
        return buscarExecucao(espacoId, conjuntoId, execucaoId, usuarioId);
    }

    void processar(TrabalhoAvaliacao trabalho, String trabalhadorId) {
        UUID execucaoId = trabalho.execucaoId();
        try {
            acessoEspaco.exigirCuradoria(trabalho.espacoId(), trabalho.usuarioId());
            List<CasoAvaliacao> casosPendentes = repositorio.listarCasosPendentes(
                    trabalho.conjuntoId(), execucaoId);
            for (CasoAvaliacao caso : casosPendentes) {
                if (repositorio.cancelamentoSolicitado(execucaoId)) {
                    repositorio.cancelarExecucao(execucaoId, trabalhadorId, Instant.now(relogio));
                    metricas.counter("contextpilot.avaliacao.execucoes", "resultado", "cancelada").increment();
                    auditoria.registrar(trabalho.espacoId(), trabalho.usuarioId(), "AVALIACAO_CANCELADA",
                            "EXECUCAO_AVALIACAO", execucaoId.toString(), Map.of());
                    return;
                }
                repositorio.renovarLease(execucaoId, trabalhadorId, Instant.now(relogio).plus(tempoLease));
                RespostaRag resposta = respostas.perguntar(
                        trabalho.espacoId(), caso.pergunta(), trabalho.usuarioId());
                ResultadoCaso resultado = avaliar(caso, resposta);
                repositorio.salvarResultado(execucaoId, resultado);
                repositorio.registrarProgresso(execucaoId, trabalhadorId);
            }

            if (repositorio.cancelamentoSolicitado(execucaoId)) {
                repositorio.cancelarExecucao(execucaoId, trabalhadorId, Instant.now(relogio));
                metricas.counter("contextpilot.avaliacao.execucoes", "resultado", "cancelada").increment();
                return;
            }
            List<ResultadoCaso> resultados = repositorio.listarResultados(execucaoId);
            int aprovados = (int) resultados.stream().filter(ResultadoCaso::aprovado).count();
            RespostaRag ultimaResposta = resultados.isEmpty() ? null
                    : respostas.buscar(trabalho.espacoId(), resultados.getLast().consultaId(), trabalho.usuarioId());
            String modeloEmbedding = ultimaResposta == null ? null : ultimaResposta.modeloEmbedding();
            String provedorIa = ultimaResposta == null ? null : ultimaResposta.provedorIa();
            int totalCasos = resultados.size();
            double taxa = totalCasos == 0 ? 0.0 : (double) aprovados / totalCasos;
            double recallMedio = media(resultados.stream().mapToDouble(ResultadoCaso::pontuacaoFontes).toArray());
            double precisaoMedia = media(resultados.stream().mapToDouble(ResultadoCaso::precisaoFontes).toArray());
            double mrrMedio = media(resultados.stream().mapToDouble(ResultadoCaso::mrr).toArray());
            long latenciaP95 = percentil95(resultados.stream().mapToLong(ResultadoCaso::latenciaMs).toArray());
            BigDecimal custoTotal = resultados.stream().map(ResultadoCaso::custoUsd)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            repositorio.concluirExecucao(execucaoId, aprovados, taxa, recallMedio, precisaoMedia,
                    mrrMedio, latenciaP95, custoTotal, modeloEmbedding, provedorIa,
                    Instant.now(relogio), trabalhadorId);
            metricasAvaliacao.registrar(taxa, recallMedio, precisaoMedia, mrrMedio, latenciaP95, custoTotal);
            metricas.counter("contextpilot.avaliacao.execucoes", "resultado", "concluida").increment();
            auditoria.registrar(trabalho.espacoId(), trabalho.usuarioId(), "AVALIACAO_EXECUTADA",
                    "EXECUCAO_AVALIACAO", execucaoId.toString(), Map.of("casos", totalCasos, "aprovados", aprovados,
                            "taxa", taxa, "recall", recallMedio, "precisao", precisaoMedia, "mrr", mrrMedio));
        } catch (RuntimeException excecao) {
            String erro = "Falha durante a avaliacao: " + excecao.getClass().getSimpleName();
            repositorio.falharExecucao(execucaoId, erro, Instant.now(relogio), trabalhadorId);
            metricas.counter("contextpilot.avaliacao.execucoes", "resultado", "falhou").increment();
            auditoria.registrar(trabalho.espacoId(), trabalho.usuarioId(), "AVALIACAO_FALHOU", "EXECUCAO_AVALIACAO",
                    execucaoId.toString(), Map.of("erro", excecao.getClass().getSimpleName()));
        }
    }

    @Transactional
    public ExecucaoAvaliacao cancelar(
            UUID espacoId,
            UUID conjuntoId,
            UUID execucaoId,
            String usuarioId) {
        acessoEspaco.exigirCuradoria(espacoId, usuarioId);
        ExecucaoAvaliacao execucao = buscarExecucao(espacoId, conjuntoId, execucaoId, usuarioId);
        if (!"PENDENTE".equals(execucao.estado()) && !"EXECUTANDO".equals(execucao.estado())) {
            throw new BusinessRuleException("Somente execucao pendente ou em andamento pode ser cancelada.");
        }
        repositorio.solicitarCancelamento(execucaoId, Instant.now(relogio));
        auditoria.registrar(espacoId, usuarioId, "CANCELAMENTO_AVALIACAO_SOLICITADO",
                "EXECUCAO_AVALIACAO", execucaoId.toString(), Map.of());
        return buscarExecucao(espacoId, conjuntoId, execucaoId, usuarioId);
    }

    public ExecucaoAvaliacao buscarExecucao(
            UUID espacoId,
            UUID conjuntoId,
            UUID execucaoId,
            String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        exigirConjunto(espacoId, conjuntoId);
        return repositorio.buscarExecucao(execucaoId, conjuntoId)
                .orElseThrow(() -> new ResourceNotFoundException("Execucao de avaliacao nao encontrada."));
    }

    public ComparacaoExecucoes comparar(
            UUID espacoId,
            UUID conjuntoId,
            UUID execucaoAtualId,
            UUID execucaoBaseId,
            String usuarioId) {
        ExecucaoAvaliacao atual = buscarExecucao(espacoId, conjuntoId, execucaoAtualId, usuarioId);
        ExecucaoAvaliacao base = buscarExecucao(espacoId, conjuntoId, execucaoBaseId, usuarioId);
        if (!"CONCLUIDA".equals(atual.estado()) || !"CONCLUIDA".equals(base.estado())) {
            throw new BusinessRuleException("Somente execucoes concluidas podem ser comparadas.");
        }

        double deltaTaxa = atual.taxaAcerto() - base.taxaAcerto();
        double deltaRecall = atual.recallMedio() - base.recallMedio();
        double deltaPrecisao = atual.precisaoMedia() - base.precisaoMedia();
        double deltaMrr = atual.mrrMedio() - base.mrrMedio();
        long deltaLatencia = atual.latenciaP95Ms() - base.latenciaP95Ms();
        BigDecimal deltaCusto = atual.custoTotalUsd().subtract(base.custoTotalUsd());
        List<String> motivos = new ArrayList<>();
        verificarQueda(deltaTaxa, "taxa de acerto", motivos);
        verificarQueda(deltaRecall, "recall", motivos);
        verificarQueda(deltaPrecisao, "precisao", motivos);
        verificarQueda(deltaMrr, "MRR", motivos);
        if (base.latenciaP95Ms() > 0 && deltaLatencia > 100
                && atual.latenciaP95Ms() > Math.round(base.latenciaP95Ms() * 1.20)) {
            motivos.add("latencia p95 aumentou mais de 20%");
        }
        if (base.custoTotalUsd().signum() > 0
                && deltaCusto.compareTo(new BigDecimal("0.000001")) > 0
                && atual.custoTotalUsd().compareTo(base.custoTotalUsd().multiply(new BigDecimal("1.20"))) > 0) {
            motivos.add("custo total aumentou mais de 20%");
        }
        return new ComparacaoExecucoes(execucaoAtualId, execucaoBaseId, deltaTaxa, deltaRecall,
                deltaPrecisao, deltaMrr, deltaLatencia, deltaCusto, !motivos.isEmpty(), List.copyOf(motivos));
    }

    private ResultadoCaso avaliar(CasoAvaliacao caso, RespostaRag resposta) {
        String texto = normalizar(resposta.resposta());
        long termosEncontrados = caso.termosEsperados().stream()
                .map(this::normalizar)
                .filter(texto::contains)
                .count();
        double pontuacaoTermos = caso.termosEsperados().isEmpty()
                ? 1.0 : (double) termosEncontrados / caso.termosEsperados().size();

        Set<UUID> documentosObtidos = new LinkedHashSet<>();
        resposta.fontes().forEach(fonte -> documentosObtidos.add(fonte.documentoId()));
        long fontesEncontradas = caso.documentosEsperados().stream().filter(documentosObtidos::contains).count();
        double pontuacaoFontes = caso.documentosEsperados().isEmpty()
                ? 1.0 : (double) fontesEncontradas / caso.documentosEsperados().size();
        double precisaoFontes = caso.documentosEsperados().isEmpty()
                ? 1.0 : documentosObtidos.isEmpty() ? 0.0 : (double) fontesEncontradas / documentosObtidos.size();
        double mrr = calcularMrr(caso.documentosEsperados(), documentosObtidos);
        boolean recusaCorreta = resposta.recusada() == caso.deveRecusar();
        boolean latenciaRespeitada = caso.latenciaMaximaMs() == null
                || resposta.latenciaMs() <= caso.latenciaMaximaMs();
        boolean custoRespeitado = caso.custoMaximoUsd() == null
                || resposta.custoEstimadoUsd().compareTo(caso.custoMaximoUsd()) <= 0;
        boolean orcamentoRespeitado = latenciaRespeitada && custoRespeitado;
        boolean aprovado = recusaCorreta && pontuacaoTermos >= 0.8 && pontuacaoFontes >= 0.8
                && precisaoFontes >= 0.5 && mrr > 0 && orcamentoRespeitado;
        String detalhes = "termos=%.2f; recall=%.2f; precisao=%.2f; mrr=%.2f; recusa=%s; orcamento=%s"
                .formatted(pontuacaoTermos, pontuacaoFontes, precisaoFontes, mrr,
                        recusaCorreta, orcamentoRespeitado);
        return new ResultadoCaso(caso.id(), resposta.consultaId(), aprovado,
                pontuacaoTermos, pontuacaoFontes, precisaoFontes, mrr, recusaCorreta,
                resposta.latenciaMs(), resposta.custoEstimadoUsd(), orcamentoRespeitado, detalhes);
    }

    private double calcularMrr(List<UUID> esperados, Set<UUID> obtidos) {
        if (esperados.isEmpty()) {
            return 1.0;
        }
        int posicao = 1;
        for (UUID documento : obtidos) {
            if (esperados.contains(documento)) {
                return 1.0 / posicao;
            }
            posicao++;
        }
        return 0.0;
    }

    private double media(double[] valores) {
        return java.util.Arrays.stream(valores).average().orElse(0.0);
    }

    private long percentil95(long[] valores) {
        if (valores.length == 0) {
            return 0;
        }
        java.util.Arrays.sort(valores);
        int indice = Math.max(0, (int) Math.ceil(valores.length * 0.95) - 1);
        return valores[indice];
    }

    private void verificarQueda(double delta, String metrica, List<String> motivos) {
        if (delta < -0.05) {
            motivos.add(metrica + " caiu mais de 5 pontos percentuais");
        }
    }

    private void exigirConjunto(UUID espacoId, UUID conjuntoId) {
        if (!repositorio.conjuntoPertenceAoEspaco(conjuntoId, espacoId)) {
            throw new ResourceNotFoundException("Conjunto de avaliacao nao encontrado.");
        }
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String limpar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
