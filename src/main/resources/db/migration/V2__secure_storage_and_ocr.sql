ALTER TABLE documentos
    ADD COLUMN armazenamento VARCHAR(10) NOT NULL DEFAULT 'BANCO',
    ADD COLUMN chave_armazenamento VARCHAR(700),
    ADD COLUMN resultado_antivirus VARCHAR(20) NOT NULL DEFAULT 'NAO_VERIFICADO',
    ADD COLUMN verificado_antivirus_em TIMESTAMPTZ,
    ADD COLUMN origem_texto VARCHAR(10),
    ADD COLUMN paginas_ocr INTEGER NOT NULL DEFAULT 0;

ALTER TABLE documentos
    ALTER COLUMN conteudo_original DROP NOT NULL,
    ADD CONSTRAINT ck_documentos_armazenamento
        CHECK (armazenamento IN ('BANCO', 'S3')),
    ADD CONSTRAINT ck_documentos_referencia_conteudo
        CHECK (
            (armazenamento = 'BANCO' AND conteudo_original IS NOT NULL AND chave_armazenamento IS NULL)
            OR
            (armazenamento = 'S3' AND conteudo_original IS NULL AND chave_armazenamento IS NOT NULL)
        ),
    ADD CONSTRAINT ck_documentos_resultado_antivirus
        CHECK (resultado_antivirus IN ('NAO_VERIFICADO', 'LIMPO')),
    ADD CONSTRAINT ck_documentos_verificacao_antivirus
        CHECK (
            (resultado_antivirus = 'NAO_VERIFICADO' AND verificado_antivirus_em IS NULL)
            OR
            (resultado_antivirus = 'LIMPO' AND verificado_antivirus_em IS NOT NULL)
        ),
    ADD CONSTRAINT ck_documentos_origem_texto
        CHECK (origem_texto IS NULL OR origem_texto IN ('NATIVO', 'OCR')),
    ADD CONSTRAINT ck_documentos_paginas_ocr
        CHECK (
            (origem_texto = 'OCR' AND paginas_ocr > 0)
            OR
            (origem_texto IS DISTINCT FROM 'OCR' AND paginas_ocr = 0)
        );

CREATE UNIQUE INDEX uk_documentos_chave_armazenamento
    ON documentos (chave_armazenamento)
    WHERE chave_armazenamento IS NOT NULL;
