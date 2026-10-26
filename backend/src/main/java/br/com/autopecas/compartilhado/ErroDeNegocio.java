package br.com.autopecas.compartilhado;

import org.springframework.http.HttpStatus;

/** Erro esperado da regra de negócio. Vira uma resposta Problem Details (RFC 9457). */
public class ErroDeNegocio extends RuntimeException {

    private final HttpStatus status;

    private ErroDeNegocio(HttpStatus status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public static ErroDeNegocio requisicaoInvalida(String mensagem) {
        return new ErroDeNegocio(HttpStatus.BAD_REQUEST, mensagem);
    }

    public static ErroDeNegocio naoAutenticado(String mensagem) {
        return new ErroDeNegocio(HttpStatus.UNAUTHORIZED, mensagem);
    }

    public static ErroDeNegocio naoEncontrado(String mensagem) {
        return new ErroDeNegocio(HttpStatus.NOT_FOUND, mensagem);
    }

    public static ErroDeNegocio conflito(String mensagem) {
        return new ErroDeNegocio(HttpStatus.CONFLICT, mensagem);
    }

    public HttpStatus status() {
        return status;
    }
}
