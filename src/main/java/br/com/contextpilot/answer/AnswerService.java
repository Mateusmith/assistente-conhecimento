package br.com.contextpilot.answer;

import static br.com.contextpilot.answer.AnswerModels.RESPOSTA_SEM_CONTEXTO;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import br.com.contextpilot.answer.AnswerModels.FonteContexto;
import br.com.contextpilot.answer.AnswerModels.MensagemMemoria;
import br.com.contextpilot.answer.AnswerModels.PapelMemoria;
import br.com.contextpilot.answer.AnswerModels.PerguntarRequest;
import br.com.contextpilot.answer.AnswerModels.RegistrarFeedbackRequest;
import br.com.contextpilot.answer.AnswerModels.RespostaRag;
import br.com.contextpilot.answer.AnswerModels.ResultadoGeracao;
import br.com.contextpilot.audit.AuditService;
import br.com.contextpilot.governance.GovernanceService;
import br.com.contextpilot.answer.AnswerIntegrityValidator.Validacao;
import br.com.contextpilot.retrieval.HybridSearchService;
import br.com.contextpilot.retrieval.RetrievalModels.EstrategiaBusca;
import br.com.contextpilot.retrieval.RetrievalModels.FiltrosBusca;
import br.com.contextpilot.shared.domain.ResourceNotFoundException;
import br.com.contextpilot.workspace.WorkspaceAccessService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnswerService {

    private final HybridSearchService busca;
    private final AnswerGenerator gerador;
    private final AnswerRepository repositorio;
    private final WorkspaceAccessService acessoEspaco;
    private final AuditService auditoria;
    private final AnswerIntegrityValidator validador;
    private final GovernanceService governanca;
    private final MeterRegistry metricas;
    private final Clock relogio;

    public AnswerService(
            HybridSearchService busca,
            AnswerGenerator gerador,
            AnswerRepository repositorio,
            WorkspaceAccessService acessoEspaco,
            AuditService auditoria,
            AnswerIntegrityValidator validador,
            GovernanceService governanca,
            MeterRegistry metricas,
            Clock relogio) {
        this.busca = busca;
        this.gerador = gerador;
        this.repositorio = repositorio;
        this.acessoEspaco = acessoEspaco;
        this.auditoria = auditoria;
        this.validador = validador;
        this.governanca = governanca;
        this.metricas = metricas;
        this.relogio = relogio;
    }

    @Transactional
    public RespostaRag perguntar(UUID espacoId, String pergunta, String usuarioId) {
        return perguntar(espacoId, new PerguntarRequest(pergunta), usuarioId, List.of());
    }

    @Transactional
    public RespostaRag perguntar(UUID espacoId, PerguntarRequest requisicao, String usuarioId) {
        return perguntar(espacoId, requisicao, usuarioId, List.of());
    }

    @Transactional
    public RespostaRag perguntar(
            UUID espacoId,
            PerguntarRequest requisicao,
            String usuarioId,
            List<MensagemMemoria> memoria) {
        long inicio = System.nanoTime();
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        governanca.reservarConsulta(espacoId);
        String perguntaLimpa = requisicao.pergunta().trim();
        EstrategiaBusca estrategia = requisicao.estrategia() == null
                ? EstrategiaBusca.HIBRIDA : requisicao.estrategia();
        FiltrosBusca filtros = requisicao.filtros() == null ? FiltrosBusca.vazios() : requisicao.filtros();
        List<MensagemMemoria> memoriaSegura = memoria == null ? List.of() : List.copyOf(memoria);
        String consultaBusca = construirConsultaBusca(perguntaLimpa, memoriaSegura);
        var resultadoBusca = busca.buscar(espacoId, consultaBusca, usuarioId, estrategia, filtros);
        var recuperadas = resultadoBusca.fontes();
        List<FonteContexto> fontes = new ArrayList<>();
        for (int indice = 0; indice < recuperadas.size(); indice++) {
            var fonte = recuperadas.get(indice);
            fontes.add(new FonteContexto("F" + (indice + 1), fonte.trechoId(), fonte.documentoId(),
                    fonte.tituloDocumento(), fonte.ordemTrecho(), fonte.conteudo(), fonte.pontuacao()));
        }

        ResultadoGeracao geracao = fontes.isEmpty()
                ? new ResultadoGeracao(RESPOSTA_SEM_CONTEXTO, "recusa-segura")
                : gerador.gerar(perguntaLimpa, fontes, memoriaSegura);
        Validacao validacao = validador.validar(geracao.texto(), fontes);
        List<FonteContexto> citadas = fontes.stream()
                .filter(fonte -> validacao.marcadores().contains(fonte.marcador()))
                .toList();
        long latenciaMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
        UUID consultaId = UUID.randomUUID();
        Instant criadaEm = Instant.now(relogio);

        repositorio.salvar(consultaId, espacoId, usuarioId, perguntaLimpa, validacao.texto(),
                validacao.recusa(), geracao.provedor(), resultadoBusca.indiceId(), resultadoBusca.modeloEmbedding(),
                resultadoBusca.estrategia(), geracao.tokensEntrada(), geracao.tokensSaida(),
                geracao.custoEstimadoUsd(), latenciaMs, criadaEm, citadas);
        governanca.registrarConsumoIa(espacoId, provedor(geracao.provedor()), geracao.provedor(), "RESPOSTA",
                geracao.tokensEntrada(), geracao.tokensSaida(), geracao.custoEstimadoUsd());
        metricas.counter("contextpilot.rag.consultas", "resultado", validacao.recusa() ? "recusa" : "respondida").increment();
        metricas.timer("contextpilot.rag.latencia", "provedor", geracao.provedor())
                .record(latenciaMs, TimeUnit.MILLISECONDS);
        auditoria.registrar(espacoId, usuarioId, "CONSULTA_RAG_REALIZADA", "CONSULTA", consultaId.toString(),
                Map.of("recusada", validacao.recusa(), "fontes", citadas.size(), "provedor", geracao.provedor(),
                        "modeloEmbedding", resultadoBusca.modeloEmbedding(),
                        "estrategia", resultadoBusca.estrategia().name()));

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

    private String provedor(String nome) {
        int separador = nome.indexOf(':');
        return separador < 0 ? nome : nome.substring(0, separador);
    }

    private String construirConsultaBusca(String pergunta, List<MensagemMemoria> memoria) {
        StringBuilder consulta = new StringBuilder();
        memoria.stream()
                .filter(mensagem -> mensagem.papel() == PapelMemoria.USUARIO)
                .skip(Math.max(0, memoria.stream().filter(m -> m.papel() == PapelMemoria.USUARIO).count() - 2))
                .map(MensagemMemoria::conteudo)
                .map(String::trim)
                .filter(texto -> !texto.isBlank())
                .forEach(texto -> consulta.append(texto, 0, Math.min(texto.length(), 1000)).append('\n'));
        consulta.append(pergunta);
        return consulta.length() <= 3000 ? consulta.toString() : consulta.substring(consulta.length() - 3000);
    }

}
