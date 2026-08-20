ALTER TABLE documentos
    ADD COLUMN visao_aplicada BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN provedor_visao VARCHAR(40),
    ADD COLUMN modelo_visao VARCHAR(160),
    ADD COLUMN tokens_visao_entrada INTEGER NOT NULL DEFAULT 0 CHECK (tokens_visao_entrada >= 0),
    ADD COLUMN tokens_visao_saida INTEGER NOT NULL DEFAULT 0 CHECK (tokens_visao_saida >= 0),
    ADD COLUMN custo_visao_usd NUMERIC(14, 8) NOT NULL DEFAULT 0 CHECK (custo_visao_usd >= 0);

ALTER TABLE documentos
    DROP CONSTRAINT ck_documentos_origem_texto,
    DROP CONSTRAINT ck_documentos_paginas_ocr,
    ADD CONSTRAINT ck_documentos_origem_texto
        CHECK (origem_texto IS NULL OR origem_texto IN ('NATIVO', 'OCR', 'VISAO', 'OCR_E_VISAO')),
    ADD CONSTRAINT ck_documentos_paginas_ocr
        CHECK (
            (origem_texto IN ('OCR', 'OCR_E_VISAO') AND paginas_ocr > 0)
            OR
            (origem_texto NOT IN ('OCR', 'OCR_E_VISAO') AND paginas_ocr = 0)
            OR
            (origem_texto IS NULL AND paginas_ocr = 0)
        ),
    ADD CONSTRAINT ck_documentos_visao
        CHECK (
            (visao_aplicada = TRUE AND provedor_visao IS NOT NULL AND modelo_visao IS NOT NULL)
            OR
            (visao_aplicada = FALSE AND provedor_visao IS NULL AND modelo_visao IS NULL
                AND tokens_visao_entrada = 0 AND tokens_visao_saida = 0 AND custo_visao_usd = 0)
        );

ALTER TABLE consumo_ia_diario
    DROP CONSTRAINT consumo_ia_diario_operacao_check,
    ADD CONSTRAINT consumo_ia_diario_operacao_check
        CHECK (operacao IN ('EMBEDDING', 'RESPOSTA', 'VISAO'));

ALTER TABLE execucoes_avaliacao
    ADD COLUMN execucao_base_id UUID REFERENCES execucoes_avaliacao(id) ON DELETE SET NULL,
    ADD COLUMN ultimo_lote_em TIMESTAMPTZ;

CREATE INDEX idx_execucoes_avaliacao_historico
    ON execucoes_avaliacao (conjunto_id, iniciada_em DESC);
