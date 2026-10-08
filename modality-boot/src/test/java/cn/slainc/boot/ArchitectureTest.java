package cn.slainc.boot;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "cn.slainc")
class ArchitectureTest {

    private static final String[] LAYERED_PACKAGES = {
            "..cn.slainc.utils..", "..cn.slainc.crypto..", "..cn.slainc.core..", "..cn.slainc.web..",
            "..cn.slainc.excel..", "..cn.slainc.datasource..", "..cn.slainc.mybatis..", "..cn.slainc.redis..",
            "..cn.slainc.tenant..", "..cn.slainc.idempotent..", "..cn.slainc.storage..", "..cn.slainc.logger..",
            "..cn.slainc.monitor..", "..cn.slainc.security..", "..cn.slainc.jwt..", "..cn.slainc.oauth2..",
            "..cn.slainc.sso..", "..cn.slainc.apikey..", "..cn.slainc.captcha..", "..cn.slainc.sign..",
            "..cn.slainc.session..", "..cn.slainc.sensitive..", "..cn.slainc.openapi..", "..cn.slainc.policy..",
            "..cn.slainc.thirdauth.."
    };

    private static String[] layersAbove(String... kept) {
        return java.util.Arrays.stream(LAYERED_PACKAGES)
                .filter(pkg -> java.util.Arrays.stream(kept).noneMatch(pkg::equals))
                .toArray(String[]::new);
    }

    @ArchTest
    static final ArchRule utilsMustNotDependOnUpperLayers = noClasses()
            .that().resideInAPackage("..cn.slainc.utils..")
            .should().dependOnClassesThat().resideInAnyPackage(layersAbove("..cn.slainc.utils.."));

    @ArchTest
    static final ArchRule cryptoMustNotDependOnCoreOrAbove = noClasses()
            .that().resideInAPackage("..cn.slainc.crypto..")
            .should().dependOnClassesThat().resideInAnyPackage(layersAbove("..cn.slainc.utils..", "..cn.slainc.crypto.."));

    @ArchTest
    static final ArchRule coreMustNotDependOnWebOrAbove = noClasses()
            .that().resideInAPackage("..cn.slainc.core..")
            .should().dependOnClassesThat().resideInAnyPackage(layersAbove("..cn.slainc.utils..", "..cn.slainc.crypto..", "..cn.slainc.core.."));

    @ArchTest
    static final ArchRule librariesMustNotDependOnBusinessModulesOrBoot = noClasses()
            .that().resideInAnyPackage(LAYERED_PACKAGES)
            .should().dependOnClassesThat().resideInAnyPackage("..cn.slainc.modules..", "..cn.slainc.boot..");

    @ArchTest
    static final ArchRule businessModulesMustNotDependOnEachOther = SlicesRuleDefinition.slices()
            .matching("..cn.slainc.modules.(*)..")
            .should().notDependOnEachOther();
}
