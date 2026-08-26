package com.zhougang.timeddatacollection.scheduler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.zhougang.timeddatacollection.service.CokeMaterialQueryService;
import com.zhougang.timeddatacollection.service.FilterMaterialQueryService;
import com.zhougang.timeddatacollection.service.JmStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 每月1号0点0分0秒运行，汇总上个月整个月的材料数据之和：
 * 焦炭 = 筛选材料查询（materialType=1）netWgt 合计 + 焦炭材料查询 transportWeight 合计；
 * 喷煤 = 筛选材料查询（materialType=2）按"高挥发"/"低硫"分别汇总，存储为"烟煤"/"无烟煤"。
 * 结果分别以 type=焦炭 / 烟煤 / 无烟煤 写入 jm_month 表
 */
@Component
public class MonthDataSumScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthDataSumScheduler.class);

    private final FilterMaterialQueryService filterMaterialQueryService;
    private final CokeMaterialQueryService cokeMaterialQueryService;
    private final JmStorageService jmStorageService;

    public MonthDataSumScheduler(FilterMaterialQueryService filterMaterialQueryService,
                                 CokeMaterialQueryService cokeMaterialQueryService,
                                 JmStorageService jmStorageService) {
        this.filterMaterialQueryService = filterMaterialQueryService;
        this.cokeMaterialQueryService = cokeMaterialQueryService;
        this.jmStorageService = jmStorageService;
    }

    /**
     * 每月1号0点0分0秒运行：采集上个月整个月的材料数据之和
     */
    @Scheduled(cron = "0 0 0 1 * ?")
    public void scheduledCollectMonthSum() {
        log.info("========== 月度数据合计采集触发 ==========");
        try {
            ZoneId zone = ZoneId.systemDefault();
            YearMonth lastMonth = YearMonth.now().minusMonths(1);
            long monthStartTime = lastMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
            collectMonthSum(monthStartTime);
            log.info("========== 月度数据合计采集完成 ==========");
        } catch (Exception e) {
            log.error("月度数据合计采集失败: {}", e.getMessage(), e);
        }
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

            log.info("采集完成第 {} 天 {}: 焦炭={}, 烟煤={}, 无烟煤={}, 累计焦炭={}, 累计烟煤={}, 累计无烟煤={}",
                    day, date, cokeDay, bituminousDay, anthraciteDay, cokeTotal, bituminousTotal, anthraciteTotal);
        }

        // 入库：焦炭 / 烟煤 / 无烟煤，total=整月累加，time=当月1号0点0分0秒
        jmStorageService.storeMonth("焦炭", cokeTotal, monthStartTime);
        jmStorageService.storeMonth("烟煤", bituminousTotal, monthStartTime);
        jmStorageService.storeMonth("无烟煤", anthraciteTotal, monthStartTime);

        log.info("月度采集完成: 焦炭总和={}, 烟煤(高挥发)总和={}, 无烟煤(低硫)总和={}",
                cokeTotal, bituminousTotal, anthraciteTotal);
    }

}
