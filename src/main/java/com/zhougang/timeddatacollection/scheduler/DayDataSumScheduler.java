package com.zhougang.timeddatacollection.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.zhougang.timeddatacollection.service.BlastFurnacePciQueryService;
import com.zhougang.timeddatacollection.service.CokeMaterialQueryService;
import com.zhougang.timeddatacollection.service.FilterMaterialQueryService;
import com.zhougang.timeddatacollection.service.IronCokeQueryService;
import com.zhougang.timeddatacollection.service.JmStorageService;
import com.zhougang.timeddatacollection.service.SinteredCokeQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

/**
 * 日度数据合计定时任务
 * <p>
 * 每天0点5分0秒运行，回采昨天以及过去一年（共 collectDays 天）的材料数据之和：
 * 焦炭 = 筛选材料查询（materialType=1）netWgt 合计 + 焦炭材料查询 transportWeight 合计；
 * 喷煤 = 筛选材料查询（materialType=2）按"高挥发"/"低硫"分别汇总，存储为"烟煤"/"无烟煤"；
 * 烧结焦炭 = 焦炭材料流转查询（endPos=ST1_LC_RP）transportWeight 合计；
 * 炼铁焦炭 = 焦炭材料流转查询（两组 matCodeList 各查一次）transportWeight 合计之和；
 * 高炉高挥发喷煤 = 焦炭材料流转查询（终点 BF1_LC_PM1~PM5，matCode=1220000008）transportWeight 合计；
 * 高炉低硫贫瘦喷煤 = 同一接口（matCode=1220000015）transportWeight 合计，与前者分别独立入库。
 * 结果分别以 type=焦炭 / 烟煤 / 无烟煤 / 烧结焦炭 / 炼铁焦炭 / 高炉高挥发喷煤 / 高炉低硫贫瘦喷煤
 * 写入 jm_day 表，已存在的数据会被覆盖
 */
@Component
public class DayDataSumScheduler {

    private static final Logger log = LoggerFactory.getLogger(DayDataSumScheduler.class);

    /** 回采天数：昨天 + 往前共几天，默认 365 */
    @Value("${timedata.collect.days:365}")
    private int collectDays;

    private final FilterMaterialQueryService filterMaterialQueryService;
    private final CokeMaterialQueryService cokeMaterialQueryService;
    private final SinteredCokeQueryService sinteredCokeQueryService;
    private final IronCokeQueryService ironCokeQueryService;
    private final BlastFurnacePciQueryService blastFurnacePciQueryService;
    private final JmStorageService jmStorageService;
    private final CollectRateLimiter rateLimiter;

    public DayDataSumScheduler(FilterMaterialQueryService filterMaterialQueryService,
                               CokeMaterialQueryService cokeMaterialQueryService,
                               SinteredCokeQueryService sinteredCokeQueryService,
                               IronCokeQueryService ironCokeQueryService,
                               BlastFurnacePciQueryService blastFurnacePciQueryService,
                               JmStorageService jmStorageService,
                               CollectRateLimiter rateLimiter) {
        this.filterMaterialQueryService = filterMaterialQueryService;
        this.cokeMaterialQueryService = cokeMaterialQueryService;
        this.sinteredCokeQueryService = sinteredCokeQueryService;
        this.ironCokeQueryService = ironCokeQueryService;
        this.blastFurnacePciQueryService = blastFurnacePciQueryService;
        this.jmStorageService = jmStorageService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 每天0点5分0秒运行：回采昨天以及过去一年的材料数据之和（共 collectDays 天）
     * <p>
     * 逐日采集，已存在的数据会被覆盖；每天之间加入延迟，避免触发上游限速。
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void scheduledCollectDaySum() {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        log.info("========== 日度数据合计回采开始: 昨天起往前共 {} 天 ==========", collectDays);

        for (int i = 1; i <= collectDays; i++) {
            LocalDate date = today.minusDays(i);
            long dayStartTime = date.atStartOfDay(zone).toInstant().toEpochMilli();
            log.info("{}", dayStartTime);
            try {
                collectDaySum(dayStartTime);
            } catch (Exception e) {
                // 单日失败不中断整体回采
                log.error("日度数据合计采集失败, date={}: {}", date, e.getMessage(), e);
            }
            rateLimiter.pause();
        }

        log.info("========== 日度数据合计回采结束: 共处理 {} 天 ==========", collectDays);
    }

    /**
     * 计算指定某一天的材料数据之和并入库
     *
     * @param dayStartTime 指定日期当天0点0分0秒的时间戳（毫秒）
     */
    public void collectDaySum(long dayStartTime) throws JsonProcessingException {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate day = Instant.ofEpochMilli(dayStartTime).atZone(zone).toLocalDate();

        long startTime = dayStartTime;
        long endTime = day.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli();

        log.info("采集日数据时间范围: {}, startTime={} (00:00:00), endTime={} (23:59:59)",
                day, startTime, endTime);

        // 焦炭：筛选材料查询(materialType=1) + 焦炭材料查询
        Map<String, Double> cokeFilterMap = filterMaterialQueryService.queryNetWeight(startTime, endTime, 1);
        double cokeFilterNetWeight = cokeFilterMap.getOrDefault("焦炭", 0.0);
        double cokeTransportWeight = cokeMaterialQueryService.queryTotalWeight(startTime, endTime);
        double cokeTotal = cokeFilterNetWeight + cokeTransportWeight;
        jmStorageService.storeDay("焦炭", cokeTotal, startTime);

        // 喷煤：筛选材料查询(materialType=2)，按"烟煤"/"无烟煤"分别入库
        Map<String, Double> pciFilterMap = filterMaterialQueryService.queryNetWeight(startTime, endTime, 2);
        for (Map.Entry<String, Double> entry : pciFilterMap.entrySet()) {
            jmStorageService.storeDay(entry.getKey(), entry.getValue(), startTime);
        }

        // 烧结焦炭：焦炭材料流转查询（endPos=ST1_LC_RP）
        double sinteredCoke = sinteredCokeQueryService.queryTotalWeight(startTime, endTime);
        jmStorageService.storeDay("烧结焦炭", sinteredCoke, startTime);

        // 炼铁焦炭：两组 matCodeList 各查一次，结果相加
        double ironCoke = ironCokeQueryService.queryTotalWeight(startTime, endTime);
        jmStorageService.storeDay("炼铁焦炭", ironCoke, startTime);

        // 高炉高挥发喷煤：焦炭材料流转查询（终点 BF1_LC_PM1~PM5，matCode=1220000008）
        double highVolatilePci = blastFurnacePciQueryService.queryHighVolatileWeight(startTime, endTime);
        jmStorageService.storeDay("高炉高挥发喷煤", highVolatilePci, startTime);

        // 高炉低硫贫瘦喷煤：同一接口，matCode=1220000015，与高挥发喷煤分别独立入库
        double lowSulfurLeanPci = blastFurnacePciQueryService.queryLowSulfurLeanWeight(startTime, endTime);
        jmStorageService.storeDay("高炉低硫贫瘦喷煤", lowSulfurLeanPci, startTime);

        log.info("日度采集完成: 焦炭总和={}, 喷煤明细={}, 烧结焦炭={}, 炼铁焦炭={}, 高炉高挥发喷煤={}, 高炉低硫贫瘦喷煤={}",
                cokeTotal, pciFilterMap, sinteredCoke, ironCoke, highVolatilePci, lowSulfurLeanPci);
    }

}
