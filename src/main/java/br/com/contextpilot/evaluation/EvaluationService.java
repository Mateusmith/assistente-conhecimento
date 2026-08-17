package br.com.contextpilot.evaluation;

import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import br.com.contextpilot.answer.AnswerModels.RespostaRag;
import br.com.contextpilot.answer.AnswerService;
import br.com.contextpilot.audit.AuditService;
import br.com.contextpilot.evaluation.EvaluationModels.CasoAvaliacao;
import br.com.contextpilot.evaluation.EvaluationModels.ConjuntoAvaliacao;
import br.com.contextpilot.evaluation.EvaluationModels.CriarCasoRequest;
import br.com.contextpilot.evaluation.EvaluationModels.CriarConjuntoRequest;
import br.com.contextpilot.evaluation.EvaluationModels.ExecucaoAvaliacao;
import br.com.contextpilot.evaluation.EvaluationModels.ResultadoCaso;
import br.com.contextpilot.shared.domain.BusinessRuleException;
import br.com.contextpilot.shared.domain.ResourceNotFoundException;
import br.com.contextpilot.workspace.WorkspaceAccessService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {

    private final EvaluationRepository repositorio;
    private final WorkspaceAccessService acessoEspaco;
    private final AnswerService respostas;
    private final AuditService auditoria;
    private final MeterRegistry metricas;
    private final Clock relogio;

    public EvaluationService(
            EvaluationRepository repositorio,
            WorkspaceAccessService acessoEspaco,
            AnswerService respostas,
            AuditService auditoria,
            MeterRegistry metricas,
            Clock relogio) {
        this.repositorio = repositorio;
        this.acessoEspaco = acessoEspaco;
        this.respostas = respostas;
        this.auditoria = auditoria;
        this.metricas = metricas;
        this.relogio = relogio;
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
                requisicao.documentosEsperados().stream().distinct().toList(), requisicao.deveRecusar(), Instant.now(relogio));
        return repositorio.listarCasos(conjuntoId).stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }

    public List<CasoAvaliacao> listarCasos(UUID espacoId, UUID conjuntoId, String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        exigirConjunto(espacoId, conjuntoId);
        return repositorio.listarCasos(conjuntoId);
    }

    public ExecucaoAvaliacao executar(UUID espacoId, UUID conjuntoId, String usuarioId) {
        acessoEspaco.exigirCuradoria(espacoId, usuarioId);
        exigirConjunto(espacoId, conjuntoId);
        List<CasoAvaliacao> casos = repositorio.listarCasos(conjuntoId);
        if (casos.isEmpty()) {
            throw new BusinessRuleException("Adicione pelo menos um caso antes de executar a avaliacao.");
        }

        UUID execucaoId = UUID.randomUUID();
        repositorio.iniciarExecucao(execucaoId, conjuntoId, usuarioId, casos.size(), Instant.now(relogio));
        int aprovados = 0;
        for (CasoAvaliacao caso : casos) {
            RespostaRag resposta = respostas.perguntar(espacoId, caso.pergunta(), usuarioId);
            ResultadoCaso resultado = avaliar(caso, resposta);
            repositorio.salvarResultado(execucaoId, resultado);
            if (resultado.aprovado()) {
                aprovados++;
            }
        }
        double taxa = (double) aprovados / casos.size();
        repositorio.concluirExecucao(execucaoId, aprovados, taxa, Instant.now(relogio));
        metricas.gauge("contextpilot.avaliacao.taxa_acerto", taxa);
        auditoria.registrar(espacoId, usuarioId, "AVALIACAO_EXECUTADA", "EXECUCAO_AVALIACAO",
                execucaoId.toString(), Map.of("casos", casos.size(), "aprovados", aprovados, "taxa", taxa));
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

    private ResultadoCaso avaliar(CasoAvaliacao caso, RespostaRag resposta) {
        String texto = normalizar(resposta.resposta());
        long termosEncontrados = caso.termosEsperados().stream()
                .map(this::normalizar)
                .filter(texto::contains)
                .count();
        double pontuacaoTermos = caso.termosEsperados().isEmpty()
                ? 1.0 : (double) termosEncontrados / caso.termosEsperados().size();

        Set<UUID> documentosObtidos = new HashSet<>();
        resposta.fontes().forEach(fonte -> documentosObtidos.add(fonte.documentoId()));
        long fontesEncontradas = caso.documentosEsperados().stream().filter(documentosObtidos::contains).count();
        double pontuacaoFontes = caso.documentosEsperados().isEmpty()
                ? 1.0 : (double) fontesEncontradas / caso.documentosEsperados().size();
        boolean recusaCorreta = resposta.recusada() == caso.deveRecusar();
        boolean aprovado = recusaCorreta && pontuacaoTermos >= 0.8 && pontuacaoFontes >= 0.8;
        String detalhes = "termos=%.2f; fontes=%.2f; recusaCorreta=%s"
                .formatted(pontuacaoTermos, pontuacaoFontes, recusaCorreta);
        return new ResultadoCaso(caso.id(), resposta.consultaId(), aprovado,
                pontuacaoTermos, pontuacaoFontes, recusaCorreta, detalhes);
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
