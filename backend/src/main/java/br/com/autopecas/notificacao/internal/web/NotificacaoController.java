package br.com.autopecas.notificacao.internal.web;

import br.com.autopecas.compartilhado.Identidade;
import br.com.autopecas.notificacao.internal.application.NotificacaoService;
import br.com.autopecas.notificacao.internal.application.PainelDeNotificacoes;
import br.com.autopecas.notificacao.internal.domain.Notificacao;
import br.com.autopecas.notificacao.internal.domain.TipoNotificacao;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificacoes")
class NotificacaoController {

    private final NotificacaoService servico;

    NotificacaoController(NotificacaoService servico) {
        this.servico = servico;
    }

    @GetMapping
    ListaNotificacoesResponse listar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) List<TipoNotificacao> tipos,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limite) {
        return ListaNotificacoesResponse.de(servico.listar(Identidade.usuarioId(jwt), tipos, limite));
    }

    @PatchMapping("/{id}")
    ResponseEntity<Void> atualizar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody AtualizarNotificacaoRequest requisicao) {
        servico.definirLida(Identidade.usuarioId(jwt), id, requisicao.lida());
        return ResponseEntity.noContent().build();
    }

    record AtualizarNotificacaoRequest(@NotNull(message = "Informe o campo lida.") Boolean lida) {
    }

    record ListaNotificacoesResponse(List<NotificacaoResponse> itens, NaoLidasResponse naoLidas) {

        static ListaNotificacoesResponse de(PainelDeNotificacoes painel) {
            return new ListaNotificacoesResponse(
                    painel.itens().stream().map(NotificacaoResponse::de).toList(),
                    new NaoLidasResponse(
                            painel.naoLidas().chat(), painel.naoLidas().outras(), painel.naoLidas().pecasAProcura()));
        }
    }

    record NaoLidasResponse(long chat, long outras, long pecasAProcura) {
    }

    record NotificacaoResponse(String id, String tipo, String titulo, String resumo, Instant criadaEm, boolean lida) {

        static NotificacaoResponse de(Notificacao notificacao) {
            return new NotificacaoResponse(
                    notificacao.getId().toString(),
                    notificacao.getTipo().name(),
                    notificacao.getTitulo(),
                    notificacao.getResumo(),
                    notificacao.getCriadaEm(),
                    notificacao.isLida());
        }
    }
}
