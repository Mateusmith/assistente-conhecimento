package br.com.contextpilot.answer;

import java.util.List;

import br.com.contextpilot.answer.AnswerModels.FonteContexto;
import br.com.contextpilot.answer.AnswerModels.ResultadoGeracao;

interface AnswerGenerator {

    ResultadoGeracao gerar(String pergunta, List<FonteContexto> fontes);
}
