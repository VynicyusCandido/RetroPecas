package br.com.autopecas.conta.internal.infra.persistencia;

import br.com.autopecas.conta.internal.domain.Usuario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * As buscas por e-mail comparam com {@code lower(...)} de propósito: é a expressão do índice único
 * {@code ux_usuario_email}. O {@code IgnoreCase} derivado do Spring Data gera {@code upper(...)}, que não usa o índice.
 */
public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    @Query("select u from Usuario u where lower(u.email) = lower(:email)")
    Optional<Usuario> findByEmailIgnoreCase(@Param("email") String email);

    @Query("select count(u) > 0 from Usuario u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);
}
