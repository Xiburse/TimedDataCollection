package com.zhougang.timeddatacollection.controller;

import com.zhougang.timeddatacollection.scheduler.DayDataSumScheduler;
import com.zhougang.timeddatacollection.scheduler.MonthDataSumScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * jm 日/月数据手动采集控制器
 * 通过HTTP接口触发指定日期（或月份）的焦炭和喷煤数据采集并入库
 */
@RestController
@RequestMapping("/jm")
public class JmDataController {

    private static final Logger log = LoggerFactory.getLogger(JmDataController.class);

    private final DayDataSumScheduler dayDataSumScheduler;
    private final MonthDataSumScheduler monthDataSumScheduler;

    public JmDataController(DayDataSumScheduler dayDataSumScheduler,
                            MonthDataSumScheduler monthDataSumScheduler) {
        this.dayDataSumScheduler = dayDataSumScheduler;
        this.monthDataSumScheduler = monthDataSumScheduler;
    }

    /**
     * 日数据采集：根据指定时间戳采集该天的焦炭和喷煤数据，存入 jm_day 表
     *
     * @param time 指定日期当天0点0分0秒的时间戳（毫秒）
     * @return 操作结果
     */
    @GetMapping("/day/{time}")
    public Map<String, Object> collectDay(@PathVariable("time") long time) {
        log.info("收到日数据采集请求, time={}", time);

        Map<String, Object> result = new HashMap<>();
        try {
            dayDataSumScheduler.collectDaySum(time);
            result.put("success", true);
            result.put("message", "日数据采集完成");
            result.put("time", time);
            result.put("table", "jm_day");
        } catch (Exception e) {
            log.error("日数据采集失败, time={}: {}", time, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "日数据采集失败: " + e.getMessage());
            result.put("time", time);
        }

        return result;
    }

    /**
     * 月数据采集：根据指定时间戳采集该月的焦炭和喷煤数据，存入 jm_month 表
     *
     * @param time 指定月份1号0点0分0秒的时间戳（毫秒）
     * @return 操作结果
     */
    @GetMapping("/month/{time}")
    public Map<String, Object> collectMonth(@PathVariable("time") long time) {
        log.info("收到月数据采集请求, time={}", time);

        Map<String, Object> result = new HashMap<>();
        try {
            monthDataSumScheduler.collectMonthSum(time);
            result.put("success", true);
            result.put("message", "月数据采集完成");
            result.put("time", time);
            result.put("table", "jm_month");
        } catch (Exception e) {
            log.error("月数据采集失败, time={}: {}", time, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "月数据采集失败: " + e.getMessage());
            result.put("time", time);
        }

        return result;
    }

}
