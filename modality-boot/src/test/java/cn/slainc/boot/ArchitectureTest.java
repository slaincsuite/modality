package cn.slainc.boot;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "cn.slainc")
class ArchitectureTest {

    private static final String[] LAYERED_PACKAGES = {
            "..cn.slainc.utils..", "..cn.slainc.crypto..", "..cn.slainc.core..", "..cn.slainc.workorder..",
            "..cn.slainc.web..", "..cn.slainc.excel..", "..cn.slainc.datasource..", "..cn.slainc.mybatis..",
            "..cn.slainc.redis..", "..cn.slainc.recycle..", "..cn.slainc.search..", "..cn.slainc.changeaudit..",
            "..cn.slainc.job..", "..cn.slainc.sse..", "..cn.slainc.webhook..", "..cn.slainc.tenant..",
            "..cn.slainc.idempotent..", "..cn.slainc.storage..", "..cn.slainc.logger..", "..cn.slainc.monitor..",
            "..cn.slainc.security..", "..cn.slainc.mfa..", "..cn.slainc.jwt..", "..cn.slainc.oauth2..",
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

    // 库内禁止使用模块独占包名（service/web/dto/entity/mapper/model/impl/vo/form/controller/repository），web 库本体豁免
    @ArchTest
    static final ArchRule librariesMustNotUseModuleReservedPackages = noClasses()
            .that().resideInAPackage("..cn.slainc..")
            .and().resideOutsideOfPackage("..cn.slainc.modules..")
            .and().resideOutsideOfPackage("..cn.slainc.web..")
            .should().resideInAnyPackage(
                    "..service..", "..web..", "..dto..", "..entity..", "..mapper..", "..model..",
                    "..impl..", "..vo..", "..form..", "..controller..", "..repository..");

    // REST 控制器只允许出现在业务模块的 web 包
    @ArchTest
    static final ArchRule restControllersOnlyInModuleWebPackage = noClasses()
            .that().resideOutsideOfPackage("..cn.slainc.modules..web..")
            .and().resideOutsideOfPackage("..cn.slainc.boot..")
            .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .orShould().beAnnotatedWith("org.springframework.web.bind.annotation.Controller");
}
