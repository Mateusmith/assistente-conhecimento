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

import br.com.contextpilot.configuration.StorageProperties;
import br.com.contextpilot.document.DocumentIngestionService;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ContextPilotApplicationTests {

    @Container
    static final PostgreSQLContainer BANCO = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-09-07T16-13-09Z"))
            .withEnv("MINIO_ROOT_USER", "contextpilot")
            .withEnv("MINIO_ROOT_PASSWORD", "contextpilot_storage_local")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    @DynamicPropertySource
    static void configurarInfraestrutura(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.datasource.url", BANCO::getJdbcUrl);
        propriedades.add("spring.datasource.username", BANCO::getUsername);
        propriedades.add("spring.datasource.password", BANCO::getPassword);
        propriedades.add("contextpilot.armazenamento.endpoint",
                () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        propriedades.add("contextpilot.armazenamento.chave-acesso", () -> "contextpilot");
        propriedades.add("contextpilot.armazenamento.chave-secreta", () -> "contextpilot_storage_local");
    }

    private final MockMvc http;
    private final ObjectMapper json;
    private final JdbcClient banco;
    private final DocumentIngestionService ingestao;
    private final S3Client s3;
    private final StorageProperties armazenamento;

    @Autowired
    ContextPilotApplicationTests(
            MockMvc http,
            ObjectMapper json,
            JdbcClient banco,
            DocumentIngestionService ingestao,
            S3Client s3,
            StorageProperties armazenamento) {
        this.http = http;
        this.json = json;
        this.banco = banco;
        this.ingestao = ingestao;
        this.s3 = s3;
        this.armazenamento = armazenamento;
    }

    @Test
    void deveSubirContextoComPgvectorEMigracoes() {
        Integer extensao = banco.sql("SELECT COUNT(*) FROM pg_extension WHERE extname = 'vector'")
                .query(Integer.class).single();
        Integer migracoes = banco.sql("SELECT COUNT(*) FROM flyway_schema_history WHERE success = true")
                .query(Integer.class).single();

        assertThat(extensao).isOne();
        assertThat(migracoes).isEqualTo(2);
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
        String envio = enviarDocumentoRestrito(espacoId);
        UUID documentoId = UUID.fromString(json.readTree(envio).path("id").asText());
        assertThat(json.readTree(envio).path("armazenamento").asText()).isEqualTo("S3");
        assertThat(json.readTree(envio).path("resultadoAntivirus").asText()).isEqualTo("NAO_VERIFICADO");
        var armazenamento = banco.sql("""
                        SELECT armazenamento, chave_armazenamento, conteudo_original IS NULL AS sem_bytea
                          FROM documentos
                         WHERE id = :id
                        """)
                .param("id", documentoId)
                .query((rs, linha) -> java.util.Map.of(
                        "tipo", rs.getString("armazenamento"),
                        "chave", rs.getString("chave_armazenamento"),
                        "semBytea", rs.getBoolean("sem_bytea")))
                .single();
        assertThat(armazenamento).containsEntry("tipo", "S3").containsEntry("semBytea", true);
        assertThat(armazenamento.get("chave").toString()).contains(documentoId.toString());
        ingestao.consumirFila();

        http.perform(get("/api/v1/espacos/{espacoId}/documentos/{documentoId}/conteudo", espacoId, documentoId)
                        .with(usuario("ana")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("30 dias"));

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

    @Test
    void deveContinuarLendoDocumentoLegadoArmazenadoNoBanco() throws Exception {
        UUID espacoId = criarEspaco();
        UUID documentoId = UUID.randomUUID();
        byte[] conteudo = "Conteudo preservado pela migracao retrocompativel.".getBytes(StandardCharsets.UTF_8);

        banco.sql("""
                        INSERT INTO documentos
                            (id, espaco_id, titulo, nome_arquivo, tipo_mime, visibilidade, estado,
                             hash_sha256, conteudo_original, tamanho_bytes, criado_por, processado_em)
                        VALUES
                            (:id, :espacoId, 'Documento legado', 'legado.txt', 'text/plain', 'ESPACO', 'PRONTO',
                             :hash, :conteudo, :tamanho, 'ana', CURRENT_TIMESTAMP)
                        """)
                .param("id", documentoId)
                .param("espacoId", espacoId)
                .param("hash", "a".repeat(64))
                .param("conteudo", conteudo)
                .param("tamanho", conteudo.length)
                .update();

        http.perform(get("/api/v1/espacos/{espacoId}/documentos/{documentoId}", espacoId, documentoId)
                        .with(usuario("ana")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.armazenamento").value("BANCO"))
                .andExpect(jsonPath("$.resultadoAntivirus").value("NAO_VERIFICADO"));

        http.perform(get("/api/v1/espacos/{espacoId}/documentos/{documentoId}/conteudo", espacoId, documentoId)
                        .with(usuario("ana")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(conteudo));
    }

    @Test
    void deveRecusarObjetoCujoConteudoNaoCorrespondeAoHash() throws Exception {
        UUID espacoId = criarEspaco();
        String envio = enviarDocumentoRestrito(espacoId);
        UUID documentoId = UUID.fromString(json.readTree(envio).path("id").asText());
        String chave = banco.sql("SELECT chave_armazenamento FROM documentos WHERE id = :id")
                .param("id", documentoId)
                .query(String.class)
                .single();

        byte[] adulterado = "conteudo adulterado no armazenamento".getBytes(StandardCharsets.UTF_8);
        s3.putObject(PutObjectRequest.builder()
                        .bucket(armazenamento.bucket())
                        .key(chave)
                        .contentType("text/plain")
                        .metadata(java.util.Map.of("sha256", "0".repeat(64)))
                        .build(),
                RequestBody.fromBytes(adulterado));

        http.perform(get("/api/v1/espacos/{espacoId}/documentos/{documentoId}/conteudo", espacoId, documentoId)
                        .with(usuario("ana")))
                .andExpect(status().isServiceUnavailable());
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

    private String enviarDocumentoRestrito(UUID espacoId) throws Exception {
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
        return resposta;
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
