package com.zhougang.timeddatacollection.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhougang.timeddatacollection.dto.CokeMaterialItemDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 高炉喷煤查询服务
 * <p>
 * 调用焦炭材料流转历史查询接口（与焦炭材料查询同一接口、同一鉴权），
 * 请求条件为终点位置 BF1_LC_PM1~PM5、计量设备 RA_PDCa_B401 / RA_PDCa_B402，
 * 分别用两组物料编码各查一次，汇总各自范围内所有节点的 transportWeight 之和。
 * <p>
 * 两种物料分别独立入库，互不相加：
 * 高炉高挥发喷煤 matCode=1220000008；
 * 高炉低硫贫瘦喷煤 matCode=1220000015。
 */
@Service
public class BlastFurnacePciQueryService {

    private static final Logger log = LoggerFactory.getLogger(BlastFurnacePciQueryService.class);

    /** 高炉喷煤的终点位置 */
    private static final List<String> END_POS_LIST = Arrays.asList(
            "BF1_LC_PM1", "BF1_LC_PM2", "BF1_LC_PM3", "BF1_LC_PM4", "BF1_LC_PM5");

    /** 高炉高挥发喷煤的物料编码 */
    private static final String MAT_CODE_HIGH_VOLATILE = "1220000008";

    /** 高炉低硫贫瘦喷煤的物料编码 */
    private static final String MAT_CODE_LOW_SULFUR_LEAN = "1220000015";

    /** 计量设备编码 */
    private static final List<String> METERING_EQUIPMENT_CODES =
            Arrays.asList("RA_PDCa_B401", "RA_PDCa_B402");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String serviceId;
    private final String serviceSecret;

    public BlastFurnacePciQueryService(RestTemplate restTemplate,
                                       ObjectMapper objectMapper,
                                       @Value("${cokematerial.api.url}") String apiUrl,
                                       @Value("${cokematerial.api.service-id}") String serviceId,
                                       @Value("${cokematerial.api.service-secret}") String serviceSecret) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiUrl = apiUrl;
        this.serviceId = serviceId;
        this.serviceSecret = serviceSecret;
    }

    /**
     * 查询指定时间范围内的高炉高挥发喷煤合计（matCode=1220000008）
     *
     * @param startTime 开始时间戳（毫秒，13位）
     * @param endTime   结束时间戳（毫秒，13位）
     * @return 所有节点 transportWeight 的合计
     */
    public double queryHighVolatileWeight(long startTime, long endTime) throws JsonProcessingException {
        return queryByMatCode(startTime, endTime, MAT_CODE_HIGH_VOLATILE, "高炉高挥发喷煤");
    }

    /**
     * 查询指定时间范围内的高炉低硫贫瘦喷煤合计（matCode=1220000015）
     *
     * @param startTime 开始时间戳（毫秒，13位）
     * @param endTime   结束时间戳（毫秒，13位）
     * @return 所有节点 transportWeight 的合计
     */
    public double queryLowSulfurLeanWeight(long startTime, long endTime) throws JsonProcessingException {
        return queryByMatCode(startTime, endTime, MAT_CODE_LOW_SULFUR_LEAN, "高炉低硫贫瘦喷煤");
    }

    /**
     * 用指定物料编码查询一次，返回 transportWeight 合计
     *
     * @param matCode   物料编码
     * @param typeLabel 日志用的类型名称
     */
    private double queryByMatCode(long startTime, long endTime, String matCode, String typeLabel)
            throws JsonProcessingException {
        Map<String, Object> requestBody = buildRequestBody(startTime, endTime, matCode);

        String urlTemplate = apiUrl + "?serviceId={serviceId}&serviceSecret={serviceSecret}";
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("serviceId", serviceId);
        uriVariables.put("serviceSecret", serviceSecret);

        log.info("{}查询请求URL: {}", typeLabel, urlTemplate);
        log.info("{}查询参数: matCode={}, startTime={}, endTime={}", typeLabel, matCode, startTime, endTime);

        String responseJson = restTemplate.postForObject(urlTemplate, requestBody, String.class, uriVariables);

        if (responseJson == null || responseJson.trim().isEmpty()) {
            log.warn("{}查询响应为空, startTime={}, endTime={}", typeLabel, startTime, endTime);
            return 0;
        }

        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode dataArray = root.path("data");
        if (!dataArray.isArray()) {
            log.warn("{}查询响应中 data 不是数组, startTime={}, endTime={}", typeLabel, startTime, endTime);
            return 0;
        }

        List<CokeMaterialItemDTO> items = objectMapper.convertValue(
                dataArray, new TypeReference<List<CokeMaterialItemDTO>>() {});

        double totalWeight = items.stream()
                .filter(item -> item.getTransportWeight() != null)
                .mapToDouble(item -> item.getTransportWeight())
                .sum();

        log.info("{}查询完成, matCode={}, startTime={}, endTime={}, 条数={}, transportWeight合计={}",
                typeLabel, matCode, startTime, endTime, items.size(), totalWeight);
        return totalWeight;
    }

    /**
     * 构建请求体：仅 startTime / endTime / matCode 可变，其余为高炉喷煤固定参数
     */
    private Map<String, Object> buildRequestBody(long startTime, long endTime, String matCode) {
        Map<String, Object> body = new HashMap<>();
        body.put("endPosList", END_POS_LIST);
        body.put("endTime", endTime);
        body.put("fullQuery", true);
        body.put("sourceTypeList", Arrays.asList("SubFlow", "Sign", "ExtCar"));
        body.put("startPosList", Collections.emptyList());
        body.put("split", false);
        body.put("matCodeList", Collections.singletonList(matCode));
        body.put("startTime", startTime);
        body.put("statusList", Arrays.asList("Completed", "Start", "End"));
        body.put("limit", 50000);
        body.put("meteringEquipmentCodeList", METERING_EQUIPMENT_CODES);
        body.put("bizCode", "common");
        body.put("strictCheckReset", true);
        return body;
    }

}
