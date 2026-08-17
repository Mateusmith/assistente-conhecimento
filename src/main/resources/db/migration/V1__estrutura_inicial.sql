CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE espacos (
    id UUID PRIMARY KEY,
    nome VARCHAR(120) NOT NULL,
    descricao VARCHAR(500),
    criado_por VARCHAR(120) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE membros_espaco (
    espaco_id UUID NOT NULL REFERENCES espacos(id) ON DELETE CASCADE,
    usuario_id VARCHAR(120) NOT NULL,
    papel VARCHAR(20) NOT NULL CHECK (papel IN ('PROPRIETARIO', 'CURADOR', 'LEITOR')),
    adicionado_por VARCHAR(120) NOT NULL,
    adicionado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (espaco_id, usuario_id)
);

CREATE INDEX idx_membros_usuario ON membros_espaco (usuario_id, espaco_id);

CREATE TABLE documentos (
    id UUID PRIMARY KEY,
    espaco_id UUID NOT NULL REFERENCES espacos(id) ON DELETE CASCADE,
    titulo VARCHAR(180) NOT NULL,
    nome_arquivo VARCHAR(255) NOT NULL,
    tipo_mime VARCHAR(100) NOT NULL,
    visibilidade VARCHAR(20) NOT NULL CHECK (visibilidade IN ('ESPACO', 'RESTRITO')),
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('PENDENTE', 'PROCESSANDO', 'PRONTO', 'FALHOU')),
    hash_sha256 CHAR(64) NOT NULL,
    versao INTEGER NOT NULL DEFAULT 1 CHECK (versao > 0),
    conteudo_original BYTEA NOT NULL,
    tamanho_bytes BIGINT NOT NULL CHECK (tamanho_bytes >= 0),
    criado_por VARCHAR(120) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processado_em TIMESTAMPTZ,
    erro_processamento VARCHAR(1000),
    UNIQUE (espaco_id, hash_sha256)
);

CREATE INDEX idx_documentos_espaco_estado ON documentos (espaco_id, estado, criado_em DESC);

CREATE TABLE permissoes_documento (
    documento_id UUID NOT NULL REFERENCES documentos(id) ON DELETE CASCADE,
    usuario_id VARCHAR(120) NOT NULL,
    nivel VARCHAR(20) NOT NULL CHECK (nivel IN ('LEITURA', 'GESTAO')),
    concedido_por VARCHAR(120) NOT NULL,
    concedido_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (documento_id, usuario_id)
);

CREATE INDEX idx_permissoes_usuario ON permissoes_documento (usuario_id, documento_id);

CREATE TABLE trechos_documento (
    id UUID PRIMARY KEY,
    documento_id UUID NOT NULL REFERENCES documentos(id) ON DELETE CASCADE,
    espaco_id UUID NOT NULL REFERENCES espacos(id) ON DELETE CASCADE,
    ordem INTEGER NOT NULL CHECK (ordem >= 0),
    conteudo TEXT NOT NULL,
    termos TSVECTOR GENERATED ALWAYS AS (to_tsvector('portuguese', conteudo)) STORED,
    embedding VECTOR(384) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (documento_id, ordem)
);

CREATE INDEX idx_trechos_espaco ON trechos_documento (espaco_id, documento_id);
CREATE INDEX idx_trechos_termos ON trechos_documento USING GIN (termos);
CREATE INDEX idx_trechos_embedding ON trechos_documento USING hnsw (embedding vector_cosine_ops);

CREATE TABLE tarefas_ingestao (
    id UUID PRIMARY KEY,
    documento_id UUID NOT NULL UNIQUE REFERENCES documentos(id) ON DELETE CASCADE,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('PENDENTE', 'PROCESSANDO', 'CONCLUIDA', 'FALHOU')),
    tentativas INTEGER NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    proxima_tentativa_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    iniciada_em TIMESTAMPTZ,
    finalizada_em TIMESTAMPTZ,
    erro VARCHAR(1000),
    criada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tarefas_ingestao_fila ON tarefas_ingestao (estado, proxima_tentativa_em, criada_em);

