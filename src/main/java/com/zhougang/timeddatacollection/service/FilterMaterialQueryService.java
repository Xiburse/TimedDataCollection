package com.zhougang.timeddatacollection.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhougang.timeddatacollection.dto.FilterMaterialItemDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 筛选材料查询服务
 * 调用外部物料流转查询接口，返回指定时间范围内筛选结果的 netWgt 合计
 */
@Service
public class FilterMaterialQueryService {

    private static final Logger log = LoggerFactory.getLogger(FilterMaterialQueryService.class);

    /** 与外部接口 queryStarttime / queryEndtime 保持一致的时间格式 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String serviceId;
    private final String serviceSecret;

    public FilterMaterialQueryService(RestTemplate restTemplate,
                                      ObjectMapper objectMapper,
                                      @Value("${filtermaterial.api.url}") String apiUrl,
                                      @Value("${filtermaterial.api.service-id}") String serviceId,
                                      @Value("${filtermaterial.api.service-secret}") String serviceSecret) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.serviceId = serviceId;
        this.serviceSecret = serviceSecret;
    }

    /**
     * 查询指定时间范围内的物料记录，并按材料类型汇总 netWgt：
     * 1. 将 data.data 数组解析为 FilterMaterialItemDTO 集合
     * 2. 筛选 fpWgtDev 为 B401BC 或 B402BC 的记录
     * 3. materialType=1（焦炭）：matName 含"焦炭" → 返回 {焦炭: netWgt合计}
     *    materialType=2（喷煤）：matName 分别含"高挥发"/"低硫" → 返回 {烟煤: 高挥发合计, 无烟煤: 低硫合计}
     *
     * @param startTime    开始时间戳（毫秒）
     * @param endTime      结束时间戳（毫秒）
     * @param materialType 材料类型判断：1=焦炭，2=喷煤
     * @return 存储类型名 → netWgt 合计
     */
    public Map<String, Double> queryNetWeight(long startTime, long endTime, int materialType) throws JsonProcessingException {
        String startTimeStr = formatTime(startTime);
        String endTimeStr = formatTime(endTime);

        // 通过URI变量方式拼接，RestTemplate 会自动对时间字符串里的空格/冒号做URL编码
        String urlTemplate = apiUrl
                + "?serviceId={serviceId}&serviceSecret={serviceSecret}"
                + "&queryStarttime={start}&queryEndtime={end}";

        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("serviceId", serviceId);
        uriVariables.put("serviceSecret", serviceSecret);
        uriVariables.put("start", startTimeStr);
        uriVariables.put("end", endTimeStr);

        log.info("筛选材料查询请求URL: {}", urlTemplate);
        log.info("筛选材料查询参数: serviceId={}, queryStarttime={}, queryEndtime={}",
                serviceId, startTimeStr, endTimeStr);

        String responseJson = restTemplate.getForObject(urlTemplate, String.class, uriVariables);

        if (responseJson == null || responseJson.trim().isEmpty()) {
            log.warn("筛选材料查询响应为空, startTime={}, endTime={}", startTime, endTime);
            return Collections.emptyMap();
        }

        JsonNode root = objectMapper.readTree(responseJson);
        // 第一层 data 下嵌套的 data 数组 → 解析为集合
        JsonNode dataArray = root.path("data").path("data");
        if (!dataArray.isArray()) {
            log.warn("筛选材料查询响应中 data.data 不是数组, startTime={}, endTime={}", startTime, endTime);
            return Collections.emptyMap();
        }

        List<FilterMaterialItemDTO> items = objectMapper.convertValue(
                dataArray, new TypeReference<List<FilterMaterialItemDTO>>() {});

        // 第二个集合：fpWgtDev 为 B401BC 或 B402BC
        List<FilterMaterialItemDTO> fpWgtDevFiltered = items.stream()
                .filter(item -> "B401BC".equals(item.getFpWgtDev()) || "B402BC".equals(item.getFpWgtDev()))
                .collect(Collectors.toList());

        Map<String, Double> result = new LinkedHashMap<>();
        if (materialType == 1) {
            // 焦炭：matName 含"焦炭"
            double cokeNetWgt = sumNetWgtByMatName(fpWgtDevFiltered, "焦炭");
            result.put("焦炭", cokeNetWgt);
            log.info("筛选材料查询完成（焦炭）, startTime={}, endTime={}, netWgt合计={}",
                    startTime, endTime, cokeNetWgt);
        } else {
            // 喷煤：matName 分别按"高挥发"、"低硫"筛选，存储名映射为"烟煤"、"无烟煤"
            double highVolatile = sumNetWgtByMatName(fpWgtDevFiltered, "高挥发");
            double lowSulfur = sumNetWgtByMatName(fpWgtDevFiltered, "低硫");
            result.put("烟煤", highVolatile);
            result.put("无烟煤", lowSulfur);
            log.info("筛选材料查询完成（喷煤）, startTime={}, endTime={}, 高挥发(烟煤)={}, 低硫(无烟煤)={}",
                    startTime, endTime, highVolatile, lowSulfur);
        }
        return result;
    }

    /**
     * 对已筛选出的记录，按 matName 含指定关键词再次筛选，并累加 netWgt
     */
    private double sumNetWgtByMatName(List<FilterMaterialItemDTO> items, String keyword) {
        return items.stream()
                .filter(item -> item.getMatName() != null && item.getMatName().contains(keyword))
                .filter(item -> item.getNetWgt() != null)
                .mapToDouble(item -> item.getNetWgt())
                .sum();
    }

    /**
     * 将毫秒时间戳格式化为 "yyyy-MM-dd HH:mm:ss"
     */
    private String formatTime(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(TIME_FORMATTER);
    }

}
