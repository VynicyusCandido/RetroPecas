package br.com.autopecas.conta.internal.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import br.com.autopecas.compartilhado.ErroDeNegocio;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import java.time.Clock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroService {

    /** O BCrypt só lê os primeiros 72 bytes da senha. */
    static final int LIMITE_DA_SENHA_EM_BYTES = 72;

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final Clock relogio;

    RegistroService(UsuarioRepository usuarios, PasswordEncoder codificador, Clock relogio) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.relogio = relogio;
    }

    @Transactional
    public Usuario registrar(String nome, String email, String senha) {
        if (senha.getBytes(UTF_8).length > LIMITE_DA_SENHA_EM_BYTES) {
            throw ErroDeNegocio.requisicaoInvalida("A senha pode ter no máximo 72 bytes.");
        }
        if (usuarios.existsByEmailIgnoreCase(email.trim())) {
            throw ErroDeNegocio.conflito("Já existe uma conta com este e-mail.");
        }
        try {
            return usuarios.saveAndFlush(Usuario.novo(nome, email, codificador.encode(senha), relogio.instant()));
        } catch (DataIntegrityViolationException corrida) {
            // Duas requisições simultâneas com o mesmo e-mail: a segunda bate no índice único.
            throw ErroDeNegocio.conflito("Já existe uma conta com este e-mail.");
        }
    }
}
