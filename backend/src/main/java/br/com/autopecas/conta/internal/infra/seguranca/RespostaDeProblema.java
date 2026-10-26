package br.com.autopecas.conta.internal.infra.seguranca;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Resposta Problem Details escrita à mão, para falhas que acontecem antes de chegar a um controller. */
final class RespostaDeProblema {

    private RespostaDeProblema() {
    }

    /** O título é sempre um texto fixo nosso, sem aspas, então não precisa de escape. */
    static void escrever(HttpServletResponse resposta, int status, String titulo) throws IOException {
        resposta.setStatus(status);
        resposta.setContentType("application/problem+json");
        resposta.setCharacterEncoding("UTF-8");
        resposta.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + titulo + "\",\"status\":" + status + "}");
    }
}
