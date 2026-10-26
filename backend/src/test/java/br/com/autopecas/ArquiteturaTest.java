package br.com.autopecas;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/** As fronteiras entre módulos e entre camadas são verificadas em todo build. */
class ArquiteturaTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("br.com.autopecas");

    @Test
    void osModulosSoSeEnxergamPelaApiPublica() {
        ApplicationModules.of(AutopecasApplication.class).verify();
    }

    @Test
    void oDominioNaoDependeDeOutrasCamadasNemDoSpring() {
        noClasses()
                .that()
                .resideInAPackage("..internal.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..internal.web..", "..internal.application..", "..internal.infra..", "org.springframework..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void aCamadaWebNaoAcessaARepositorios() {
        noClasses()
                .that()
                .resideInAPackage("..internal.web..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..internal.infra.persistencia..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void aCamadaDeAplicacaoNaoDependeDaWeb() {
        noClasses()
                .that()
                .resideInAPackage("..internal.application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..internal.web..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
