package br.com.contextpilot.conversation;

import static br.com.contextpilot.shared.domain.SqlTime.instante;

import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contextpilot.answer.AnswerModels.MensagemMemoria;
import br.com.contextpilot.answer.AnswerModels.PapelMemoria;
import br.com.contextpilot.conversation.ConversationModels.ConversaResumo;
import br.com.contextpilot.conversation.ConversationModels.EstadoConversa;
import br.com.contextpilot.conversation.ConversationModels.MensagemConversa;
import br.com.contextpilot.conversation.ConversationModels.PapelMensagem;
import br.com.contextpilot.shared.domain.ConflictException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ConversationRepository {

    private final JdbcClient banco;

    ConversationRepository(JdbcClient banco) {
        this.banco = banco;
    }

    void criar(UUID id, UUID espacoId, String usuarioId, String titulo, Instant agora) {
        banco.sql("""
                        INSERT INTO conversas
                            (id, espaco_id, usuario_id, titulo, estado, criada_em, atualizada_em)
                        VALUES (:id, :espacoId, :usuarioId, :titulo, 'ATIVA', :agora, :agora)
                        """)
                .param("id", id)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .param("titulo", titulo)
                .param("agora", instante(agora))
                .update();
    }

    List<ConversaResumo> listar(UUID espacoId, String usuarioId, int limite) {
        return banco.sql("""
                        SELECT c.id, c.espaco_id, c.titulo, c.estado, c.versao,
                               COUNT(m.id) AS quantidade_mensagens, c.criada_em, c.atualizada_em
                          FROM conversas c
                          LEFT JOIN mensagens_conversa m ON m.conversa_id = c.id
                         WHERE c.espaco_id = :espacoId AND c.usuario_id = :usuarioId
                         GROUP BY c.id
                         ORDER BY c.atualizada_em DESC
                         LIMIT :limite
                        """)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .param("limite", limite)
                .query(this::mapearConversa)
                .list();
    }

    Optional<ConversaResumo> buscar(UUID id, UUID espacoId, String usuarioId) {
        return banco.sql("""
                        SELECT c.id, c.espaco_id, c.titulo, c.estado, c.versao,
                               COUNT(m.id) AS quantidade_mensagens, c.criada_em, c.atualizada_em
                          FROM conversas c
                          LEFT JOIN mensagens_conversa m ON m.conversa_id = c.id
                         WHERE c.id = :id AND c.espaco_id = :espacoId AND c.usuario_id = :usuarioId
                         GROUP BY c.id
                        """)
                .param("id", id)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .query(this::mapearConversa)
                .optional();
    }

    List<MensagemConversa> listarMensagens(UUID conversaId, int limite) {
        return banco.sql("""
                        SELECT id, conversa_id, consulta_id, sequencia, papel, conteudo, criada_em
                          FROM mensagens_conversa
                         WHERE conversa_id = :conversaId
                         ORDER BY sequencia
                         LIMIT :limite
                        """)
                .param("conversaId", conversaId)
                .param("limite", limite)
                .query(this::mapearMensagem)
                .list();
    }

    List<MensagemMemoria> listarMemoria(UUID conversaId, int limite) {
        return banco.sql("""
                        SELECT papel, conteudo
                          FROM (
                                SELECT sequencia, papel, conteudo
                                  FROM mensagens_conversa
                                 WHERE conversa_id = :conversaId
                                 ORDER BY sequencia DESC
                                 LIMIT :limite
                               ) historico
                         ORDER BY sequencia
                        """)
                .param("conversaId", conversaId)
                .param("limite", limite)
                .query((rs, linha) -> new MensagemMemoria(
                        PapelMemoria.valueOf(rs.getString("papel")), rs.getString("conteudo")))
                .list();
    }

    boolean adquirirLease(
            UUID conversaId,
            UUID espacoId,
            String usuarioId,
            UUID token,
            Instant agora,
            Instant leaseAte) {
        return banco.sql("""
                        UPDATE conversas
                           SET token_lease = :token, lease_ate = :leaseAte
                         WHERE id = :conversaId
                           AND espaco_id = :espacoId
                           AND usuario_id = :usuarioId
                           AND estado = 'ATIVA'
                           AND (token_lease IS NULL OR lease_ate < :agora)
                        """)
                .param("token", token)
                .param("leaseAte", instante(leaseAte))
                .param("conversaId", conversaId)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .param("agora", instante(agora))
                .update() == 1;
    }

    InteracaoPersistida concluirInteracao(
            UUID conversaId,
            UUID espacoId,
            String usuarioId,
            UUID token,
            UUID consultaId,
            String pergunta,
            String resposta,
            String chaveIdempotencia,
            String impressaoRequisicao,
            String tituloAutomatico,
            Instant agora) {
        Long versao = banco.sql("""
                        SELECT versao
                          FROM conversas
                         WHERE id = :conversaId AND espaco_id = :espacoId
                           AND usuario_id = :usuarioId AND token_lease = :token
                         FOR UPDATE
                        """)
                .param("conversaId", conversaId)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .param("token", token)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ConflictException("A reserva da conversa expirou antes da gravacao."));

        int proximaSequencia = banco.sql("""
                        SELECT COALESCE(MAX(sequencia), 0) + 1
                          FROM mensagens_conversa
                         WHERE conversa_id = :conversaId
                        """)
                .param("conversaId", conversaId)
                .query(Integer.class)
                .single();
        UUID mensagemUsuarioId = UUID.randomUUID();
        UUID mensagemAssistenteId = UUID.randomUUID();
        inserirMensagem(mensagemUsuarioId, conversaId, null, proximaSequencia,
                PapelMensagem.USUARIO, pergunta, chaveIdempotencia, impressaoRequisicao, agora);
        inserirMensagem(mensagemAssistenteId, conversaId, consultaId, proximaSequencia + 1,
                PapelMensagem.ASSISTENTE, resposta, null, null, agora);

        banco.sql("""
                        UPDATE conversas
                           SET titulo = CASE WHEN versao = 0 AND titulo = 'Nova conversa'
                                            THEN :tituloAutomatico ELSE titulo END,
                               versao = versao + 1, atualizada_em = :agora,
                               token_lease = NULL, lease_ate = NULL
                         WHERE id = :conversaId AND token_lease = :token
                        """)
                .param("tituloAutomatico", tituloAutomatico)
                .param("agora", instante(agora))
                .param("conversaId", conversaId)
                .param("token", token)
                .update();

        MensagemConversa mensagemUsuario = buscarMensagem(mensagemUsuarioId).orElseThrow();
        MensagemConversa mensagemAssistente = buscarMensagem(mensagemAssistenteId).orElseThrow();
        return new InteracaoPersistida(versao + 1, mensagemUsuario, mensagemAssistente, impressaoRequisicao);
    }

    Optional<InteracaoPersistida> buscarInteracaoIdempotente(UUID conversaId, String chaveIdempotencia) {
        Optional<CabecalhoInteracao> cabecalho = banco.sql("""
                        SELECT ((usuario.sequencia + 1) / 2)::BIGINT AS versao,
                               usuario.id AS mensagem_usuario_id,
                               assistente.id AS mensagem_assistente_id, usuario.impressao_requisicao
                          FROM mensagens_conversa usuario
                          JOIN mensagens_conversa assistente
                            ON assistente.conversa_id = usuario.conversa_id
                           AND assistente.sequencia = usuario.sequencia + 1
                           AND assistente.papel = 'ASSISTENTE'
                         WHERE usuario.conversa_id = :conversaId
                           AND usuario.chave_idempotencia = :chaveIdempotencia
                        """)
                .param("conversaId", conversaId)
                .param("chaveIdempotencia", chaveIdempotencia)
                .query((rs, linha) -> new CabecalhoInteracao(
                        rs.getLong("versao"),
                        rs.getObject("mensagem_usuario_id", UUID.class),
                        rs.getObject("mensagem_assistente_id", UUID.class),
                        rs.getString("impressao_requisicao")))
                .optional();
        return cabecalho.map(valor -> new InteracaoPersistida(
                valor.versao(),
                buscarMensagem(valor.mensagemUsuarioId()).orElseThrow(),
                buscarMensagem(valor.mensagemAssistenteId()).orElseThrow(),
                valor.impressaoRequisicao()));
    }

    void liberarLease(UUID conversaId, UUID token) {
        banco.sql("""
                        UPDATE conversas SET token_lease = NULL, lease_ate = NULL
                         WHERE id = :conversaId AND token_lease = :token
                        """)
                .param("conversaId", conversaId)
                .param("token", token)
                .update();
    }

    int atualizar(
            UUID id,
            UUID espacoId,
            String usuarioId,
            String titulo,
            EstadoConversa estado,
            Instant agora) {
        return banco.sql("""
                        UPDATE conversas
                           SET titulo = COALESCE(:titulo, titulo),
                               estado = COALESCE(:estado, estado),
                               atualizada_em = :agora
                         WHERE id = :id AND espaco_id = :espacoId AND usuario_id = :usuarioId
                           AND (token_lease IS NULL OR lease_ate < :agora)
                        """)
                .param("titulo", titulo, Types.VARCHAR)
                .param("estado", estado == null ? null : estado.name(), Types.VARCHAR)
                .param("agora", instante(agora))
                .param("id", id)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .update();
    }

    int excluir(UUID id, UUID espacoId, String usuarioId) {
        return banco.sql("""
                        DELETE FROM conversas
                         WHERE id = :id AND espaco_id = :espacoId AND usuario_id = :usuarioId
                           AND (token_lease IS NULL OR lease_ate < CURRENT_TIMESTAMP)
                        """)
                .param("id", id)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .update();
    }

    private void inserirMensagem(
            UUID id,
            UUID conversaId,
            UUID consultaId,
            int sequencia,
            PapelMensagem papel,
            String conteudo,
            String chaveIdempotencia,
            String impressaoRequisicao,
            Instant agora) {
        banco.sql("""
                        INSERT INTO mensagens_conversa
                            (id, conversa_id, consulta_id, sequencia, papel, conteudo,
                             chave_idempotencia, impressao_requisicao, criada_em)
                        VALUES (:id, :conversaId, :consultaId, :sequencia, :papel, :conteudo,
                                :chaveIdempotencia, :impressaoRequisicao, :agora)
                        """)
                .param("id", id)
                .param("conversaId", conversaId)
                .param("consultaId", consultaId, Types.OTHER)
                .param("sequencia", sequencia)
                .param("papel", papel.name())
                .param("conteudo", conteudo)
                .param("chaveIdempotencia", chaveIdempotencia, Types.VARCHAR)
                .param("impressaoRequisicao", impressaoRequisicao, Types.CHAR)
                .param("agora", instante(agora))
                .update();
    }

    private Optional<MensagemConversa> buscarMensagem(UUID id) {
        return banco.sql("""
                        SELECT id, conversa_id, consulta_id, sequencia, papel, conteudo, criada_em
                          FROM mensagens_conversa WHERE id = :id
                        """)
                .param("id", id)
                .query(this::mapearMensagem)
                .optional();
    }

    private ConversaResumo mapearConversa(java.sql.ResultSet rs, int linha) throws java.sql.SQLException {
        return new ConversaResumo(
                rs.getObject("id", UUID.class),
                rs.getObject("espaco_id", UUID.class),
                rs.getString("titulo"),
                EstadoConversa.valueOf(rs.getString("estado")),
                rs.getLong("versao"),
                rs.getInt("quantidade_mensagens"),
                rs.getTimestamp("criada_em").toInstant(),
                rs.getTimestamp("atualizada_em").toInstant());
    }

    private MensagemConversa mapearMensagem(java.sql.ResultSet rs, int linha) throws java.sql.SQLException {
        return new MensagemConversa(
                rs.getObject("id", UUID.class),
                rs.getObject("conversa_id", UUID.class),
                rs.getObject("consulta_id", UUID.class),
                rs.getInt("sequencia"),
                PapelMensagem.valueOf(rs.getString("papel")),
                rs.getString("conteudo"),
                rs.getTimestamp("criada_em").toInstant());
    }

    record InteracaoPersistida(
            long versao,
            MensagemConversa mensagemUsuario,
            MensagemConversa mensagemAssistente,
            String impressaoRequisicao) {
    }

    private record CabecalhoInteracao(
            long versao,
            UUID mensagemUsuarioId,
            UUID mensagemAssistenteId,
            String impressaoRequisicao) {
    }
}
