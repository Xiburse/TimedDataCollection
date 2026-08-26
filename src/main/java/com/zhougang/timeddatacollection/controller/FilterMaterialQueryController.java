package com.zhougang.timeddatacollection.controller;

import com.zhougang.timeddatacollection.service.FilterMaterialQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 筛选材料查询控制器
 * 根据开始/结束时间戳查询筛选出的材料，返回 netWgt 合计
 */
@RestController
@RequestMapping("/filtermaterial")
public class FilterMaterialQueryController {

    private static final Logger log = LoggerFactory.getLogger(FilterMaterialQueryController.class);

    private final FilterMaterialQueryService filterMaterialQueryService;

    public FilterMaterialQueryController(FilterMaterialQueryService filterMaterialQueryService) {
        this.filterMaterialQueryService = filterMaterialQueryService;
    }

    /**
     * 查询指定时间范围内筛选出的材料，返回最终筛选结果 netWgt 的合计
     *
     * @param startTime    开始时间戳（毫秒，13位）
     * @param endTime      结束时间戳（毫秒，13位）
     * @param materialType 材料类型判断：1=焦炭，2=喷煤
     * @return 最终筛选结果中各元素 netWgt 的合计
     */
    @GetMapping("/netWeight")
    public Map<String, Object> netWeight(@RequestParam("startTime") long startTime,
                                         @RequestParam("endTime") long endTime,
                                         @RequestParam("materialType") int materialType) {
        log.info("收到筛选材料查询请求, startTime={}, endTime={}, materialType={}",
                startTime, endTime, materialType);

        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Double> netWeight = filterMaterialQueryService.queryNetWeight(startTime, endTime, materialType);
            result.put("success", true);
            result.put("message", "查询成功");
            result.put("netWeight", netWeight);
            result.put("startTime", startTime);
            result.put("endTime", endTime);
            result.put("materialType", materialType);
        } catch (Exception e) {
            log.error("筛选材料查询失败, startTime={}, endTime={}, materialType={}: {}",
                    startTime, endTime, materialType, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "查询失败: " + e.getMessage());
            result.put("startTime", startTime);
            result.put("endTime", endTime);
            result.put("materialType", materialType);
        }

        return result;
    }

}
