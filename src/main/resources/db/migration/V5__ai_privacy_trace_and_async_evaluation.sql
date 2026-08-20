ALTER TABLE consultas_rag
    ADD COLUMN versao_prompt VARCHAR(80) NOT NULL DEFAULT 'legado-v1',
    ADD COLUMN impressao_prompt CHAR(64) NOT NULL DEFAULT REPEAT('0', 64),
    ADD COLUMN candidatos_recuperados INTEGER NOT NULL DEFAULT 0 CHECK (candidatos_recuperados >= 0),
    ADD COLUMN fontes_contexto INTEGER NOT NULL DEFAULT 0 CHECK (fontes_contexto >= 0),
    ADD COLUMN dados_sensiveis_protegidos INTEGER NOT NULL DEFAULT 0 CHECK (dados_sensiveis_protegidos >= 0);

ALTER TABLE execucoes_avaliacao
    DROP CONSTRAINT execucoes_avaliacao_estado_check,
    ADD CONSTRAINT execucoes_avaliacao_estado_check
        CHECK (estado IN ('PENDENTE', 'EXECUTANDO', 'CONCLUIDA', 'FALHOU', 'CANCELADA')),
    ADD COLUMN casos_processados INTEGER NOT NULL DEFAULT 0 CHECK (casos_processados >= 0),
    ADD COLUMN cancelamento_solicitado BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN trabalhador_id VARCHAR(160),
    ADD COLUMN bloqueado_ate TIMESTAMPTZ;

CREATE INDEX idx_execucoes_avaliacao_fila
    ON execucoes_avaliacao (estado, bloqueado_ate, iniciada_em);
