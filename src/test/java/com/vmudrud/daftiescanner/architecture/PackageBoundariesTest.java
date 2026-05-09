package com.vmudrud.daftiescanner.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.vmudrud.daftiescanner")
class PackageBoundariesTest {

    @ArchTest
    static final ArchRule search_does_not_depend_on_notification =
            noClasses().that().resideInAPackage("..search..")
                    .should().dependOnClassesThat().resideInAPackage("..notification..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule search_does_not_depend_on_aianalyzer =
            noClasses().that().resideInAPackage("..search..")
                    .should().dependOnClassesThat().resideInAPackage("..aianalyzer..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule notification_does_not_depend_on_search =
            noClasses().that().resideInAPackage("..notification..")
                    .should().dependOnClassesThat().resideInAPackage("..search..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule notification_does_not_depend_on_aianalyzer =
            noClasses().that().resideInAPackage("..notification..")
                    .should().dependOnClassesThat().resideInAPackage("..aianalyzer..")
                    .allowEmptyShould(true);
}
