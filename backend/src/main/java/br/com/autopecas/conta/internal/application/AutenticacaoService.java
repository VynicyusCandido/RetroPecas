package br.com.autopecas.conta.internal.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import br.com.autopecas.compartilhado.ErroDeNegocio;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutenticacaoService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final EmissorDeToken emissor;
    private final SessaoService sessoes;
    private final String hashFalso;

    AutenticacaoService(
            UsuarioRepository usuarios, PasswordEncoder codificador, EmissorDeToken emissor, SessaoService sessoes) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.emissor = emissor;
        this.sessoes = sessoes;
        this.hashFalso = codificador.encode("senha-que-nunca-sera-usada");
    }

    @Transactional(readOnly = true)
    public ResultadoDeLogin autenticar(String email, String senha) {
        Optional<Usuario> encontrado = usuarios.findByEmailIgnoreCase(email.trim());
        // Sempre confere um hash, mesmo sem usuário: o tempo de resposta não revela se o e-mail existe.
        String hash = encontrado.map(Usuario::getSenhaHash).orElse(hashFalso);
        boolean cabeNoBcrypt = senha.getBytes(UTF_8).length <= RegistroService.LIMITE_DA_SENHA_EM_BYTES;
        boolean confere = cabeNoBcrypt && codificador.matches(senha, hash);

        if (encontrado.isEmpty() || !confere) {
            throw ErroDeNegocio.naoAutenticado("E-mail ou senha inválidos.");
        }
        Usuario usuario = encontrado.get();
        return new ResultadoDeLogin(sessoes.montar(usuario), emissor.emitir(usuario.getId()));
    }
}
