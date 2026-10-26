package br.com.autopecas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** O esquema é do Liquibase: estas regras valem no banco, mesmo que a aplicação erre. */
@TesteDeIntegracao
class EsquemaDoBancoTest {

    @Autowired
    JdbcTemplate jdbc;

    private UUID inserirUsuario(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into usuario (id, email, senha_hash, nome) values (?, ?, 'hash', 'Nome')", id, email);
        return id;
    }

    @Test
    void oEmailEUnicoIgnorandoACaixa() {
        String base = "caixa-" + UUID.randomUUID();
        inserirUsuario(base + "@example.com");

        assertThatThrownBy(() -> inserirUsuario(base.toUpperCase() + "@EXAMPLE.COM"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oStatusDoVendedorSoAceitaOsValoresConhecidos() {
        UUID usuario = inserirUsuario("status-" + UUID.randomUUID() + "@example.com");

        assertThatThrownBy(() ->
                        jdbc.update("insert into perfil_vendedor (usuario_id, status) values (?, 'TALVEZ')", usuario))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oTipoDaNotificacaoSoAceitaOsValoresConhecidos() {
        UUID usuario = inserirUsuario("tipo-" + UUID.randomUUID() + "@example.com");

        assertThatThrownBy(() -> jdbc.update(
                        "insert into notificacao (id, usuario_id, tipo, titulo) values (?, ?, 'EMAIL', 'x')",
                        UUID.randomUUID(),
                        usuario))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removerUmUsuarioRemoveOPerfilEAsNotificacoes() {
        UUID usuario = inserirUsuario("cascata-" + UUID.randomUUID() + "@example.com");
        jdbc.update("insert into perfil_vendedor (usuario_id, status) values (?, 'APROVADO')", usuario);
        jdbc.update(
                "insert into notificacao (id, usuario_id, tipo, titulo) values (?, ?, 'CHAT', 'x')",
                UUID.randomUUID(),
                usuario);

        jdbc.update("delete from usuario where id = ?", usuario);

        assertThat(jdbc.queryForObject("select count(*) from perfil_vendedor where usuario_id = ?", Long.class, usuario))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from notificacao where usuario_id = ?", Long.class, usuario))
                .isZero();
    }

    @Test
    void oResumoDaNotificacaoTemValorPadraoVazioELidaComecaFalsa() {
        UUID usuario = inserirUsuario("padrao-" + UUID.randomUUID() + "@example.com");
        UUID id = UUID.randomUUID();

        jdbc.update("insert into notificacao (id, usuario_id, tipo, titulo) values (?, ?, 'SISTEMA', 'x')", id, usuario);

        assertThat(jdbc.queryForObject("select resumo from notificacao where id = ?", String.class, id)).isEmpty();
        assertThat(jdbc.queryForObject("select lida from notificacao where id = ?", Boolean.class, id)).isFalse();
    }
}
