package br.com.autopecas;

import br.com.autopecas.conta.internal.application.CalculadoraDeAuthId;
import br.com.autopecas.conta.internal.application.EmissorDeToken;
import br.com.autopecas.conta.internal.domain.PerfilVendedor;
import br.com.autopecas.conta.internal.domain.StatusVendedor;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.PerfilVendedorRepository;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import br.com.autopecas.conta.internal.infra.seguranca.CookieDeSessao;
import br.com.autopecas.notificacao.internal.domain.Notificacao;
import br.com.autopecas.notificacao.internal.domain.TipoNotificacao;
import br.com.autopecas.notificacao.internal.infra.persistencia.NotificacaoRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Cria usuários, sessões e notificações direto no banco, para os testes não dependerem dos endpoints. */
@TestComponent
public class ApoioDeTeste {

    public static final String SENHA = "senha-de-teste-123";

    // Custo baixo só nos testes: o login confere qualquer custo de BCrypt.
    private static final String HASH_DA_SENHA = new BCryptPasswordEncoder(4).encode(SENHA);

    private final UsuarioRepository usuarios;
    private final PerfilVendedorRepository perfis;
    private final NotificacaoRepository notificacoes;
    private final EmissorDeToken emissor;
    private final CalculadoraDeAuthId calculadora;

    ApoioDeTeste(
            UsuarioRepository usuarios,
            PerfilVendedorRepository perfis,
            NotificacaoRepository notificacoes,
            EmissorDeToken emissor,
            CalculadoraDeAuthId calculadora) {
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.notificacoes = notificacoes;
        this.emissor = emissor;
        this.calculadora = calculadora;
    }

    public String emailUnico() {
        return "usuario-" + UUID.randomUUID() + "@example.com";
    }

    public Usuario usuario() {
        return usuarios.saveAndFlush(Usuario.novo("Usuário de Teste", emailUnico(), HASH_DA_SENHA, Instant.now()));
    }

    public Usuario usuario(String email) {
        return usuarios.saveAndFlush(Usuario.novo("Usuário de Teste", email, HASH_DA_SENHA, Instant.now()));
    }

    public Usuario vendedor(StatusVendedor status) {
        Usuario usuario = usuario();
        perfis.saveAndFlush(PerfilVendedor.de(usuario.getId(), status, Instant.now()));
        return usuario;
    }

    public String jwtDe(Usuario usuario) {
        return emissor.emitir(usuario.getId()).jwt();
    }

    public Cookie cookieDe(Usuario usuario) {
        return new Cookie(CookieDeSessao.NOME, jwtDe(usuario));
    }

    public String authIdDe(Usuario usuario) {
        return calculadora.calcular(usuario.getId());
    }

    public Notificacao notificacao(
            Usuario usuario, TipoNotificacao tipo, boolean lida, Instant criadaEm, String titulo) {
        Notificacao notificacao = Notificacao.nova(usuario.getId(), tipo, titulo, "Resumo de " + titulo, criadaEm);
        notificacao.definirLida(lida);
        return notificacoes.saveAndFlush(notificacao);
    }
}
