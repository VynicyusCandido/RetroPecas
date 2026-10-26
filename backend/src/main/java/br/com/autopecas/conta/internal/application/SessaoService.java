package br.com.autopecas.conta.internal.application;

import br.com.autopecas.compartilhado.ErroDeNegocio;
import br.com.autopecas.conta.internal.domain.PerfilVendedor;
import br.com.autopecas.conta.internal.domain.StatusVendedor;
import br.com.autopecas.conta.internal.domain.Usuario;
import br.com.autopecas.conta.internal.infra.persistencia.PerfilVendedorRepository;
import br.com.autopecas.conta.internal.infra.persistencia.UsuarioRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessaoService {

    private final UsuarioRepository usuarios;
    private final PerfilVendedorRepository perfis;
    private final CalculadoraDeAuthId calculadora;

    SessaoService(UsuarioRepository usuarios, PerfilVendedorRepository perfis, CalculadoraDeAuthId calculadora) {
        this.usuarios = usuarios;
        this.perfis = perfis;
        this.calculadora = calculadora;
    }

    @Transactional(readOnly = true)
    public SessaoDoUsuario montar(Usuario usuario) {
        StatusVendedor vendedor = perfis.findById(usuario.getId()).map(PerfilVendedor::getStatus).orElse(null);
        return new SessaoDoUsuario(
                usuario.getId(), usuario.getNome(), usuario.getEmail(), vendedor, calculadora.calcular(usuario.getId()));
    }

    /** Um usuário removido não tem mais sessão, mesmo com um token ainda dentro da validade. */
    @Transactional(readOnly = true)
    public SessaoDoUsuario obter(UUID usuarioId) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> ErroDeNegocio.naoAutenticado("Sessão inválida."));
        return montar(usuario);
    }
}
