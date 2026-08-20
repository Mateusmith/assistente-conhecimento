package br.com.contextpilot.privacy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import br.com.contextpilot.audit.AuditService;
import br.com.contextpilot.configuration.GovernanceProperties;
import br.com.contextpilot.privacy.PrivacyModels.ExclusaoPrivacidadeResponse;
import br.com.contextpilot.privacy.PrivacyModels.ExportacaoPrivacidade;
import br.com.contextpilot.shared.domain.ConflictException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivacyService {

    private final PrivacyRepository repositorio;
    private final AuditService auditoria;
    private final GovernanceProperties propriedades;
    private final MeterRegistry metricas;
    private final Clock relogio;

    public PrivacyService(
            PrivacyRepository repositorio,
            AuditService auditoria,
            GovernanceProperties propriedades,
            MeterRegistry metricas,
            Clock relogio) {
        this.repositorio = repositorio;
        this.auditoria = auditoria;
        this.propriedades = propriedades;
        this.metricas = metricas;
        this.relogio = relogio;
    }

    public ExportacaoPrivacidade exportar(String usuarioId) {
        metricas.counter("contextpilot.privacidade.operacoes", "tipo", "exportacao").increment();
        return new ExportacaoPrivacidade(usuarioId, Instant.now(relogio), repositorio.listarEspacos(usuarioId),
                repositorio.listarDocumentos(usuarioId), repositorio.listarConsultas(usuarioId),
                repositorio.listarFeedbacks(usuarioId), repositorio.listarEventosAuditoria(usuarioId));
    }

    @Transactional
    public ExclusaoPrivacidadeResponse excluir(String usuarioId) {
        List<String> espacos = repositorio.listarEspacosSobPropriedade(usuarioId);
        if (!espacos.isEmpty()) {
            throw new ConflictException(
                    "Transfira ou exclua os espacos sob sua propriedade antes de apagar os dados: "
                            + String.join(", ", espacos));
        }
        String pseudonimo = pseudonimizar(usuarioId);
        int consultas = repositorio.excluirConsultas(usuarioId);
        int vinculos = repositorio.excluirVinculosEPseudonimizar(usuarioId, pseudonimo);
        auditoria.registrar(null, pseudonimo, "DADOS_PESSOAIS_EXCLUIDOS", "USUARIO", pseudonimo,
                Map.of("consultasExcluidas", consultas, "vinculosExcluidos", vinculos));
        metricas.counter("contextpilot.privacidade.operacoes", "tipo", "exclusao").increment();
        return new ExclusaoPrivacidadeResponse(
                "CONCLUIDA", pseudonimo, consultas, vinculos, Instant.now(relogio));
    }

    @Scheduled(cron = "${contextpilot.privacidade.cron-retencao:0 20 2 * * *}")
    public void aplicarRetencao() {
        int excluidas = repositorio.expurgarConsultasVencidas();
        metricas.counter("contextpilot.privacidade.retencao", "resultado", "consulta_excluida")
                .increment(excluidas);
    }

    private String pseudonimizar(String usuarioId) {
        try {
            String base = propriedades.salPrivacidade() + ":" + usuarioId;
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
            return "anon-" + HexFormat.of().formatHex(hash).substring(0, 24);
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("SHA-256 nao esta disponivel.", excecao);
        }
    }
}
