package br.com.contextpilot.reindex;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.contextpilot.audit.AuditService;
import br.com.contextpilot.reindex.EmbeddingIndexModels.CriarIndiceRequest;
import br.com.contextpilot.reindex.EmbeddingIndexModels.EstadoIndice;
import br.com.contextpilot.reindex.EmbeddingIndexModels.IndiceEmbeddingResponse;
import br.com.contextpilot.retrieval.EmbeddingProvider;
import br.com.contextpilot.retrieval.EmbeddingProviderRegistry;
import br.com.contextpilot.shared.domain.BusinessRuleException;
import br.com.contextpilot.shared.domain.ConflictException;
import br.com.contextpilot.shared.domain.ResourceNotFoundException;
import br.com.contextpilot.workspace.WorkspaceAccessService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmbeddingIndexService {

    private final EmbeddingIndexRepository repositorio;
    private final EmbeddingProviderRegistry provedores;
    private final WorkspaceAccessService acesso;
    private final AuditService auditoria;
    private final MeterRegistry metricas;
    private final Clock relogio;

    public EmbeddingIndexService(
            EmbeddingIndexRepository repositorio,
            EmbeddingProviderRegistry provedores,
            WorkspaceAccessService acesso,
            AuditService auditoria,
            MeterRegistry metricas,
            Clock relogio) {
        this.repositorio = repositorio;
        this.provedores = provedores;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.metricas = metricas;
        this.relogio = relogio;
    }

    @Transactional
    public void criarInicial(UUID espacoId, String usuarioId) {
        EmbeddingProvider provedor = provedores.padrao();
        repositorio.criar(UUID.randomUUID(), espacoId, provedor.provedor(), provedor.nome(),
                provedor.dimensoes(), EstadoIndice.ATIVO, usuarioId, Instant.now(relogio));
    }

    public List<IndiceEmbeddingResponse> listar(UUID espacoId, String usuarioId) {
        acesso.exigirMembro(espacoId, usuarioId);
        return repositorio.listar(espacoId);
    }

    public List<EmbeddingProviderRegistry.ModeloDisponivel> listarModelos() {
        return provedores.listar();
    }

    @Transactional
    public IndiceEmbeddingResponse iniciar(UUID espacoId, CriarIndiceRequest requisicao, String usuarioId) {
        acesso.exigirProprietario(espacoId, usuarioId);
        String modelo = requisicao.modelo().trim();
        provedores.validarModelo(modelo);
        IndiceEmbeddingResponse ativo = obterAtivo(espacoId);
        if (ativo.modelo().equals(modelo)) {
            throw new BusinessRuleException("O modelo informado ja esta ativo neste espaco.");
        }
        EmbeddingProvider provedor = provedores.obter(modelo);
        UUID id = UUID.randomUUID();
        try {
            repositorio.criar(id, espacoId, provedor.provedor(), provedor.nome(), provedor.dimensoes(),
                    EstadoIndice.CONSTRUINDO, usuarioId, Instant.now(relogio));
        } catch (DuplicateKeyException excecao) {
            throw new ConflictException("Ja existe uma reindexacao em andamento neste espaco.");
        }
        auditoria.registrar(espacoId, usuarioId, "REINDEXACAO_INICIADA", "INDICE_EMBEDDING", id.toString(),
                Map.of("modeloAnterior", ativo.modelo(), "modeloNovo", modelo));
        metricas.counter("contextpilot.reindexacao.total", "resultado", "iniciada").increment();
        return repositorio.buscar(id, espacoId).orElseThrow();
    }

    @Transactional
    public IndiceEmbeddingResponse ativar(UUID espacoId, UUID indiceId, String usuarioId) {
        acesso.exigirProprietario(espacoId, usuarioId);
        IndiceEmbeddingResponse indice = repositorio.buscar(indiceId, espacoId)
                .orElseThrow(() -> new ResourceNotFoundException("Indice de embedding nao encontrado."));
        if (indice.estado() == EstadoIndice.FALHOU || indice.estado() == EstadoIndice.CONSTRUINDO) {
            throw new BusinessRuleException("Somente um indice completo pode ser ativado.");
        }
        provedores.validarModelo(indice.modelo());
        ativarCompleto(espacoId, indiceId);
        auditoria.registrar(espacoId, usuarioId, "INDICE_EMBEDDING_ATIVADO", "INDICE_EMBEDDING",
                indiceId.toString(), Map.of("modelo", indice.modelo(), "operacao", "rollback_manual"));
        return repositorio.buscar(indiceId, espacoId).orElseThrow();
    }

    @Transactional
    public void ativarConcluido(UUID espacoId, UUID indiceId) {
        ativarCompleto(espacoId, indiceId);
        metricas.counter("contextpilot.reindexacao.total", "resultado", "concluida").increment();
    }

    public IndiceEmbeddingResponse obterAtivo(UUID espacoId) {
        return repositorio.buscarAtivo(espacoId)
                .orElseThrow(() -> new IllegalStateException("O espaco nao possui indice de embedding ativo."));
    }

    @Transactional
    public void exigirIndiceAtivo(UUID espacoId, UUID indiceId) {
        repositorio.bloquearEspaco(espacoId);
        UUID indiceAtivoId = obterAtivo(espacoId).id();
        if (!indiceAtivoId.equals(indiceId)) {
            throw new ConflictException(
                    "O indice ativo mudou durante a ingestao; o documento sera reprocessado.");
        }
    }

    private void ativarCompleto(UUID espacoId, UUID indiceId) {
        repositorio.bloquearEspaco(espacoId);
        int total = repositorio.contarTrechos(espacoId);
        int vetores = repositorio.contarVetores(indiceId);
        if (vetores != total) {
            throw new ConflictException(
                    "O indice possui %d de %d vetores e ainda nao pode ser ativado.".formatted(vetores, total));
        }
        repositorio.ativar(indiceId, espacoId, Instant.now(relogio));
    }
}
