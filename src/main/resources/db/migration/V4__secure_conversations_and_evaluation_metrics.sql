CREATE TABLE conversas (
    id UUID PRIMARY KEY,
    espaco_id UUID NOT NULL REFERENCES espacos(id) ON DELETE CASCADE,
    usuario_id VARCHAR(120) NOT NULL,
    titulo VARCHAR(160) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'ATIVA' CHECK (estado IN ('ATIVA', 'ARQUIVADA')),
    versao BIGINT NOT NULL DEFAULT 0,
    token_lease UUID,
    lease_ate TIMESTAMPTZ,
    criada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_conversas_usuario_atualizada
    ON conversas (espaco_id, usuario_id, atualizada_em DESC);

CREATE TABLE mensagens_conversa (
    id UUID PRIMARY KEY,
    conversa_id UUID NOT NULL REFERENCES conversas(id) ON DELETE CASCADE,
    consulta_id UUID REFERENCES consultas_rag(id) ON DELETE SET NULL,
    sequencia INTEGER NOT NULL CHECK (sequencia > 0),
    papel VARCHAR(20) NOT NULL CHECK (papel IN ('USUARIO', 'ASSISTENTE')),
    conteudo TEXT NOT NULL,
    chave_idempotencia VARCHAR(120),
    impressao_requisicao CHAR(64),
    criada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (conversa_id, sequencia)
);

CREATE INDEX idx_mensagens_conversa_sequencia
    ON mensagens_conversa (conversa_id, sequencia);

CREATE UNIQUE INDEX ux_mensagens_conversa_idempotencia
    ON mensagens_conversa (conversa_id, chave_idempotencia)
    WHERE chave_idempotencia IS NOT NULL;

ALTER TABLE casos_avaliacao
    ADD COLUMN latencia_maxima_ms BIGINT CHECK (latencia_maxima_ms IS NULL OR latencia_maxima_ms > 0),
    ADD COLUMN custo_maximo_usd NUMERIC(14, 8) CHECK (custo_maximo_usd IS NULL OR custo_maximo_usd >= 0);

ALTER TABLE resultados_avaliacao
    ADD COLUMN precisao_fontes NUMERIC(6, 4) NOT NULL DEFAULT 1,
    ADD COLUMN mrr NUMERIC(6, 4) NOT NULL DEFAULT 1,
    ADD COLUMN latencia_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN custo_usd NUMERIC(14, 8) NOT NULL DEFAULT 0,
    ADD COLUMN orcamento_respeitado BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE execucoes_avaliacao
    ADD COLUMN recall_medio NUMERIC(6, 4) NOT NULL DEFAULT 0,
    ADD COLUMN precisao_media NUMERIC(6, 4) NOT NULL DEFAULT 0,
    ADD COLUMN mrr_medio NUMERIC(6, 4) NOT NULL DEFAULT 0,
    ADD COLUMN latencia_p95_ms BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN custo_total_usd NUMERIC(14, 8) NOT NULL DEFAULT 0,
    ADD COLUMN modelo_embedding VARCHAR(160),
    ADD COLUMN provedor_ia VARCHAR(160);
