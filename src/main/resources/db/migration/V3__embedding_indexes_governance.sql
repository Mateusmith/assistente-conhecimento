ALTER TABLE espacos
    ADD COLUMN limite_armazenamento_bytes BIGINT NOT NULL DEFAULT 1073741824
        CHECK (limite_armazenamento_bytes BETWEEN 10485760 AND 1099511627776),
    ADD COLUMN limite_consultas_dia INTEGER NOT NULL DEFAULT 1000
        CHECK (limite_consultas_dia BETWEEN 1 AND 1000000),
    ADD COLUMN limite_uploads_dia INTEGER NOT NULL DEFAULT 100
        CHECK (limite_uploads_dia BETWEEN 1 AND 100000),
    ADD COLUMN retencao_consultas_dias INTEGER NOT NULL DEFAULT 365
        CHECK (retencao_consultas_dias BETWEEN 1 AND 3650);

ALTER TABLE documentos
    ADD COLUMN metadados JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX idx_documentos_metadados ON documentos USING GIN (metadados);

ALTER TABLE trechos_documento
    ADD COLUMN risco_prompt BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE indices_embedding (
    id UUID PRIMARY KEY,
    espaco_id UUID NOT NULL REFERENCES espacos(id) ON DELETE CASCADE,
    provedor VARCHAR(40) NOT NULL,
    modelo VARCHAR(160) NOT NULL,
    dimensoes INTEGER NOT NULL CHECK (dimensoes = 384),
    estado VARCHAR(20) NOT NULL
        CHECK (estado IN ('ATIVO', 'CONSTRUINDO', 'ARQUIVADO', 'FALHOU')),
    total_trechos INTEGER NOT NULL DEFAULT 0 CHECK (total_trechos >= 0),
    trechos_processados INTEGER NOT NULL DEFAULT 0 CHECK (trechos_processados >= 0),
    tentativas INTEGER NOT NULL DEFAULT 0 CHECK (tentativas >= 0),
    criado_por VARCHAR(120) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    iniciado_em TIMESTAMPTZ,
    finalizado_em TIMESTAMPTZ,
    ativado_em TIMESTAMPTZ,
    erro VARCHAR(1000),
    trabalhador_id VARCHAR(160),
    bloqueado_ate TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_indices_embedding_ativo
    ON indices_embedding (espaco_id) WHERE estado = 'ATIVO';
CREATE UNIQUE INDEX uk_indices_embedding_construindo
    ON indices_embedding (espaco_id) WHERE estado = 'CONSTRUINDO';
CREATE INDEX idx_indices_embedding_fila
    ON indices_embedding (estado, bloqueado_ate, criado_em);

INSERT INTO indices_embedding
    (id, espaco_id, provedor, modelo, dimensoes, estado, total_trechos,
     trechos_processados, criado_por, criado_em, finalizado_em, ativado_em)
SELECT (
           SUBSTRING(MD5(e.id::text || ':local-hashing-v1') FROM 1 FOR 8) || '-' ||
           SUBSTRING(MD5(e.id::text || ':local-hashing-v1') FROM 9 FOR 4) || '-' ||
           SUBSTRING(MD5(e.id::text || ':local-hashing-v1') FROM 13 FOR 4) || '-' ||
           SUBSTRING(MD5(e.id::text || ':local-hashing-v1') FROM 17 FOR 4) || '-' ||
           SUBSTRING(MD5(e.id::text || ':local-hashing-v1') FROM 21 FOR 12)
       )::uuid,
       e.id,
       'local',
       'local-hashing-v1',
       384,
       'ATIVO',
       COUNT(t.id)::integer,
       COUNT(t.id)::integer,
       e.criado_por,
       e.criado_em,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
  FROM espacos e
  LEFT JOIN trechos_documento t ON t.espaco_id = e.id
 GROUP BY e.id, e.criado_por, e.criado_em;

CREATE TABLE vetores_trecho (
    indice_id UUID NOT NULL REFERENCES indices_embedding(id) ON DELETE CASCADE,
    trecho_id UUID NOT NULL REFERENCES trechos_documento(id) ON DELETE CASCADE,
    embedding VECTOR(384) NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (indice_id, trecho_id)
);

INSERT INTO vetores_trecho (indice_id, trecho_id, embedding, criado_em)
SELECT i.id, t.id, t.embedding, t.criado_em
  FROM indices_embedding i
  JOIN trechos_documento t ON t.espaco_id = i.espaco_id
 WHERE i.estado = 'ATIVO';

CREATE INDEX idx_vetores_trecho_embedding
    ON vetores_trecho USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_vetores_trecho_trecho ON vetores_trecho (trecho_id, indice_id);

ALTER TABLE tarefas_ingestao
    ADD COLUMN trabalhador_id VARCHAR(160),
    ADD COLUMN bloqueado_ate TIMESTAMPTZ;

ALTER TABLE consultas_rag
    ADD COLUMN indice_embedding_id UUID REFERENCES indices_embedding(id) ON DELETE SET NULL,
    ADD COLUMN modelo_embedding VARCHAR(160),
    ADD COLUMN estrategia_busca VARCHAR(20) NOT NULL DEFAULT 'HIBRIDA'
        CHECK (estrategia_busca IN ('HIBRIDA', 'SEMANTICA', 'TEXTUAL')),
    ADD COLUMN tokens_entrada INTEGER NOT NULL DEFAULT 0 CHECK (tokens_entrada >= 0),
    ADD COLUMN tokens_saida INTEGER NOT NULL DEFAULT 0 CHECK (tokens_saida >= 0),
    ADD COLUMN custo_estimado_usd NUMERIC(14, 8) NOT NULL DEFAULT 0 CHECK (custo_estimado_usd >= 0);

CREATE TABLE consumo_ia_diario (
    espaco_id UUID NOT NULL REFERENCES espacos(id) ON DELETE CASCADE,
    data DATE NOT NULL,
    provedor VARCHAR(40) NOT NULL,
    modelo VARCHAR(160) NOT NULL,
    operacao VARCHAR(20) NOT NULL CHECK (operacao IN ('EMBEDDING', 'RESPOSTA')),
    chamadas BIGINT NOT NULL DEFAULT 0,
    tokens_entrada BIGINT NOT NULL DEFAULT 0,
    tokens_saida BIGINT NOT NULL DEFAULT 0,
    custo_estimado_usd NUMERIC(16, 8) NOT NULL DEFAULT 0,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (espaco_id, data, provedor, modelo, operacao)
);

ALTER TABLE resultados_avaliacao
    DROP CONSTRAINT resultados_avaliacao_consulta_id_fkey,
    ADD CONSTRAINT resultados_avaliacao_consulta_id_fkey
        FOREIGN KEY (consulta_id) REFERENCES consultas_rag(id) ON DELETE SET NULL;

CREATE INDEX idx_consultas_retencao ON consultas_rag (espaco_id, criada_em);
