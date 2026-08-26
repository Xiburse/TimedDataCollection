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
import java.time.ZoneId;
import java.util.Map;

/**
 * 日度数据合计定时任务
 * <p>
 * 每天0点5分0秒运行，汇总前一天的材料数据之和：
 * 焦炭 = 筛选材料查询（materialType=1）netWgt 合计 + 焦炭材料查询 transportWeight 合计；
 * 喷煤 = 筛选材料查询（materialType=2）按"高挥发"/"低硫"分别汇总，存储为"烟煤"/"无烟煤"。
 * 结果分别以 type=焦炭 / 烟煤 / 无烟煤 写入 jm_day 表
 */
@Component
public class DayDataSumScheduler {

    private static final Logger log = LoggerFactory.getLogger(DayDataSumScheduler.class);

    private final FilterMaterialQueryService filterMaterialQueryService;
    private final CokeMaterialQueryService cokeMaterialQueryService;
    private final JmStorageService jmStorageService;

    public DayDataSumScheduler(FilterMaterialQueryService filterMaterialQueryService,
                               CokeMaterialQueryService cokeMaterialQueryService,
                               JmStorageService jmStorageService) {
        this.filterMaterialQueryService = filterMaterialQueryService;
        this.cokeMaterialQueryService = cokeMaterialQueryService;
        this.jmStorageService = jmStorageService;
    }

    /**
     * 每天0点5分0秒运行：采集前一天的材料数据之和
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void scheduledCollectDaySum() {
        log.info("========== 日度数据合计采集触发 ==========");
        try {
            ZoneId zone = ZoneId.systemDefault();
            LocalDate yesterday = LocalDate.now().minusDays(1);
            long dayStartTime = yesterday.atStartOfDay(zone).toInstant().toEpochMilli();
            collectDaySum(dayStartTime);
            log.info("========== 日度数据合计采集完成 ==========");
        } catch (Exception e) {
            log.error("日度数据合计采集失败: {}", e.getMessage(), e);
        }
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

        log.info("日度采集完成: 焦炭总和={}, 喷煤明细={}", cokeTotal, pciFilterMap);
    }

}
