package br.com.contextpilot.document;

import static br.com.contextpilot.document.DocumentModels.EstadoDocumento;
import static br.com.contextpilot.document.DocumentModels.VisibilidadeDocumento;
import static br.com.contextpilot.shared.domain.SqlTime.instante;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.contextpilot.document.DocumentModels.DocumentoParaIngestao;
import br.com.contextpilot.document.DocumentModels.DocumentoResponse;
import br.com.contextpilot.document.DocumentModels.ArmazenamentoDocumento;
import br.com.contextpilot.document.DocumentModels.NivelPermissaoDocumento;
import br.com.contextpilot.document.DocumentModels.OrigemTexto;
import br.com.contextpilot.document.DocumentModels.ReferenciaConteudo;
import br.com.contextpilot.document.DocumentModels.ResultadoAntivirus;
import br.com.contextpilot.document.DocumentModels.TarefaIngestao;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Repository
class DocumentRepository {

    private static final String SELECAO_DOCUMENTO = """
            SELECT d.id, d.espaco_id, d.titulo, d.nome_arquivo, d.tipo_mime, d.visibilidade,
                   d.estado, d.versao, d.tamanho_bytes, d.criado_por, d.criado_em,
                   d.processado_em, d.erro_processamento, d.armazenamento,
                   d.resultado_antivirus, d.verificado_antivirus_em, d.origem_texto, d.paginas_ocr,
                   d.metadados::text AS metadados
              FROM documentos d
            """;

    private final JdbcClient banco;
    private final ObjectMapper json;

    DocumentRepository(JdbcClient banco, ObjectMapper json) {
        this.banco = banco;
        this.json = json;
    }

    void criar(
            UUID id,
            UUID espacoId,
            String titulo,
            String nomeArquivo,
            String tipoMime,
            VisibilidadeDocumento visibilidade,
            String hash,
            String chaveArmazenamento,
            long tamanhoBytes,
            String metadadosJson,
            ResultadoAntivirus resultadoAntivirus,
            Instant verificadoAntivirusEm,
            String usuarioId,
            Instant instante) {
        banco.sql("""
                        INSERT INTO documentos
                            (id, espaco_id, titulo, nome_arquivo, tipo_mime, visibilidade, estado,
                             hash_sha256, armazenamento, chave_armazenamento, conteudo_original,
                             tamanho_bytes, metadados, resultado_antivirus, verificado_antivirus_em, criado_por, criado_em)
                        VALUES
                            (:id, :espacoId, :titulo, :nomeArquivo, :tipoMime, :visibilidade, 'PENDENTE',
                             :hash, 'S3', :chaveArmazenamento, NULL,
                             :tamanho, CAST(:metadados AS jsonb), :resultadoAntivirus,
                             :verificadoAntivirusEm, :usuarioId, :instante)
                        """)
                .param("id", id)
                .param("espacoId", espacoId)
                .param("titulo", titulo)
                .param("nomeArquivo", nomeArquivo)
                .param("tipoMime", tipoMime)
                .param("visibilidade", visibilidade.name())
                .param("hash", hash)
                .param("chaveArmazenamento", chaveArmazenamento)
                .param("tamanho", tamanhoBytes)
                .param("metadados", metadadosJson)
                .param("resultadoAntivirus", resultadoAntivirus.name())
                .param("verificadoAntivirusEm", verificadoAntivirusEm == null ? null : instante(verificadoAntivirusEm))
                .param("usuarioId", usuarioId)
                .param("instante", instante(instante))
                .update();

        banco.sql("""
                        INSERT INTO tarefas_ingestao (id, documento_id, estado, proxima_tentativa_em, criada_em)
                        VALUES (:id, :documentoId, 'PENDENTE', :instante, :instante)
                        """)
                .param("id", UUID.randomUUID())
                .param("documentoId", id)
                .param("instante", instante(instante))
                .update();
    }

    boolean existeHash(UUID espacoId, String hash) {
        return banco.sql("SELECT COUNT(*) FROM documentos WHERE espaco_id = :espacoId AND hash_sha256 = :hash")
                .param("espacoId", espacoId)
                .param("hash", hash)
                .query(Integer.class)
                .single() > 0;
    }

