package br.com.contextpilot.conversation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.contextpilot.answer.AnswerModels.PerguntarRequest;
import br.com.contextpilot.answer.AnswerModels.RespostaRag;
import br.com.contextpilot.answer.AnswerService;
import br.com.contextpilot.audit.AuditService;
import br.com.contextpilot.configuration.ConversationProperties;
import br.com.contextpilot.conversation.ConversationModels.AtualizarConversaRequest;
import br.com.contextpilot.conversation.ConversationModels.ConversaDetalhe;
import br.com.contextpilot.conversation.ConversationModels.ConversaResumo;
import br.com.contextpilot.conversation.ConversationModels.CriarConversaRequest;
import br.com.contextpilot.conversation.ConversationModels.EstadoConversa;
import br.com.contextpilot.conversation.ConversationModels.InteracaoConversa;
import br.com.contextpilot.shared.domain.BusinessRuleException;
import br.com.contextpilot.shared.domain.ConflictException;
import br.com.contextpilot.shared.domain.ResourceNotFoundException;
import br.com.contextpilot.workspace.WorkspaceAccessService;
import br.com.contextpilot.retrieval.RetrievalModels.EstrategiaBusca;
import br.com.contextpilot.retrieval.RetrievalModels.FiltrosBusca;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ConversationService {

    private static final int LIMITE_MENSAGENS_DETALHE = 500;

    private final ConversationRepository repositorio;
    private final WorkspaceAccessService acessoEspaco;
    private final AnswerService respostas;
    private final AuditService auditoria;
    private final ConversationProperties propriedades;
    private final TransactionTemplate transacoes;
    private final MeterRegistry metricas;
    private final Clock relogio;

    public ConversationService(
            ConversationRepository repositorio,
            WorkspaceAccessService acessoEspaco,
            AnswerService respostas,
            AuditService auditoria,
            ConversationProperties propriedades,
            TransactionTemplate transacoes,
            MeterRegistry metricas,
            Clock relogio) {
        this.repositorio = repositorio;
        this.acessoEspaco = acessoEspaco;
        this.respostas = respostas;
        this.auditoria = auditoria;
        this.propriedades = propriedades;
        this.transacoes = transacoes;
        this.metricas = metricas;
        this.relogio = relogio;
    }

    @Transactional
    public ConversaResumo criar(UUID espacoId, CriarConversaRequest requisicao, String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        String titulo = limparTitulo(requisicao.titulo());
        UUID id = UUID.randomUUID();
        repositorio.criar(id, espacoId, usuarioId, titulo, Instant.now(relogio));
        auditoria.registrar(espacoId, usuarioId, "CONVERSA_CRIADA", "CONVERSA", id.toString(),
                Map.of("titulo", titulo));
        return exigirConversa(id, espacoId, usuarioId);
    }

    public List<ConversaResumo> listar(UUID espacoId, String usuarioId, int limite) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        return repositorio.listar(espacoId, usuarioId, Math.max(1, Math.min(limite, 100)));
    }

    public ConversaDetalhe buscar(UUID espacoId, UUID conversaId, String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        ConversaResumo conversa = exigirConversa(conversaId, espacoId, usuarioId);
        return new ConversaDetalhe(conversa,
                repositorio.listarMensagens(conversaId, LIMITE_MENSAGENS_DETALHE));
    }

    @Transactional
    public ConversaResumo atualizar(
            UUID espacoId,
            UUID conversaId,
            AtualizarConversaRequest requisicao,
            String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        exigirConversa(conversaId, espacoId, usuarioId);
        String titulo = requisicao.titulo() == null ? null : requisicao.titulo().trim();
        if (titulo != null && titulo.isBlank()) {
            throw new BusinessRuleException("O titulo da conversa nao pode ficar vazio.");
        }
        if (titulo == null && requisicao.estado() == null) {
            throw new BusinessRuleException("Informe o titulo ou o estado que deseja alterar.");
        }
        if (repositorio.atualizar(conversaId, espacoId, usuarioId, titulo,
                requisicao.estado(), Instant.now(relogio)) == 0) {
            throw new ConflictException("A conversa esta processando uma resposta e nao pode ser alterada agora.");
        }
        auditoria.registrar(espacoId, usuarioId, "CONVERSA_ATUALIZADA", "CONVERSA", conversaId.toString(),
                Map.of("estado", requisicao.estado() == null ? "MANTIDO" : requisicao.estado().name(),
                        "tituloAlterado", titulo != null));
        return exigirConversa(conversaId, espacoId, usuarioId);
    }

    public InteracaoConversa perguntar(
            UUID espacoId,
            UUID conversaId,
            PerguntarRequest requisicao,
            String usuarioId) {
        return perguntar(espacoId, conversaId, requisicao, usuarioId, null);
    }

    public InteracaoConversa perguntar(
            UUID espacoId,
            UUID conversaId,
            PerguntarRequest requisicao,
            String usuarioId,
            String chaveIdempotencia) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        ConversaResumo conversa = exigirConversa(conversaId, espacoId, usuarioId);
        if (conversa.estado() != EstadoConversa.ATIVA) {
            throw new BusinessRuleException("Reative a conversa antes de enviar uma nova mensagem.");
        }
        String chave = validarChaveIdempotencia(chaveIdempotencia);
        String impressaoRequisicao = construirImpressao(requisicao);
        if (chave != null) {
            var anterior = repositorio.buscarInteracaoIdempotente(conversaId, chave);
            if (anterior.isPresent()) {
                var persistida = anterior.orElseThrow();
                if (!impressaoRequisicao.equals(persistida.impressaoRequisicao())) {
                    throw new ConflictException("Idempotency-Key ja foi usada com outra requisicao.");
                }
                RespostaRag resposta = respostas.buscar(
                        espacoId, persistida.mensagemAssistente().consultaId(), usuarioId);
                metricas.counter("contextpilot.conversas.idempotencia", "resultado", "reutilizada").increment();
                return new InteracaoConversa(conversaId, persistida.versao(), persistida.mensagemUsuario(),
                        persistida.mensagemAssistente(), resposta);
            }
        }

        Instant agora = Instant.now(relogio);
        UUID token = UUID.randomUUID();
        boolean reservada = repositorio.adquirirLease(conversaId, espacoId, usuarioId, token,
                agora, agora.plus(propriedades.tempoLease()));
        if (!reservada) {
            throw new ConflictException("Ja existe uma resposta sendo processada nesta conversa.");
        }

        boolean concluida = false;
        try {
            var memoria = repositorio.listarMemoria(conversaId, propriedades.limiteMensagensMemoria());
            RespostaRag resposta = respostas.perguntar(espacoId, requisicao, usuarioId, memoria);
            String tituloAutomatico = resumirTitulo(requisicao.pergunta());
            var persistida = transacoes.execute(status -> repositorio.concluirInteracao(
                    conversaId, espacoId, usuarioId, token, resposta.consultaId(), requisicao.pergunta().trim(),
                    resposta.resposta(), chave, impressaoRequisicao, tituloAutomatico, Instant.now(relogio)));
            if (persistida == null) {
                throw new IllegalStateException("A interacao nao foi persistida.");
            }
            concluida = true;
            metricas.counter("contextpilot.conversas.interacoes", "resultado",
                    resposta.recusada() ? "recusa" : "respondida").increment();
            auditoria.registrar(espacoId, usuarioId, "MENSAGEM_CONVERSA_RESPONDIDA", "CONVERSA",
                    conversaId.toString(), Map.of("consultaId", resposta.consultaId().toString(),
                            "versao", persistida.versao(), "fontes", resposta.fontes().size()));
            return new InteracaoConversa(conversaId, persistida.versao(), persistida.mensagemUsuario(),
                    persistida.mensagemAssistente(), resposta);
        } finally {
            if (!concluida) {
                repositorio.liberarLease(conversaId, token);
            }
        }
    }

    @Transactional
    public void excluir(UUID espacoId, UUID conversaId, String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        exigirConversa(conversaId, espacoId, usuarioId);
        if (repositorio.excluir(conversaId, espacoId, usuarioId) == 0) {
            throw new ConflictException("A conversa esta processando uma resposta e nao pode ser excluida agora.");
        }
        auditoria.registrar(espacoId, usuarioId, "CONVERSA_EXCLUIDA", "CONVERSA", conversaId.toString(), Map.of());
    }

    private ConversaResumo exigirConversa(UUID conversaId, UUID espacoId, String usuarioId) {
        return repositorio.buscar(conversaId, espacoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa nao encontrada."));
    }

    private String limparTitulo(String titulo) {
        return titulo == null || titulo.isBlank() ? "Nova conversa" : titulo.trim();
    }

    private String resumirTitulo(String pergunta) {
        String titulo = pergunta.trim().replaceAll("\\s+", " ");
        return titulo.length() <= 80 ? titulo : titulo.substring(0, 77) + "...";
    }

    private String validarChaveIdempotencia(String chave) {
        if (chave == null || chave.isBlank()) {
            return null;
        }
        String limpa = chave.trim();
        if (limpa.length() > 120 || !limpa.matches("[A-Za-z0-9._:-]+")) {
            throw new BusinessRuleException(
                    "Idempotency-Key deve ter ate 120 caracteres alfanumericos, ponto, dois-pontos, hifen ou sublinhado.");
        }
        return limpa;
    }

    private String construirImpressao(PerguntarRequest requisicao) {
        EstrategiaBusca estrategia = requisicao.estrategia() == null
                ? EstrategiaBusca.HIBRIDA : requisicao.estrategia();
        FiltrosBusca filtros = requisicao.filtros() == null ? FiltrosBusca.vazios() : requisicao.filtros();
        String base = requisicao.pergunta().trim() + "\n" + estrategia.name() + "\n" + filtros;
        try {
            byte[] resumo = MessageDigest.getInstance("SHA-256")
                    .digest(base.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumo);
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("SHA-256 nao esta disponivel.", excecao);
        }
    }
}
