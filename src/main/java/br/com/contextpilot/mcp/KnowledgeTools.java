package br.com.contextpilot.mcp;

import java.util.List;
import java.util.UUID;

import br.com.contextpilot.answer.AnswerModels.RespostaRag;
import br.com.contextpilot.answer.AnswerService;
import br.com.contextpilot.document.DocumentModels.DocumentoResponse;
import br.com.contextpilot.document.DocumentService;
import br.com.contextpilot.retrieval.HybridSearchService;
import br.com.contextpilot.shared.security.CurrentUser;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeTools {

    private final DocumentService documentos;
    private final HybridSearchService busca;
    private final AnswerService respostas;
    private final CurrentUser usuarioAtual;

    public KnowledgeTools(
            DocumentService documentos,
            HybridSearchService busca,
            AnswerService respostas,
            CurrentUser usuarioAtual) {
        this.documentos = documentos;
        this.busca = busca;
        this.respostas = respostas;
        this.usuarioAtual = usuarioAtual;
    }

    @McpTool(description = "Lista somente os documentos que o usuario autenticado pode acessar em um espaco do ContextPilot")
    public List<DocumentoResponse> listarDocumentos(
            @McpToolParam(description = "Identificador UUID do espaco") String espacoId) {
        return documentos.listar(UUID.fromString(espacoId), usuarioAtual.obterId());
    }

    @McpTool(description = "Busca trechos de conhecimento com verificacao de permissao e retorna as fontes mais relevantes")
    public List<FonteMcp> buscarConhecimento(
            @McpToolParam(description = "Identificador UUID do espaco") String espacoId,
            @McpToolParam(description = "Consulta de busca em linguagem natural") String pergunta) {
        return busca.buscar(UUID.fromString(espacoId), pergunta, usuarioAtual.obterId()).stream()
                .map(fonte -> new FonteMcp(
                        fonte.documentoId(),
                        fonte.tituloDocumento(),
                        fonte.ordemTrecho(),
                        excerto(fonte.conteudo()),
                        fonte.pontuacao()))
                .toList();
    }

    @McpTool(description = "Responde com RAG usando apenas documentos permitidos e inclui citacoes verificaveis")
    public RespostaRag consultarComFontes(
            @McpToolParam(description = "Identificador UUID do espaco") String espacoId,
            @McpToolParam(description = "Pergunta que deve ser respondida com fontes") String pergunta) {
        return respostas.perguntar(UUID.fromString(espacoId), pergunta, usuarioAtual.obterId());
    }

    private String excerto(String conteudo) {
        String limpo = conteudo.replaceAll("\\s+", " ").trim();
        return limpo.substring(0, Math.min(500, limpo.length()));
    }

    public record FonteMcp(
            UUID documentoId,
            String tituloDocumento,
            int ordemTrecho,
            String excerto,
            double pontuacao) {
    }
}
