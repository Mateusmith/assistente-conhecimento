package br.com.contextpilot.governance;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.contextpilot.audit.AuditService;
import br.com.contextpilot.governance.GovernanceModels.AtualizarGovernancaRequest;
import br.com.contextpilot.governance.GovernanceModels.ConsumoIaResponse;
import br.com.contextpilot.governance.GovernanceModels.GovernancaResponse;
import br.com.contextpilot.governance.GovernanceModels.UsoEspacoResponse;
import br.com.contextpilot.shared.domain.BusinessRuleException;
import br.com.contextpilot.shared.domain.RateLimitExceededException;
import br.com.contextpilot.workspace.WorkspaceAccessService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceService {

    private final GovernanceRepository repositorio;
    private final DistributedCounter contadores;
    private final WorkspaceAccessService acesso;
    private final AuditService auditoria;
    private final MeterRegistry metricas;
    private final Clock relogio;

    public GovernanceService(
            GovernanceRepository repositorio,
            DistributedCounter contadores,
            WorkspaceAccessService acesso,
            AuditService auditoria,
            MeterRegistry metricas,
            Clock relogio) {
        this.repositorio = repositorio;
        this.contadores = contadores;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.metricas = metricas;
        this.relogio = relogio;
    }

    public void reservarConsulta(UUID espacoId) {
        GovernancaResponse governanca = obter(espacoId);
        int persistidas = repositorio.consultasHoje(espacoId);
        var contador = contadores.incrementar("quota:consultas:" + espacoId + ":" + hoje(),
                persistidas, ateFimDoDia());
        if (contador.valor() > governanca.limiteConsultasDia()) {
            metricas.counter("contextpilot.governanca.quotas", "tipo", "consulta", "resultado", "bloqueada")
                    .increment();
            throw new RateLimitExceededException("A quota diaria de consultas deste espaco foi atingida.",
                    contador.validadeRestante().toSeconds());
        }
    }

    @Transactional
    public void reservarUpload(UUID espacoId, long tamanhoBytes) {
        repositorio.bloquearEspaco(espacoId);
        GovernancaResponse governanca = obter(espacoId);
        long usado = repositorio.armazenamentoUsado(espacoId);
        if (usado + tamanhoBytes > governanca.limiteArmazenamentoBytes()) {
            metricas.counter("contextpilot.governanca.quotas", "tipo", "armazenamento", "resultado", "bloqueada")
                    .increment();
            throw new BusinessRuleException("O upload excede a quota de armazenamento do espaco.");
        }
        int persistidos = repositorio.uploadsHoje(espacoId);
        var contador = contadores.incrementar("quota:uploads:" + espacoId + ":" + hoje(),
                persistidos, ateFimDoDia());
        if (contador.valor() > governanca.limiteUploadsDia()) {
            throw new RateLimitExceededException("A quota diaria de uploads deste espaco foi atingida.",
                    contador.validadeRestante().toSeconds());
        }
    }

    @Transactional
    public GovernancaResponse atualizar(
            UUID espacoId, AtualizarGovernancaRequest requisicao, String usuarioId) {
        acesso.exigirProprietario(espacoId, usuarioId);
        long usado = repositorio.armazenamentoUsado(espacoId);
        if (requisicao.limiteArmazenamentoBytes() < usado) {
            throw new BusinessRuleException("O novo limite nao pode ser menor que o armazenamento ja utilizado.");
        }
        var governanca = new GovernancaResponse(espacoId, requisicao.limiteArmazenamentoBytes(),
                requisicao.limiteConsultasDia(), requisicao.limiteUploadsDia(),
                requisicao.retencaoConsultasDias());
        repositorio.atualizar(governanca);
        auditoria.registrar(espacoId, usuarioId, "GOVERNANCA_ATUALIZADA", "ESPACO", espacoId.toString(),
                Map.of("limiteConsultasDia", governanca.limiteConsultasDia(),
                        "limiteUploadsDia", governanca.limiteUploadsDia(),
                        "retencaoConsultasDias", governanca.retencaoConsultasDias()));
        return governanca;
    }

    public GovernancaResponse buscar(UUID espacoId, String usuarioId) {
        acesso.exigirProprietario(espacoId, usuarioId);
        return obter(espacoId);
    }

    public UsoEspacoResponse consultarUso(UUID espacoId, String usuarioId) {
        acesso.exigirProprietario(espacoId, usuarioId);
        GovernancaResponse governanca = obter(espacoId);
        List<ConsumoIaResponse> consumo = repositorio.listarConsumo(espacoId, hoje().minusDays(29));
        long entrada = consumo.stream().mapToLong(ConsumoIaResponse::tokensEntrada).sum();
        long saida = consumo.stream().mapToLong(ConsumoIaResponse::tokensSaida).sum();
        BigDecimal custo = consumo.stream().map(ConsumoIaResponse::custoEstimadoUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new UsoEspacoResponse(espacoId, hoje(), repositorio.armazenamentoUsado(espacoId),
                governanca.limiteArmazenamentoBytes(), repositorio.consultasHoje(espacoId),
                governanca.limiteConsultasDia(), repositorio.uploadsHoje(espacoId),
                governanca.limiteUploadsDia(), entrada, saida, custo, consumo);
    }

    public void registrarConsumoIa(
            UUID espacoId,
            String provedor,
            String modelo,
            String operacao,
            int tokensEntrada,
            int tokensSaida,
            BigDecimal custo) {
        registrarConsumoIa(espacoId, provedor, modelo, operacao, 1,
                tokensEntrada, tokensSaida, custo);
    }

    public void registrarConsumoIa(
            UUID espacoId,
            String provedor,
            String modelo,
            String operacao,
            long chamadas,
            int tokensEntrada,
            int tokensSaida,
            BigDecimal custo) {
        repositorio.registrarConsumo(espacoId, hoje(), provedor, modelo, operacao,
                chamadas, tokensEntrada, tokensSaida, custo == null ? BigDecimal.ZERO : custo);
        metricas.counter("contextpilot.ia.tokens", "tipo", "entrada").increment(tokensEntrada);
        metricas.counter("contextpilot.ia.tokens", "tipo", "saida").increment(tokensSaida);
        metricas.counter("contextpilot.ia.custo_estimado_usd").increment(custo == null ? 0 : custo.doubleValue());
    }

    private GovernancaResponse obter(UUID espacoId) {
        return repositorio.buscar(espacoId)
                .orElseThrow(() -> new IllegalStateException("Governanca do espaco nao foi encontrada."));
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio.withZone(ZoneOffset.UTC));
    }

    private Duration ateFimDoDia() {
        Instant agora = Instant.now(relogio);
        Instant fim = hoje().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return Duration.between(agora, fim).plusHours(1);
    }
}
