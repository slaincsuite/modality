package cn.slainc.modules.open.service;

import cn.slainc.apikey.service.ApiKeyStatsService;
import cn.slainc.modules.open.store.ConsentQueryDao;
import cn.slainc.openapi.model.App;
import cn.slainc.openapi.model.AppStatus;
import cn.slainc.openapi.model.AppCredential;
import cn.slainc.openapi.model.ConsentRecord;
import cn.slainc.openapi.spi.AppService;
import cn.slainc.openapi.spi.ConsentService;
import cn.slainc.openapi.spi.CredentialService;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 开放平台统计：应用规模、授权规模、按天趋势、应用排行 TOP N 与服务端应用 API Key 调用量
public class OpenStatsService {

    private final AppService appService;

    private final ConsentService consentService;

    private final CredentialService credentialService;

    private final ObjectProvider<ConsentQueryDao> queryDao;

    private final ObjectProvider<ApiKeyStatsService> apiKeyStats;

    private final int trendDays;

    private final int topN;

    public OpenStatsService(AppService appService, ConsentService consentService,
                            CredentialService credentialService, ObjectProvider<ConsentQueryDao> queryDao,
                            ObjectProvider<ApiKeyStatsService> apiKeyStats, int trendDays, int topN) {
        this.appService = appService;
        this.consentService = consentService;
        this.credentialService = credentialService;
        this.queryDao = queryDao;
        this.apiKeyStats = apiKeyStats;
        this.trendDays = Math.max(trendDays, 1);
        this.topN = Math.max(topN, 1);
    }

    public Map<String, Object> overview() {
        List<App> apps = appService.search(null, null, null);
        List<ConsentRecord> activeConsents = apps.stream()
                .flatMap(app -> consentService.listByApp(app.getAppId()).stream())
                .filter(ConsentRecord::active)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalApps", apps.size());
        result.put("onlineApps", apps.stream().filter(app -> app.getStatus() == AppStatus.ONLINE).count());
        result.put("reviewingApps", apps.stream().filter(app -> app.getStatus() == AppStatus.REVIEWING).count());
        result.put("totalConsents", activeConsents.size());
        result.put("consentUsers", activeConsents.stream().map(ConsentRecord::getUserId).distinct().count());
        result.put("trend", consentTrend(activeConsents));
        result.put("appTop", appTop(activeConsents, apps));
        result.put("apiKeyCalls", apiKeyCalls(apps));
        return result;
    }

    private Map<String, Long> consentTrend(List<ConsentRecord> consents) {
        LocalDate today = LocalDate.now();
        Map<String, Long> byDay = consents.stream()
                .collect(Collectors.groupingBy(
                        record -> LocalDate.ofInstant(java.time.Instant.ofEpochMilli(record.getGrantedAt()),
                                java.time.ZoneId.systemDefault()).toString(),
                        Collectors.counting()));
        Map<String, Long> trend = new LinkedHashMap<>();
        for (int i = trendDays - 1; i >= 0; i--) {
            String day = today.minusDays(i).toString();
            trend.put(day, byDay.getOrDefault(day, 0L));
        }
        return trend;
    }

    private List<Map<String, Object>> appTop(List<ConsentRecord> consents, List<App> apps) {
        Map<String, String> appNames = apps.stream()
                .collect(Collectors.toMap(App::getAppId, App::getName, (left, right) -> left));
        Map<String, Long> counts = consents.stream()
                .collect(Collectors.groupingBy(ConsentRecord::getAppId, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(topN)
                .map(entry -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("appId", entry.getKey());
                    row.put("appName", appNames.getOrDefault(entry.getKey(), entry.getKey()));
                    row.put("consents", entry.getValue());
                    return row;
                })
                .toList();
    }

    // 服务端应用的调用次数来自 api-key 库的 Redis 统计；无该能力（无 Redis）时返回 null 而非假装有数
    private Long apiKeyCalls(List<App> apps) {
        ApiKeyStatsService stats = apiKeyStats.getIfAvailable();
        if (stats == null) {
            return null;
        }
        long total = 0;
        boolean anyKey = false;
        for (App app : apps) {
            AppCredential credential;
            try {
                credential = credentialService.find(app.getAppId());
            } catch (RuntimeException e) {
                continue;
            }
            if (credential.getType() != AppCredential.Type.API_KEY || credential.getClientId() == null) {
                continue;
            }
            anyKey = true;
            total += stats.total(credential.getClientId());
        }
        return anyKey ? total : 0L;
    }
}