    Optional<DocumentoResponse> buscarAcessivel(UUID espacoId, UUID documentoId, String usuarioId) {
        return banco.sql(SELECAO_DOCUMENTO + """
                         WHERE d.id = :documentoId AND d.espaco_id = :espacoId
                           AND EXISTS (
                               SELECT 1 FROM membros_espaco m
                                WHERE m.espaco_id = d.espaco_id AND m.usuario_id = :usuarioId
                           )
                           AND (
                               d.visibilidade = 'ESPACO'
                               OR d.criado_por = :usuarioId
                               OR EXISTS (
                                   SELECT 1 FROM membros_espaco m
                                    WHERE m.espaco_id = d.espaco_id AND m.usuario_id = :usuarioId
                                      AND m.papel = 'PROPRIETARIO'
                               )
                               OR EXISTS (
                                   SELECT 1 FROM permissoes_documento p
                                    WHERE p.documento_id = d.id AND p.usuario_id = :usuarioId
                               )
                           )
                        """)
                .param("documentoId", documentoId)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .query(this::mapear)
                .optional();
    }

    List<DocumentoResponse> listarAcessiveis(UUID espacoId, String usuarioId) {
        return banco.sql(SELECAO_DOCUMENTO + """
                         WHERE d.espaco_id = :espacoId
                           AND EXISTS (
                               SELECT 1 FROM membros_espaco m
                                WHERE m.espaco_id = d.espaco_id AND m.usuario_id = :usuarioId
                           )
                           AND (
                               d.visibilidade = 'ESPACO'
                               OR d.criado_por = :usuarioId
                               OR EXISTS (
                                   SELECT 1 FROM membros_espaco m
                                    WHERE m.espaco_id = d.espaco_id AND m.usuario_id = :usuarioId
                                      AND m.papel = 'PROPRIETARIO'
                               )
                               OR EXISTS (
                                   SELECT 1 FROM permissoes_documento p
                                    WHERE p.documento_id = d.id AND p.usuario_id = :usuarioId
                               )
                           )
                         ORDER BY d.criado_em DESC
                        """)
                .param("espacoId", espacoId)
                .param("usuarioId", usuarioId)
                .query(this::mapear)
                .list();
    }

    Optional<DocumentoParaIngestao> buscarParaIngestao(UUID documentoId) {
        return banco.sql("""
                        SELECT id, espaco_id, nome_arquivo, tipo_mime, armazenamento,
                               chave_armazenamento, conteudo_original
                          FROM documentos
                         WHERE id = :documentoId
                        """)
                .param("documentoId", documentoId)
                .query((rs, linha) -> new DocumentoParaIngestao(
                        rs.getObject("id", UUID.class),
                        rs.getObject("espaco_id", UUID.class),
                        rs.getString("nome_arquivo"),
                        rs.getString("tipo_mime"),
                        mapearReferencia(rs)))
                .optional();
    }

    Optional<ReferenciaConteudo> obterReferenciaConteudo(UUID documentoId) {
        return banco.sql("""
                        SELECT armazenamento, chave_armazenamento, conteudo_original
                          FROM documentos
                         WHERE id = :documentoId
                        """)
                .param("documentoId", documentoId)
                .query((rs, linha) -> mapearReferencia(rs))
                .optional();
    }

    Optional<TarefaIngestao> reivindicarProximaTarefa(
            Instant instante, Instant bloqueadoAte, String trabalhadorId) {
        return banco.sql("""
                        WITH proxima AS (
                            SELECT id
                              FROM tarefas_ingestao
                             WHERE (estado = 'PENDENTE' AND proxima_tentativa_em <= :instante)
                                OR (estado = 'PROCESSANDO' AND (bloqueado_ate IS NULL OR bloqueado_ate <= :instante))
                             ORDER BY criada_em
                             FOR UPDATE SKIP LOCKED
                             LIMIT 1
                        )
                        UPDATE tarefas_ingestao t
                           SET estado = 'PROCESSANDO', tentativas = tentativas + 1,
                               iniciada_em = :instante, erro = NULL,
                               trabalhador_id = :trabalhadorId, bloqueado_ate = :bloqueadoAte
                          FROM proxima
                         WHERE t.id = proxima.id
                        RETURNING t.id, t.documento_id, t.tentativas
                        """)
                .param("instante", instante(instante))
                .param("bloqueadoAte", instante(bloqueadoAte))
                .param("trabalhadorId", trabalhadorId)
                .query((rs, linha) -> new TarefaIngestao(
                        rs.getObject("id", UUID.class),
                        rs.getObject("documento_id", UUID.class),
                        rs.getInt("tentativas")))
                .optional();
    }

