package com.zhougang.timeddatacollection.controller;

import com.zhougang.timeddatacollection.scheduler.DataCollectionScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 手动更新控制器
 * 通过HTTP接口触发指定时间戳的数据采集
 */
@RestController
@RequestMapping("/update")
public class UpdateController {

    private static final Logger log = LoggerFactory.getLogger(UpdateController.class);

    private final DataCollectionScheduler scheduler;

    public UpdateController(DataCollectionScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * 日数据采集：根据指定时间戳执行一次日数据采集，存入 day 表
     *
     * @param time 查询时间戳（毫秒），通常为某天0点的时间戳
     * @return 操作结果
     */


    @GetMapping("/time/{time}")
    public Map<String, Object> updateByTime(@PathVariable("time") long time) {
        log.info("收到日数据更新请求, time={}", time);

        Map<String, Object> result = new HashMap<>();
        try {
            scheduler.collectData(time);
            result.put("success", true);
            result.put("message", "日数据采集完成");
            result.put("dataTime", time);
            result.put("table", "day");
        } catch (Exception e) {
            log.error("日数据更新失败, time={}: {}", time, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "日数据采集失败: " + e.getMessage());
            result.put("dataTime", time);
        }

        return result;
    }

    /**
     * 月数据采集：根据指定时间戳执行一次月数据采集（timeDivision=M），存入 month 表
     *
     * @param time 月份最早一天的时间戳（毫秒），通常为某月1号0点的时间戳
     * @return 操作结果
     */
    @GetMapping("/timemonth/{time}")
    public Map<String, Object> updateMonthByTime(@PathVariable("time") long time) {
        log.info("收到月数据更新请求, time={}", time);

        Map<String, Object> result = new HashMap<>();
        try {
            scheduler.collectMonthData(time);
            result.put("success", true);
            result.put("message", "月数据采集完成");
            result.put("dataTime", time);
            result.put("table", "month");
        } catch (Exception e) {
            log.error("月数据更新失败, time={}: {}", time, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "月数据采集失败: " + e.getMessage());
            result.put("dataTime", time);
        }

        return result;
    }

    /**
     * 老ID日数据采集：根据指定时间戳执行一次日数据采集，仅采集指定老ID，存储时 isOld=1
     *
     * @param time  查询时间戳（毫秒），通常为某天0点的时间戳
     * @param oldId 老ID
     * @return 操作结果
     */
    @GetMapping("/time/old/{time}/{oldId}")
    public Map<String, Object> updateByTimeOld(@PathVariable("time") long time,
                                               @PathVariable("oldId") String oldId) {
        log.info("收到老ID日数据更新请求, time={}, oldId={}", time, oldId);

        Map<String, Object> result = new HashMap<>();
        try {
            scheduler.collectOldData(time, oldId);
            result.put("success", true);
            result.put("message", "老ID日数据采集完成");
            result.put("dataTime", time);
            result.put("oldId", oldId);
            result.put("table", "day");
            result.put("isOld", 1);
        } catch (Exception e) {
            log.error("老ID日数据更新失败, time={}, oldId={}: {}", time, oldId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "老ID日数据采集失败: " + e.getMessage());
            result.put("dataTime", time);
            result.put("oldId", oldId);
        }

        return result;
    }

    /**
     * 老ID月数据采集：根据指定时间戳执行一次月数据采集，仅采集指定老ID，存储时 isOld=1
     *
     * @param time  月份最早一天的时间戳（毫秒），通常为某月1号0点的时间戳
     * @param oldId 老ID
     * @return 操作结果
     */
    @GetMapping("/timemonth/old/{time}/{oldId}")
    public Map<String, Object> updateMonthByTimeOld(@PathVariable("time") long time,
                                                    @PathVariable("oldId") String oldId) {
        log.info("收到老ID月数据更新请求, time={}, oldId={}", time, oldId);

        Map<String, Object> result = new HashMap<>();
        try {
            scheduler.collectOldMonthData(time, oldId);
            result.put("success", true);
            result.put("message", "老ID月数据采集完成");
            result.put("dataTime", time);
            result.put("oldId", oldId);
            result.put("table", "month");
            result.put("isOld", 1);
        } catch (Exception e) {
            log.error("老ID月数据更新失败, time={}, oldId={}: {}", time, oldId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "老ID月数据采集失败: " + e.getMessage());
            result.put("dataTime", time);
            result.put("oldId", oldId);
        }

        return result;
    }

}