CREATE TABLE consultas_rag (
    id UUID PRIMARY KEY,
    espaco_id UUID NOT NULL REFERENCES espacos(id) ON DELETE CASCADE,
    usuario_id VARCHAR(120) NOT NULL,
    pergunta TEXT NOT NULL,
    resposta TEXT NOT NULL,
    recusada BOOLEAN NOT NULL,
    provedor_ia VARCHAR(30) NOT NULL,
    latencia_ms BIGINT NOT NULL,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_consultas_espaco_criada ON consultas_rag (espaco_id, criada_em DESC);

CREATE TABLE citacoes_resposta (
    consulta_id UUID NOT NULL REFERENCES consultas_rag(id) ON DELETE CASCADE,
    trecho_id UUID NOT NULL REFERENCES trechos_documento(id),
    documento_id UUID NOT NULL REFERENCES documentos(id),
    marcador VARCHAR(12) NOT NULL,
    excerto VARCHAR(700) NOT NULL,
    pontuacao NUMERIC(8, 6) NOT NULL,
    ordem INTEGER NOT NULL,
    PRIMARY KEY (consulta_id, marcador)
);

CREATE TABLE feedback_resposta (
    id UUID PRIMARY KEY,
    consulta_id UUID NOT NULL REFERENCES consultas_rag(id) ON DELETE CASCADE,
    usuario_id VARCHAR(120) NOT NULL,
    util BOOLEAN NOT NULL,
    comentario VARCHAR(1000),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (consulta_id, usuario_id)
);

CREATE TABLE conjuntos_avaliacao (
    id UUID PRIMARY KEY,
    espaco_id UUID NOT NULL REFERENCES espacos(id) ON DELETE CASCADE,
    nome VARCHAR(160) NOT NULL,
    descricao VARCHAR(500),
    criado_por VARCHAR(120) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE casos_avaliacao (
    id UUID PRIMARY KEY,
    conjunto_id UUID NOT NULL REFERENCES conjuntos_avaliacao(id) ON DELETE CASCADE,
    pergunta TEXT NOT NULL,
    termos_esperados JSONB NOT NULL DEFAULT '[]'::jsonb,
    documentos_esperados JSONB NOT NULL DEFAULT '[]'::jsonb,
    deve_recusar BOOLEAN NOT NULL DEFAULT FALSE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE execucoes_avaliacao (
    id UUID PRIMARY KEY,
    conjunto_id UUID NOT NULL REFERENCES conjuntos_avaliacao(id) ON DELETE CASCADE,
    executada_por VARCHAR(120) NOT NULL,
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('EXECUTANDO', 'CONCLUIDA', 'FALHOU')),
    total_casos INTEGER NOT NULL DEFAULT 0,
    casos_aprovados INTEGER NOT NULL DEFAULT 0,
    taxa_acerto NUMERIC(6, 4),
    iniciada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalizada_em TIMESTAMPTZ,
    erro VARCHAR(1000)
);

CREATE TABLE resultados_avaliacao (
    id UUID PRIMARY KEY,
    execucao_id UUID NOT NULL REFERENCES execucoes_avaliacao(id) ON DELETE CASCADE,
    caso_id UUID NOT NULL REFERENCES casos_avaliacao(id) ON DELETE CASCADE,
    consulta_id UUID REFERENCES consultas_rag(id),
    aprovado BOOLEAN NOT NULL,
    pontuacao_termos NUMERIC(6, 4) NOT NULL,
    pontuacao_fontes NUMERIC(6, 4) NOT NULL,
    recusa_correta BOOLEAN NOT NULL,
    detalhes VARCHAR(1000),
    UNIQUE (execucao_id, caso_id)
);

CREATE TABLE eventos_auditoria (
    id UUID PRIMARY KEY,
    espaco_id UUID REFERENCES espacos(id) ON DELETE SET NULL,
    usuario_id VARCHAR(120) NOT NULL,
    acao VARCHAR(80) NOT NULL,
    recurso VARCHAR(80) NOT NULL,
    recurso_id VARCHAR(120),
    detalhes JSONB NOT NULL DEFAULT '{}'::jsonb,
    endereco_ip VARCHAR(64),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_auditoria_espaco_criada ON eventos_auditoria (espaco_id, criado_em DESC);
