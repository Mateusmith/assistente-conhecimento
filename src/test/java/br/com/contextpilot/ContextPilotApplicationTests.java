package br.com.contextpilot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import br.com.contextpilot.document.DocumentIngestionService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContextPilotApplicationTests {

    private final MockMvc http;
    private final ObjectMapper json;
    private final JdbcClient banco;
    private final DocumentIngestionService ingestao;

    @Autowired
    ContextPilotApplicationTests(MockMvc http, ObjectMapper json, JdbcClient banco, DocumentIngestionService ingestao) {
        this.http = http;
        this.json = json;
        this.banco = banco;
        this.ingestao = ingestao;
    }

    @Test
    void deveSubirContextoComPgvectorEMigracoes() {
        Integer extensao = banco.sql("SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'")
                .query(Integer.class).single();
        Integer migracoes = banco.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success = true")
                .query(Integer.class).single();

        assertThat(extensao).isOne();
        assertThat(migracoes).isOne();
    }

    @Test
    void deveProtegerApiEMetricas() throws Exception {
        http.perform(get("/actuator/health")).andExpect(status().isOk());
        http.perform(get("/api/v1/espacos")).andExpect(status().isUnauthorized());
        http.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        http.perform(get("/actuator/prometheus").with(httpBasic("prometheus", "contextpilot_metrics_local")))
                .andExpect(status().isOk());
        http.perform(get("/api/v1/espacos").with(httpBasic("prometheus", "contextpilot_metrics_local")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveExecutarRagComAclCitacoesFeedbackEAvaliacao() throws Exception {
        UUID espacoId = criarEspaco();
        adicionarMembro(espacoId, "carla", "LEITOR");
        UUID documentoId = enviarDocumentoRestrito(espacoId);
        ingestao.consumirFila();

        http.perform(get("/api/v1/espacos/{espacoId}/documentos/{documentoId}", espacoId, documentoId)
                        .with(usuario("carla")))
                .andExpect(status().isNotFound());

        http.perform(post("/api/v1/espacos/{espacoId}/consultas", espacoId)
                        .with(usuario("carla"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"Qual e o prazo para reembolso?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recusada").value(true))
                .andExpect(jsonPath("$.fontes.length()").value(0));

        http.perform(post("/api/v1/espacos/{espacoId}/documentos/{documentoId}/permissoes", espacoId, documentoId)
                        .with(usuario("ana"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"carla\",\"nivel\":\"LEITURA\"}"))
                .andExpect(status().isNoContent());

        String resposta = http.perform(post("/api/v1/espacos/{espacoId}/consultas", espacoId)
                        .with(usuario("carla"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pergunta\":\"Qual e o prazo para reembolso?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recusada").value(false))
                .andExpect(jsonPath("$.resposta").value(org.hamcrest.Matchers.containsString("[F1]")))
                .andExpect(jsonPath("$.fontes[0].documentoId").value(documentoId.toString()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID consultaId = UUID.fromString(json.readTree(resposta).path("consultaId").asText());

        http.perform(post("/api/v1/espacos/{espacoId}/consultas/{consultaId}/feedback", espacoId, consultaId)
                        .with(usuario("carla"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"util\":true,\"comentario\":\"Resposta objetiva e verificavel\"}"))
                .andExpect(status().isNoContent());

        UUID conjuntoId = criarConjunto(espacoId);
        adicionarCaso(espacoId, conjuntoId, documentoId);
        http.perform(post("/api/v1/espacos/{espacoId}/avaliacoes/{conjuntoId}/execucoes", espacoId, conjuntoId)
                        .with(usuario("ana")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CONCLUIDA"))
                .andExpect(jsonPath("$.casosAprovados").value(1))
                .andExpect(jsonPath("$.taxaAcerto").value(1.0));

        Integer eventos = banco.sql("SELECT COUNT(*) FROM eventos_auditoria WHERE espaco_id = :espacoId")
                .param("espacoId", espacoId).query(Integer.class).single();
        assertThat(eventos).isGreaterThanOrEqualTo(6);
    }

    private UUID criarEspaco() throws Exception {
        String resposta = http.perform(post("/api/v1/espacos")
                        .with(usuario("ana"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Operacoes Financeiras\",\"descricao\":\"Base de politicas internas\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(json.readTree(resposta).path("id").asText());
    }

    private void adicionarMembro(UUID espacoId, String usuarioId, String papel) throws Exception {
        http.perform(post("/api/v1/espacos/{espacoId}/membros", espacoId)
                        .with(usuario("ana"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuarioId\":\"%s\",\"papel\":\"%s\"}".formatted(usuarioId, papel)))
                .andExpect(status().isCreated());
    }

    private UUID enviarDocumentoRestrito(UUID espacoId) throws Exception {
        var arquivo = new MockMultipartFile("arquivo", "politica-reembolso.md", "text/markdown",
                """
                        # Politica de reembolso

                        O prazo para solicitar reembolso e de 30 dias apos a data da compra.
                        A solicitacao deve conter o numero do pedido e o motivo do cancelamento.
                        O pagamento aprovado retorna ao mesmo meio utilizado pelo cliente.
                        """.getBytes(StandardCharsets.UTF_8));
        String resposta = http.perform(multipart("/api/v1/espacos/{espacoId}/documentos", espacoId)
                        .file(arquivo)
                        .param("titulo", "Politica de reembolso")
                        .param("visibilidade", "RESTRITO")
                        .with(usuario("ana")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(json.readTree(resposta).path("id").asText());
    }

    private UUID criarConjunto(UUID espacoId) throws Exception {
        String resposta = http.perform(post("/api/v1/espacos/{espacoId}/avaliacoes", espacoId)
                        .with(usuario("ana"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\":\"Regressao financeira\",\"descricao\":\"Perguntas criticas\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(json.readTree(resposta).path("id").asText());
    }

    private void adicionarCaso(UUID espacoId, UUID conjuntoId, UUID documentoId) throws Exception {
        var corpo = json.createObjectNode();
        corpo.put("pergunta", "Qual e o prazo para reembolso?");
        corpo.putArray("termosEsperados").add("30 dias");
        corpo.putArray("documentosEsperados").add(documentoId.toString());
        corpo.put("deveRecusar", false);
        http.perform(post("/api/v1/espacos/{espacoId}/avaliacoes/{conjuntoId}/casos", espacoId, conjuntoId)
                        .with(usuario("ana"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(corpo)))
                .andExpect(status().isCreated());
    }

    private RequestPostProcessor usuario(String nome) {
        return jwt().jwt(token -> token.subject("id-" + nome).claim("preferred_username", nome));
    }
}
