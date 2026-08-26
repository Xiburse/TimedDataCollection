package com.zhougang.timeddatacollection.controller;

import com.zhougang.timeddatacollection.service.CokeMaterialQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 焦炭材料查询控制器
 * 根据开始/结束时间戳查询焦炭材料流转节点，返回 transportWeight 合计
 */
@RestController
@RequestMapping("/cokematerial")
public class CokeMaterialQueryController {

    private static final Logger log = LoggerFactory.getLogger(CokeMaterialQueryController.class);

    private final CokeMaterialQueryService cokeMaterialQueryService;

    public CokeMaterialQueryController(CokeMaterialQueryService cokeMaterialQueryService) {
        this.cokeMaterialQueryService = cokeMaterialQueryService;
    }

    /**
     * 查询指定时间范围内的焦炭材料流转节点，返回 transportWeight 合计
     *
     * @param startTime 开始时间戳（毫秒，13位）
     * @param endTime   结束时间戳（毫秒，13位）
     * @return 所有节点 transportWeight 的合计
     */
    @GetMapping("/totalWeight")
    public Map<String, Object> totalWeight(@RequestParam("startTime") long startTime,
                                           @RequestParam("endTime") long endTime) {
        log.info("收到焦炭材料查询请求, startTime={}, endTime={}", startTime, endTime);

        Map<String, Object> result = new HashMap<>();
        try {
            double totalWeight = cokeMaterialQueryService.queryTotalWeight(startTime, endTime);
            result.put("success", true);
            result.put("message", "查询成功");
            result.put("totalWeight", totalWeight);
            result.put("startTime", startTime);
            result.put("endTime", endTime);
        } catch (Exception e) {
            log.error("焦炭材料查询失败, startTime={}, endTime={}: {}", startTime, endTime, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
            result.put("startTime", startTime);
            result.put("endTime", endTime);
        }

        return result;
    }

}
