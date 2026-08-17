package br.com.contextpilot.document;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.contextpilot.audit.AuditService;
import br.com.contextpilot.document.DocumentModels.ConcederPermissaoRequest;
import br.com.contextpilot.document.DocumentModels.DocumentoResponse;
import br.com.contextpilot.document.DocumentModels.EstadoDocumento;
import br.com.contextpilot.document.DocumentModels.VisibilidadeDocumento;
import br.com.contextpilot.shared.domain.BusinessRuleException;
import br.com.contextpilot.shared.domain.ConflictException;
import br.com.contextpilot.shared.domain.ResourceNotFoundException;
import br.com.contextpilot.workspace.WorkspaceAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private final DocumentRepository repositorio;
    private final WorkspaceAccessService acessoEspaco;
    private final AuditService auditoria;
    private final Clock relogio;
    private final long tamanhoMaximo;

    public DocumentService(
            DocumentRepository repositorio,
            WorkspaceAccessService acessoEspaco,
            AuditService auditoria,
            Clock relogio,
            @Value("${contextpilot.documentos.tamanho-maximo-bytes}") long tamanhoMaximo) {
        this.repositorio = repositorio;
        this.acessoEspaco = acessoEspaco;
        this.auditoria = auditoria;
        this.relogio = relogio;
        this.tamanhoMaximo = tamanhoMaximo;
    }

    @Transactional
    public DocumentoResponse enviar(
            UUID espacoId,
            String titulo,
            VisibilidadeDocumento visibilidade,
            MultipartFile arquivo,
            String usuarioId) {
        acessoEspaco.exigirCuradoria(espacoId, usuarioId);
        validarTitulo(titulo);
        byte[] conteudo = ler(arquivo);
        String tipoMime = detectarTipo(arquivo.getOriginalFilename(), conteudo);
        String hash = calcularHash(conteudo);

        if (repositorio.existeHash(espacoId, hash)) {
            throw new ConflictException("Este conteudo ja foi enviado para o espaco.");
        }

        UUID documentoId = UUID.randomUUID();
        String nomeArquivo = normalizarNome(arquivo.getOriginalFilename(), tipoMime);
        repositorio.criar(documentoId, espacoId, titulo.trim(), nomeArquivo, tipoMime,
                visibilidade, hash, conteudo, usuarioId, Instant.now(relogio));
        auditoria.registrar(espacoId, usuarioId, "DOCUMENTO_ENVIADO", "DOCUMENTO", documentoId.toString(),
                Map.of("titulo", titulo.trim(), "tipoMime", tipoMime, "tamanhoBytes", conteudo.length));

        return buscar(espacoId, documentoId, usuarioId);
    }

    public List<DocumentoResponse> listar(UUID espacoId, String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        return repositorio.listarAcessiveis(espacoId, usuarioId);
    }

    public DocumentoResponse buscar(UUID espacoId, UUID documentoId, String usuarioId) {
        acessoEspaco.exigirMembro(espacoId, usuarioId);
        return repositorio.buscarAcessivel(espacoId, documentoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Documento nao encontrado."));
    }

    public byte[] baixar(UUID espacoId, UUID documentoId, String usuarioId) {
        buscar(espacoId, documentoId, usuarioId);
        return repositorio.obterConteudo(documentoId)
                .orElseThrow(() -> new ResourceNotFoundException("Conteudo do documento nao encontrado."));
    }

    @Transactional
    public void concederPermissao(
            UUID espacoId,
            UUID documentoId,
            ConcederPermissaoRequest requisicao,
            String usuarioId) {
        acessoEspaco.exigirCuradoria(espacoId, usuarioId);
        buscar(espacoId, documentoId, usuarioId);
        acessoEspaco.exigirMembro(espacoId, requisicao.usuarioId().trim());
        repositorio.concederPermissao(documentoId, requisicao.usuarioId().trim(), requisicao.nivel(),
                usuarioId, Instant.now(relogio));
        auditoria.registrar(espacoId, usuarioId, "PERMISSAO_DOCUMENTO_CONCEDIDA", "DOCUMENTO",
                documentoId.toString(), Map.of("usuarioId", requisicao.usuarioId().trim(), "nivel", requisicao.nivel().name()));
    }

    @Transactional
    public DocumentoResponse reprocessar(UUID espacoId, UUID documentoId, String usuarioId) {
        acessoEspaco.exigirCuradoria(espacoId, usuarioId);
        DocumentoResponse documento = buscar(espacoId, documentoId, usuarioId);
        if (documento.estado() != EstadoDocumento.FALHOU) {
            throw new BusinessRuleException("Somente documentos com falha podem ser reprocessados.");
        }
        repositorio.reagendar(documentoId, Instant.now(relogio));
        auditoria.registrar(espacoId, usuarioId, "DOCUMENTO_REPROCESSADO", "DOCUMENTO",
                documentoId.toString(), Map.of());
        return buscar(espacoId, documentoId, usuarioId);
    }

    private byte[] ler(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new BusinessRuleException("Selecione um arquivo com conteudo.");
        }
        if (arquivo.getSize() > tamanhoMaximo) {
            throw new BusinessRuleException("O arquivo excede o limite de 10 MB.");
        }
        try {
            return arquivo.getBytes();
        } catch (java.io.IOException excecao) {
            throw new BusinessRuleException("Nao foi possivel ler o arquivo enviado.");
        }
    }

    private String detectarTipo(String nomeOriginal, byte[] conteudo) {
        String nome = nomeOriginal == null ? "" : nomeOriginal.toLowerCase(java.util.Locale.ROOT);
        boolean pdf = conteudo.length >= 5
                && conteudo[0] == '%' && conteudo[1] == 'P' && conteudo[2] == 'D'
                && conteudo[3] == 'F' && conteudo[4] == '-';
        if (pdf && nome.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (!pdf && nome.endsWith(".md")) {
            return "text/markdown";
        }
        if (!pdf && nome.endsWith(".txt")) {
            return "text/plain";
        }
        throw new BusinessRuleException("Envie um arquivo PDF, TXT ou Markdown valido.");
    }

    private String normalizarNome(String nomeOriginal, String tipoMime) {
        String extensao = switch (tipoMime) {
            case "application/pdf" -> ".pdf";
            case "text/markdown" -> ".md";
            default -> ".txt";
        };
        if (nomeOriginal == null || nomeOriginal.isBlank()) {
            return "documento" + extensao;
        }
        String nome = java.nio.file.Path.of(nomeOriginal).getFileName().toString()
                .replaceAll("[^A-Za-z0-9._ -]", "_")
                .trim();
        return nome.isBlank() ? "documento" + extensao : nome;
    }

    private String calcularHash(byte[] conteudo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(conteudo));
        } catch (NoSuchAlgorithmException excecao) {
            throw new IllegalStateException("SHA-256 nao esta disponivel.", excecao);
        }
    }

    private void validarTitulo(String titulo) {
        if (titulo == null || titulo.trim().length() < 3 || titulo.trim().length() > 180) {
            throw new BusinessRuleException("O titulo deve ter entre 3 e 180 caracteres.");
        }
    }
}