    void marcarDocumentoProcessando(UUID documentoId) {
        banco.sql("UPDATE documentos SET estado = 'PROCESSANDO', erro_processamento = NULL WHERE id = :id")
                .param("id", documentoId)
                .update();
    }

    void substituirTrechos(
            UUID documentoId,
            UUID espacoId,
            UUID indiceId,
            List<String> trechos,
            List<String> vetores,
            List<Boolean> riscosPrompt,
            Instant instante) {
        banco.sql("SELECT id FROM espacos WHERE id = :espacoId FOR UPDATE")
                .param("espacoId", espacoId)
                .query(UUID.class)
                .single();
        banco.sql("DELETE FROM trechos_documento WHERE documento_id = :documentoId")
                .param("documentoId", documentoId)
                .update();

        for (int ordem = 0; ordem < trechos.size(); ordem++) {
            UUID trechoId = UUID.randomUUID();
            banco.sql("""
                            INSERT INTO trechos_documento
                                (id, documento_id, espaco_id, ordem, conteudo, embedding, risco_prompt, criado_em)
                            VALUES
                                (:id, :documentoId, :espacoId, :ordem, :conteudo,
                                 CAST(:embedding AS vector), :riscoPrompt, :instante)
                            """)
                    .param("id", trechoId)
                    .param("documentoId", documentoId)
                    .param("espacoId", espacoId)
                    .param("ordem", ordem)
                    .param("conteudo", trechos.get(ordem))
                    .param("embedding", vetores.get(ordem))
                    .param("riscoPrompt", riscosPrompt.get(ordem))
                    .param("instante", instante(instante))
                    .update();
            banco.sql("""
                            INSERT INTO vetores_trecho (indice_id, trecho_id, embedding, criado_em)
                            VALUES (:indiceId, :trechoId, CAST(:embedding AS vector), :instante)
                            """)
                    .param("indiceId", indiceId)
                    .param("trechoId", trechoId)
                    .param("embedding", vetores.get(ordem))
                    .param("instante", instante(instante))
                    .update();
        }
        banco.sql("""
                        UPDATE indices_embedding
                           SET total_trechos = (SELECT COUNT(*) FROM trechos_documento WHERE espaco_id = :espacoId),
                               trechos_processados = (SELECT COUNT(*) FROM vetores_trecho WHERE indice_id = :indiceId)
                         WHERE id = :indiceId AND estado = 'ATIVO'
                        """)
                .param("indiceId", indiceId)
                .param("espacoId", espacoId)
                .update();
    }

    void concluir(TarefaIngestao tarefa, OrigemTexto origemTexto, int paginasOcr, Instant instante) {
        banco.sql("""
                        UPDATE tarefas_ingestao
                           SET estado = 'CONCLUIDA', finalizada_em = :instante,
                               trabalhador_id = NULL, bloqueado_ate = NULL
                         WHERE id = :tarefaId
                        """)
                .param("tarefaId", tarefa.id())
                .param("instante", instante(instante))
                .update();
        banco.sql("""
                        UPDATE documentos
                           SET estado = 'PRONTO', processado_em = :instante, erro_processamento = NULL,
                               origem_texto = :origemTexto, paginas_ocr = :paginasOcr
                         WHERE id = :documentoId
                        """)
                .param("documentoId", tarefa.documentoId())
                .param("origemTexto", origemTexto.name())
                .param("paginasOcr", paginasOcr)
                .param("instante", instante(instante))
                .update();
    }

