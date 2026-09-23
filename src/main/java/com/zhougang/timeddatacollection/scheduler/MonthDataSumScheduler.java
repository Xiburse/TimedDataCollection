package com.zhougang.timeddatacollection.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Map;

/**
 * 月度数据合计定时任务
 * <p>
 * 每月1号0点0分0秒运行，回采上个月以及过去一年（共 collectMonths 个月）的材料数据之和：
 * 焦炭 = 筛选材料查询（materialType=1）netWgt 合计 + 焦炭材料查询 transportWeight 合计；
 * 喷煤 = 筛选材料查询（materialType=2）按"高挥发"/"低硫"分别汇总，存储为"烟煤"/"无烟煤"；
 * 烧结焦炭 = 焦炭材料流转查询（endPos=ST1_LC_RP）transportWeight 合计；
 * 炼铁焦炭 = 焦炭材料流转查询（两组 matCodeList 各查一次）transportWeight 合计之和。
 * 结果分别以 type=焦炭 / 烟煤 / 无烟煤 / 烧结焦炭 / 炼铁焦炭 写入 jm_month 表，已存在的数据会被覆盖
 */
@Component
public class MonthDataSumScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthDataSumScheduler.class);

    /** 回采月数：昨月 + 往前共几个月，默认 12 */
    @Value("${timedata.collect.months:12}")
    private int collectMonths;

    private final FilterMaterialQueryService filterMaterialQueryService;
    private final CokeMaterialQueryService cokeMaterialQueryService;
    private final SinteredCokeQueryService sinteredCokeQueryService;
    private final IronCokeQueryService ironCokeQueryService;
    private final JmStorageService jmStorageService;
    private final CollectRateLimiter rateLimiter;

    public MonthDataSumScheduler(FilterMaterialQueryService filterMaterialQueryService,
                                 CokeMaterialQueryService cokeMaterialQueryService,
                                 SinteredCokeQueryService sinteredCokeQueryService,
                                 IronCokeQueryService ironCokeQueryService,
                                 JmStorageService jmStorageService,
                                 CollectRateLimiter rateLimiter) {
        this.filterMaterialQueryService = filterMaterialQueryService;
        this.cokeMaterialQueryService = cokeMaterialQueryService;
        this.sinteredCokeQueryService = sinteredCokeQueryService;
        this.ironCokeQueryService = ironCokeQueryService;
        this.jmStorageService = jmStorageService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 每月1号0点0分0秒运行：回采上个月以及过去一年的材料数据之和（共 collectMonths 个月）
     * <p>
     * 逐月采集，已存在的数据会被覆盖；月内逐日采集之间同样加入延迟，避免触发上游限速。
     */
    @Scheduled(cron = "0 0 0 1 * ?")
    public void scheduledCollectMonthSum() {
        ZoneId zone = ZoneId.systemDefault();
        YearMonth thisMonth = YearMonth.now();
        log.info("========== 月度数据合计回采开始: 昨月起往前共 {} 个月 ==========", collectMonths);

        for (int i = 1; i <= collectMonths; i++) {
            YearMonth month = thisMonth.minusMonths(i);
            long monthStartTime = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
            try {
                collectMonthSum(monthStartTime);
            } catch (Exception e) {
                // 单月失败不中断整体回采
                log.error("月度数据合计采集失败, month={}: {}", month, e.getMessage(), e);
            }
            rateLimiter.pause();
        }

        log.info("========== 月度数据合计回采结束: 共处理 {} 个月 ==========", collectMonths);
    }

    /**
     * 计算指定某个月的材料数据之和并入库
     * <p>
     * 逐日采集该月每一天的焦炭/喷煤数据，累加后作为月数据入库
     *
     * @param monthStartTime 指定月份1号0点0分0秒的时间戳（毫秒）
     */
    public void collectMonthSum(long monthStartTime) throws JsonProcessingException {
        ZoneId zone = ZoneId.systemDefault();
        YearMonth month = YearMonth.from(Instant.ofEpochMilli(monthStartTime).atZone(zone).toLocalDate());
        int daysInMonth = month.lengthOfMonth();

        log.info("采集月数据: {}, 共 {} 天，逐日累加", month, daysInMonth);

        double cokeTotal = 0;
        double bituminousTotal = 0;   // 烟煤（高挥发）
        double anthraciteTotal = 0;   // 无烟煤（低硫）
        double sinteredCokeTotal = 0; // 烧结焦炭
        double ironCokeTotal = 0;     // 炼铁焦炭

        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = month.atDay(day);
            long dayStartTime = date.atStartOfDay(zone).toInstant().toEpochMilli();
            long dayEndTime = date.atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli();

            // 焦炭：筛选材料查询(materialType=1) + 焦炭材料查询
            Map<String, Double> cokeFilterMap = filterMaterialQueryService.queryNetWeight(dayStartTime, dayEndTime, 1);
            double cokeFilterNetWeight = cokeFilterMap.getOrDefault("焦炭", 0.0);
            double cokeTransportWeight = cokeMaterialQueryService.queryTotalWeight(dayStartTime, dayEndTime);
            double cokeDay = cokeFilterNetWeight + cokeTransportWeight;
            cokeTotal += cokeDay;

            // 喷煤：筛选材料查询(materialType=2)，返回 {烟煤: 高挥发合计, 无烟煤: 低硫合计}
            Map<String, Double> pciFilterMap = filterMaterialQueryService.queryNetWeight(dayStartTime, dayEndTime, 2);
            double bituminousDay = pciFilterMap.getOrDefault("烟煤", 0.0);
            double anthraciteDay = pciFilterMap.getOrDefault("无烟煤", 0.0);
            bituminousTotal += bituminousDay;
            anthraciteTotal += anthraciteDay;

            // 烧结焦炭：焦炭材料流转查询（endPos=ST1_LC_RP）
            double sinteredCokeDay = sinteredCokeQueryService.queryTotalWeight(dayStartTime, dayEndTime);
            sinteredCokeTotal += sinteredCokeDay;

            // 炼铁焦炭：两组 matCodeList 各查一次，结果相加
            double ironCokeDay = ironCokeQueryService.queryTotalWeight(dayStartTime, dayEndTime);
            ironCokeTotal += ironCokeDay;

            log.info("采集完成第 {} 天 {}: 焦炭={}, 烟煤={}, 无烟煤={}, 烧结焦炭={}, 炼铁焦炭={}, "
                            + "累计焦炭={}, 累计烟煤={}, 累计无烟煤={}, 累计烧结焦炭={}, 累计炼铁焦炭={}",
                    day, date, cokeDay, bituminousDay, anthraciteDay, sinteredCokeDay, ironCokeDay,
                    cokeTotal, bituminousTotal, anthraciteTotal, sinteredCokeTotal, ironCokeTotal);

            rateLimiter.pause();
        }

        // 入库：焦炭 / 烟煤 / 无烟煤 / 烧结焦炭 / 炼铁焦炭，total=整月累加，time=当月1号0点0分0秒
        jmStorageService.storeMonth("焦炭", cokeTotal, monthStartTime);
        jmStorageService.storeMonth("烟煤", bituminousTotal, monthStartTime);
        jmStorageService.storeMonth("无烟煤", anthraciteTotal, monthStartTime);
        jmStorageService.storeMonth("烧结焦炭", sinteredCokeTotal, monthStartTime);
        jmStorageService.storeMonth("炼铁焦炭", ironCokeTotal, monthStartTime);

        log.info("月度采集完成: 焦炭总和={}, 烟煤(高挥发)总和={}, 无烟煤(低硫)总和={}, 烧结焦炭总和={}, 炼铁焦炭总和={}",
                cokeTotal, bituminousTotal, anthraciteTotal, sinteredCokeTotal, ironCokeTotal);
    }

}
