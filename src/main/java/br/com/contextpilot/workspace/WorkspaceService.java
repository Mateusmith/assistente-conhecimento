package br.com.contextpilot.workspace;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.contextpilot.audit.AuditService;
import br.com.contextpilot.reindex.EmbeddingIndexService;
import br.com.contextpilot.shared.domain.BusinessRuleException;
import br.com.contextpilot.shared.domain.ResourceNotFoundException;
import br.com.contextpilot.workspace.WorkspaceModels.AdicionarMembroRequest;
import br.com.contextpilot.workspace.WorkspaceModels.CriarEspacoRequest;
import br.com.contextpilot.workspace.WorkspaceModels.EspacoResponse;
import br.com.contextpilot.workspace.WorkspaceModels.MembroResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkspaceService {

    private final WorkspaceRepository repositorio;
    private final WorkspaceAccessService acesso;
    private final AuditService auditoria;
    private final EmbeddingIndexService indices;
    private final Clock relogio;

    public WorkspaceService(
            WorkspaceRepository repositorio,
            WorkspaceAccessService acesso,
            AuditService auditoria,
            EmbeddingIndexService indices,
            Clock relogio) {
        this.repositorio = repositorio;
        this.acesso = acesso;
        this.auditoria = auditoria;
        this.indices = indices;
        this.relogio = relogio;
    }

    @Transactional
    public EspacoResponse criar(CriarEspacoRequest requisicao, String usuarioId) {
        String nome = requisicao.nome().trim();
        if (nome.length() < 3) {
            throw new BusinessRuleException("O nome do espaco deve ter pelo menos 3 caracteres.");
        }

        UUID id = UUID.randomUUID();
        repositorio.criar(id, nome, limpar(requisicao.descricao()), usuarioId, Instant.now(relogio));
        indices.criarInicial(id, usuarioId);
        auditoria.registrar(id, usuarioId, "ESPACO_CRIADO", "ESPACO", id.toString(), Map.of("nome", nome));
        return repositorio.buscar(id, usuarioId).orElseThrow();
    }

    public List<EspacoResponse> listar(String usuarioId) {
        return repositorio.listarPorUsuario(usuarioId);
    }

    public EspacoResponse buscar(UUID espacoId, String usuarioId) {
        return repositorio.buscar(espacoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Espaco nao encontrado."));
    }

    @Transactional
    public MembroResponse adicionarMembro(UUID espacoId, AdicionarMembroRequest requisicao, String usuarioId) {
        acesso.exigirProprietario(espacoId, usuarioId);
        if (requisicao.papel() == WorkspaceModels.PapelMembro.PROPRIETARIO) {
            throw new BusinessRuleException("A propriedade do espaco nao pode ser transferida por esta operacao.");
        }

        String novoMembro = requisicao.usuarioId().trim();
        repositorio.salvarMembro(espacoId, novoMembro, requisicao.papel(), usuarioId, Instant.now(relogio));
        auditoria.registrar(espacoId, usuarioId, "MEMBRO_ADICIONADO", "MEMBRO", novoMembro,
                Map.of("papel", requisicao.papel().name()));
        return repositorio.listarMembros(espacoId).stream()
                .filter(membro -> membro.usuarioId().equals(novoMembro))
                .findFirst()
                .orElseThrow();
    }

    public List<MembroResponse> listarMembros(UUID espacoId, String usuarioId) {
        acesso.exigirMembro(espacoId, usuarioId);
        return repositorio.listarMembros(espacoId);
    }

    private String limpar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