    void falhar(TarefaIngestao tarefa, String erro, Instant agora, Instant proximaTentativa) {
        boolean definitiva = tarefa.tentativa() >= 3;
        banco.sql("""
                        UPDATE tarefas_ingestao
                           SET estado = :estado, erro = :erro, proxima_tentativa_em = :proxima,
                               finalizada_em = CASE WHEN :definitiva THEN :agora ELSE NULL END
                               , trabalhador_id = NULL, bloqueado_ate = NULL
                         WHERE id = :tarefaId
                        """)
                .param("estado", definitiva ? "FALHOU" : "PENDENTE")
                .param("erro", erro)
                .param("proxima", instante(proximaTentativa))
                .param("definitiva", definitiva)
                .param("agora", instante(agora))
                .param("tarefaId", tarefa.id())
                .update();
        banco.sql("UPDATE documentos SET estado = :estado, erro_processamento = :erro WHERE id = :documentoId")
                .param("estado", definitiva ? "FALHOU" : "PENDENTE")
                .param("erro", erro)
                .param("documentoId", tarefa.documentoId())
                .update();
    }

    void concederPermissao(
            UUID documentoId,
            String usuarioId,
            NivelPermissaoDocumento nivel,
            String concedidoPor,
            Instant instante) {
        banco.sql("""
                        INSERT INTO permissoes_documento
                            (documento_id, usuario_id, nivel, concedido_por, concedido_em)
                        VALUES (:documentoId, :usuarioId, :nivel, :concedidoPor, :instante)
                        ON CONFLICT (documento_id, usuario_id)
                        DO UPDATE SET nivel = EXCLUDED.nivel, concedido_por = EXCLUDED.concedido_por,
                                      concedido_em = EXCLUDED.concedido_em
                        """)
                .param("documentoId", documentoId)
                .param("usuarioId", usuarioId)
                .param("nivel", nivel.name())
                .param("concedidoPor", concedidoPor)
                .param("instante", instante(instante))
                .update();
    }

    void reagendar(UUID documentoId, Instant instante) {
        banco.sql("""
                        UPDATE tarefas_ingestao
                           SET estado = 'PENDENTE', tentativas = 0, proxima_tentativa_em = :instante,
                               iniciada_em = NULL, finalizada_em = NULL, erro = NULL,
                               trabalhador_id = NULL, bloqueado_ate = NULL
                         WHERE documento_id = :documentoId
                        """)
                .param("documentoId", documentoId)
                .param("instante", instante(instante))
                .update();
        banco.sql("UPDATE documentos SET estado = 'PENDENTE', erro_processamento = NULL WHERE id = :documentoId")
                .param("documentoId", documentoId)
                .update();
    }

    private DocumentoResponse mapear(java.sql.ResultSet rs, int linha) throws java.sql.SQLException {
        var processadoEm = rs.getTimestamp("processado_em");
        return new DocumentoResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("espaco_id", UUID.class),
                rs.getString("titulo"),
                rs.getString("nome_arquivo"),
                rs.getString("tipo_mime"),
                ArmazenamentoDocumento.valueOf(rs.getString("armazenamento")),
                ResultadoAntivirus.valueOf(rs.getString("resultado_antivirus")),
                rs.getTimestamp("verificado_antivirus_em") == null
                        ? null : rs.getTimestamp("verificado_antivirus_em").toInstant(),
                rs.getString("origem_texto") == null ? null : OrigemTexto.valueOf(rs.getString("origem_texto")),
                rs.getInt("paginas_ocr"),
                lerMetadados(rs.getString("metadados")),
                VisibilidadeDocumento.valueOf(rs.getString("visibilidade")),
                EstadoDocumento.valueOf(rs.getString("estado")),
                rs.getInt("versao"),
                rs.getLong("tamanho_bytes"),
                rs.getString("criado_por"),
                rs.getTimestamp("criado_em").toInstant(),
                processadoEm == null ? null : processadoEm.toInstant(),
                rs.getString("erro_processamento"));
    }

    private JsonNode lerMetadados(String valor) {
        try {
            return json.readTree(valor == null ? "{}" : valor);
        } catch (JacksonException excecao) {
            throw new IllegalStateException("Metadados do documento estao corrompidos.", excecao);
        }
    }

    private ReferenciaConteudo mapearReferencia(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ReferenciaConteudo(
                ArmazenamentoDocumento.valueOf(rs.getString("armazenamento")),
                rs.getString("chave_armazenamento"),
                rs.getBytes("conteudo_original"));
    }
}
