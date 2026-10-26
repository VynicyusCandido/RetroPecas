package br.com.autopecas.conta.internal.infra.seguranca;

import br.com.autopecas.conta.internal.application.CalculadoraDeAuthId;
import br.com.autopecas.conta.internal.infra.persistencia.TokenRevogadoRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
@EnableConfigurationProperties(SegurancaProperties.class)
class ConfiguracaoDeSeguranca {

    static final String EMISSOR = "autopecas";
    private static final String ALGORITMO_DA_CHAVE = "HmacSHA256";

    @Bean
    PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    JwtEncoder codificadorDeJwt(SegurancaProperties propriedades) {
        SecretKey chave = new SecretKeySpec(propriedades.jwtChave(), ALGORITMO_DA_CHAVE);
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
    }

    @Bean
    JwtDecoder decodificadorDeJwt(SegurancaProperties propriedades, TokenRevogadoRepository revogados) {
        SecretKey chave = new SecretKeySpec(propriedades.jwtChave(), ALGORITMO_DA_CHAVE);
        NimbusJwtDecoder decodificador =
                NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
        decodificador.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(EMISSOR), new ValidadorDeTokenRevogado(revogados)));
        return decodificador;
    }

    /**
     * CSRF do Spring fica desligado de propósito: o cookie é SameSite=Lax e toda requisição autenticada
     * (menos GET /api/v1/me) exige o cabeçalho X-Auth-Id, que outro site não consegue enviar.
     */
    @Bean
    SecurityFilterChain cadeiaDeSeguranca(HttpSecurity http, CalculadoraDeAuthId calculadora) throws Exception {
        AuthenticationEntryPoint naoAutenticado =
                (requisicao, resposta, erro) -> RespostaDeProblema.escrever(resposta, 401, "Não autenticado");
        AccessDeniedHandler acessoNegado =
                (requisicao, resposta, erro) -> RespostaDeProblema.escrever(resposta, 403, "Acesso negado");

        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(regras -> regras
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/registro", "/api/v1/auth/login")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(recurso -> recurso
                        .bearerTokenResolver(new CookieBearerTokenResolver())
                        .authenticationEntryPoint(naoAutenticado)
                        .jwt(Customizer.withDefaults()))
                .exceptionHandling(erros -> erros.authenticationEntryPoint(naoAutenticado)
                        .accessDeniedHandler(acessoNegado))
                .addFilterAfter(new FiltroXAuthId(calculadora), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
