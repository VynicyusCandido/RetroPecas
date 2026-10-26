package br.com.autopecas.conta.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.com.autopecas.compartilhado.ErroDeNegocio;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * O login de e-mail inexistente e o de senha errada dão a mesma resposta, e o tempo de resposta não pode revelar
 * qual dos dois foi: por isso o serviço confere um hash falso quando o e-mail não existe. Remover essa conferência
 * passa em todos os testes de integração (a resposta continua igual), então ela é fixada aqui, sem Spring e sem banco.
 */
class AutenticacaoServiceTest {

    private static final String EMAIL = "ana@example.com";
    private static final String SENHA_CERTA = "senha-certa-123";
    private static final String MENSAGEM = "E-mail ou senha inválidos.";

    /** BCrypt de verdade (custo 4, rápido) que só conta as chamadas a matches e guarda o último hash conferido. */
    private static final class CodificadorQueConta implements PasswordEncoder {

        private final PasswordEncoder real = new BCryptPasswordEncoder(4);
        int conferencias;
        String ultimoHashConferido;

        @Override
        public String encode(CharSequence senha) {
            return real.encode(senha);
        }

        @Override
        public boolean matches(CharSequence senha, String hash) {
            conferencias++;
            ultimoHashConferido = hash;
            return real.matches(senha, hash);
        }
    }

    private final CodificadorQueConta codificador = new CodificadorQueConta();
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final EmissorDeToken emissor = mock(EmissorDeToken.class);
    private final SessaoService sessoes = mock(SessaoService.class);
    private final AutenticacaoService servico = new AutenticacaoService(usuarios, codificador, emissor, sessoes);

    private Usuario usuarioComASenha(String senha) {
        return Usuario.novo("Ana", EMAIL, codificador.encode(senha), Instant.parse("2026-03-10T12:00:00Z"));
    }

    private void emailCadastrado(boolean existe) {
        when(usuarios.findByEmailIgnoreCase(EMAIL))
                .thenReturn(existe ? Optional.of(usuarioComASenha(SENHA_CERTA)) : Optional.empty());
    }

    private ErroDeNegocio falhaAoEntrarCom(String senha) {
        return catchThrowableOfType(ErroDeNegocio.class, () -> servico.autenticar(EMAIL, senha));
    }

    @Test
    void emailInexistenteConfereUmHashFalsoExatamenteUmaVez() {
        emailCadastrado(false);

        ErroDeNegocio erro = falhaAoEntrarCom(SENHA_CERTA);

        assertThat(codificador.conferencias).isEqualTo(1);
        assertThat(codificador.ultimoHashConferido).startsWith("$2");
        assertThat(erro.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(erro).hasMessage(MENSAGEM);
        verifyNoInteractions(emissor, sessoes);
    }

    @Test
    void senhaErradaComEmailExistenteTambemConfereUmaVez() {
        emailCadastrado(true);

        ErroDeNegocio erro = falhaAoEntrarCom("senha-errada-123");

        assertThat(codificador.conferencias).isEqualTo(1);
        assertThat(erro.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(erro).hasMessage(MENSAGEM);
        verifyNoInteractions(emissor, sessoes);
    }

    @Test
    void emailInexistenteESenhaErradaDaoExatamenteAMesmaResposta() {
        emailCadastrado(false);
        ErroDeNegocio deEmailInexistente = falhaAoEntrarCom("senha-errada-123");
        emailCadastrado(true);
        ErroDeNegocio deSenhaErrada = falhaAoEntrarCom("senha-errada-123");

        assertThat(deEmailInexistente.status()).isEqualTo(deSenhaErrada.status());
        assertThat(deEmailInexistente.getMessage()).isEqualTo(deSenhaErrada.getMessage());
    }

    @ParameterizedTest(name = "e-mail cadastrado: {0}")
    @ValueSource(booleans = {true, false})
    void senhaComMaisDe72BytesNaoChegaAoBcrypt(boolean emailExiste) {
        emailCadastrado(emailExiste);
        // 37 caracteres acentuados são 74 bytes em UTF-8: o BCrypt leria só os 72 primeiros.
        String longaDemais = "é".repeat(37);

        ErroDeNegocio erro = falhaAoEntrarCom(longaDemais);

        assertThat(codificador.conferencias).isZero();
        assertThat(erro.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(erro).hasMessage(MENSAGEM);
        verifyNoInteractions(emissor, sessoes);
    }

    @Test
    void senhaCertaAutenticaConferindoUmaVez() {
        Usuario usuario = usuarioComASenha(SENHA_CERTA);
        SessaoDoUsuario sessao = new SessaoDoUsuario(usuario.getId(), "Ana", EMAIL, null, "a".repeat(64));
        TokenEmitido token = new TokenEmitido("jwt", Instant.parse("2026-03-10T13:00:00Z"));
        when(usuarios.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(usuario));
        when(sessoes.montar(usuario)).thenReturn(sessao);
        when(emissor.emitir(usuario.getId())).thenReturn(token);

        ResultadoDeLogin resultado = servico.autenticar(EMAIL, SENHA_CERTA);

        assertThat(resultado.sessao()).isSameAs(sessao);
        assertThat(resultado.token()).isSameAs(token);
        assertThat(codificador.conferencias).isEqualTo(1);
    }
}
