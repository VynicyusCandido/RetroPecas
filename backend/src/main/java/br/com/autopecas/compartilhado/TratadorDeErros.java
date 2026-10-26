package br.com.autopecas.compartilhado;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Toda falha da API sai como Problem Details (RFC 9457), com o texto do título em português. */
@RestControllerAdvice
class TratadorDeErros extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(ErroDeNegocio.class)
    ResponseEntity<ProblemDetail> negocio(ErroDeNegocio erro) {
        return ResponseEntity.status(erro.status()).body(problema(erro.status(), erro.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception erro) {
        LOG.error("Erro inesperado", erro);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException erro, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        ProblemDetail problema = problema(status, "Dados inválidos");
        Map<String, String> erros = new LinkedHashMap<>();
        erro.getBindingResult().getFieldErrors().forEach(e -> erros.putIfAbsent(e.getField(), e.getDefaultMessage()));
        problema.setProperty("erros", erros);
        return ResponseEntity.status(status).body(problema);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException erro, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        return ResponseEntity.status(status).body(problema(status, "Corpo da requisição inválido"));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception erro, Object corpo, HttpHeaders cabecalhos, HttpStatusCode status, WebRequest requisicao) {
        ResponseEntity<Object> resposta = super.handleExceptionInternal(erro, corpo, cabecalhos, status, requisicao);
        if (resposta != null && resposta.getBody() instanceof ProblemDetail problema) {
            problema.setTitle(tituloPadrao(status));
        }
        return resposta;
    }

    private static ProblemDetail problema(HttpStatusCode status, String titulo) {
        ProblemDetail problema = ProblemDetail.forStatus(status);
        problema.setTitle(titulo);
        return problema;
    }

    private static String tituloPadrao(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "Requisição inválida";
            case 401 -> "Não autenticado";
            case 403 -> "Acesso negado";
            case 404 -> "Não encontrado";
            case 405 -> "Método não permitido";
            case 415 -> "Tipo de conteúdo não suportado";
            default -> "Erro ao processar a requisição";
        };
    }
}
