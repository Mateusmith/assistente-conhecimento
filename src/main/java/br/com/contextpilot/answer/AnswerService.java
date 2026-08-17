package br.com.contextpilot.answer;

import static br.com.contextpilot.answer.AnswerModels.RESPOSTA_SEM_CONTEXTO;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import br.com.contextpilot.answer.AnswerModels.FonteContexto;
import br.com.contextpilot.answer.AnswerModels.RegistrarFeedbackRequest;
import br.com.contextpilot.answer.AnswerModels.RespostaRag;
import br.com.contextpilot.answer.AnswerModels.ResultadoGeracao;
import br.com.contextpilot.audit.AuditService;
import br.com.contextpilot.retrieval.HybridSearchService;
import br.com.contextpilot.shared.domain.ResourceNotFoundException;
import br.com.contextpilot.workspace.WorkspaceAccessService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnswerService {

    private static final Pattern CITACAO = Pattern.compile("\\[(F\\d+)]");

    private final HybridSearchService busca;
    private final AnswerGenerator gerador;
    private final AnswerRepository repositorio;
    private final WorkspaceAccessService acessoEspaco;
    private final AuditService auditoria;
    private final MeterRegistry metricas;
    private final Clock relogio;

    public AnswerService(
            HybridSearchService busca,
            AnswerGenerator gerador,
            AnswerRepository repositorio,
            WorkspaceAccessService acessoEspaco,
            AuditService auditoria,
            MeterRegistry metricas,
            Clock relogio) {
        this.busca = busca;
        this.gerador = gerador;
        this.repositorio = repositorio;
        this.acessoEspaco = acessoEspaco;
        this.auditoria = auditoria;
        this.metricas = metricas;
        this.relogio = relogio;
    }

    @Transactional
    public RespostaRag perguntar(UUID espacoId, String pergunta, String usuarioId) {
        long inicio = System.nanoTime();
        String perguntaLimpa = pergunta.trim();
        var recuperadas = busca.buscar(espacoId, perguntaLimpa, usuarioId);
        List<FonteContexto> fontes = new ArrayList<>();
        for (int indice = 0; indice < recuperadas.size(); indice++) {
            var fonte = recuperadas.get(indice);
            fontes.add(new FonteContexto("F" + (indice + 1), fonte.trechoId(), fonte.documentoId(),
                    fonte.tituloDocumento(), fonte.ordemTrecho(), fonte.conteudo(), fonte.pontuacao()));
        }

        ResultadoGeracao geracao = fontes.isEmpty()
                ? new ResultadoGeracao(RESPOSTA_SEM_CONTEXTO, "recusa-segura")
                : gerador.gerar(perguntaLimpa, fontes);
        Validacao validacao = validar(geracao.texto(), fontes);
        List<FonteContexto> citadas = fontes.stream()
                .filter(fonte -> validacao.marcadores().contains(fonte.marcador()))
                .toList();
        long latenciaMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        UUID consultaId = UUID.randomUUID();
        Instant criadaEm = Instant.now(relogio);

        repositorio.salvar(consultaId, espacoId, usuarioId, perguntaLimpa, validacao.texto(),
                validacao.recusa(), geracao.provedor(), latenciaMs, criadaEm, citadas);
        metricas.counter("contextpilot.rag.consultas", "resultado", validacao.recusa() ? "recusa" : "respondida").increment();
        metricas.timer("contextpilot.rag.latencia", "provedor", geracao.provedor())
                .record(latenciaMs, TimeUnit.MILLISECONDS);
        auditoria.registrar(espacoId, usuarioId, "CONSULTA_RAG_REALIZADA", "CONSULTA", consultaId.toString(),
                Map.of("recusada", validacao.recusa(), "fontes", citadas.size(), "provedor", geracao.provedor()));

        return repositorio.buscar(consultaId, espacoId, usuarioId).orElseThrow();
    }

    public List<RespostaRag> listar(UUID espacoId, String usuarioId, int limite) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        return repositorio.listar(espacoId, usuarioId, Math.max(1, Math.min(limite, 100)));
    }

    public RespostaRag buscar(UUID espacoId, UUID consultaId, String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        return repositorio.buscar(consultaId, espacoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Consulta nao encontrada."));
    }

    @Transactional
    public void registrarFeedback(
            UUID espacoId,
            UUID consultaId,
            RegistrarFeedbackRequest requisicao,
            String usuarioId) {
        buscar(espacoId, consultaId, usuarioId);
        String comentario = requisicao.comentario() == null || requisicao.comentario().isBlank()
                ? null : requisicao.comentario().trim();
        repositorio.salvarFeedback(consultaId, usuarioId, requisicao.util(), comentario, Instant.now(relogio));
        metricas.counter("contextpilot.rag.feedback", "util", requisicao.util().toString()).increment();
    }

    private Validacao validar(String texto, List<FonteContexto> fontes) {
        if (RESPOSTA_SEM_CONTEXTO.equals(texto.trim())) {
            return new Validacao(RESPOSTA_SEM_CONTEXTO, true, Set.of());
        }

        Set<String> permitidos = fontes.stream().map(FonteContexto::marcador).collect(java.util.stream.Collectors.toSet());
        Set<String> encontrados = new HashSet<>();
        var matcher = CITACAO.matcher(texto);
        while (matcher.find()) {
            encontrados.add(matcher.group(1));
        }
        if (encontrados.isEmpty() || !permitidos.containsAll(encontrados)) {
            metricas.counter("contextpilot.rag.validacao", "resultado", "citacao_invalida").increment();
            return new Validacao(RESPOSTA_SEM_CONTEXTO, true, Set.of());
        }
        return new Validacao(texto.trim(), false, Set.copyOf(encontrados));
    }

    private record Validacao(String texto, boolean recusa, Set<String> marcadores) {
    }
}
