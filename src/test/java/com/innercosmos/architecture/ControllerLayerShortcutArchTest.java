package com.innercosmos.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ARCH-MODULES: real, bytecode-based (not regex-on-source-text) layer-boundary rules, added
 * alongside {@link DomainBoundaryArchitectureTest}'s existing "no upward dependency" checks.
 * This class covers the gap that test doesn't: a controller reaching *downward*, past the service
 * layer, straight into a mapper or a service.impl concrete class.
 *
 * See 对齐文档/ADR-0001-arch-modules.md for why this replaces a Spring Modulith migration.
 *
 * Both rules below grandfather a documented, closed set of pre-existing violations discovered
 * while adding this test (never silently grown -- shrink it as each controller is refactored to
 * go through its service interface instead).
 */
class ControllerLayerShortcutArchTest {
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.innercosmos");

    /** Pre-existing controllers that inject a mapper directly instead of a service method. */
    private static final Set<Class<?>> GRANDFATHERED_MAPPER_ACCESS = Set.of(
            com.innercosmos.controller.SocialController.class,
            com.innercosmos.controller.AuroraChatController.class
    );

    /** Pre-existing controllers that inject a concrete service.impl class instead of a service interface. */
    private static final Set<Class<?>> GRANDFATHERED_IMPL_ACCESS = Set.of(
            com.innercosmos.controller.DailyRecordController.class
    );

    @Test
    void controllersMustNotDependOnMappersDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.innercosmos.controller..")
                .and(not(belongToAnyOf(GRANDFATHERED_MAPPER_ACCESS.toArray(new Class<?>[0]))))
                .should().dependOnClassesThat().resideInAPackage("com.innercosmos.mapper..")
                .because("a controller should reach persistence through a service method, not a mapper directly; "
                        + "see the GRANDFATHERED_MAPPER_ACCESS allowlist for known pre-existing exceptions");
        rule.check(CLASSES);
    }

    @Test
    void controllersMustNotDependOnServiceImplClassesDirectly() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.innercosmos.controller..")
                .and(not(belongToAnyOf(GRANDFATHERED_IMPL_ACCESS.toArray(new Class<?>[0]))))
                .should().dependOnClassesThat().resideInAPackage("com.innercosmos.service.impl..")
                .because("a controller should depend on a service interface, not a concrete service.impl class; "
                        + "see the GRANDFATHERED_IMPL_ACCESS allowlist for known pre-existing exceptions");
        rule.check(CLASSES);
    }

    @Test
    void grandfatheredMapperAccessListNamesOnlyRealControllersThatCurrentlyViolateTheRule() {
        for (Class<?> allowed : GRANDFATHERED_MAPPER_ACCESS) {
            JavaClass javaClass = CLASSES.get(allowed);
            boolean actuallyDependsOnMapper = javaClass.getDirectDependenciesFromSelf().stream()
                    .anyMatch(dep -> dep.getTargetClass().getPackageName().startsWith("com.innercosmos.mapper"));
            org.junit.jupiter.api.Assertions.assertTrue(actuallyDependsOnMapper,
                    allowed.getSimpleName() + " no longer depends on a mapper directly -- shrink GRANDFATHERED_MAPPER_ACCESS");
        }
    }

    @Test
    void grandfatheredImplAccessListNamesOnlyRealControllersThatCurrentlyViolateTheRule() {
        for (Class<?> allowed : GRANDFATHERED_IMPL_ACCESS) {
            JavaClass javaClass = CLASSES.get(allowed);
            boolean actuallyDependsOnImpl = javaClass.getDirectDependenciesFromSelf().stream()
                    .anyMatch(dep -> dep.getTargetClass().getPackageName().startsWith("com.innercosmos.service.impl"));
            org.junit.jupiter.api.Assertions.assertTrue(actuallyDependsOnImpl,
                    allowed.getSimpleName() + " no longer depends on service.impl directly -- shrink GRANDFATHERED_IMPL_ACCESS");
        }
    }
}
