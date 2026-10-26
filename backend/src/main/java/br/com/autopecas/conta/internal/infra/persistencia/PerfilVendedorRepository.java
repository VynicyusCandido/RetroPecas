package br.com.autopecas.conta.internal.infra.persistencia;

import br.com.autopecas.conta.internal.domain.PerfilVendedor;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfilVendedorRepository extends JpaRepository<PerfilVendedor, UUID> {
}
