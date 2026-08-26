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
 * 焦炭材料查询服务
 * 调用外部物料流转历史查询接口，汇总指定时间范围内焦炭流转节点的 transportWeight
 */
@Service
public class CokeMaterialQueryService {

    private static final Logger log = LoggerFactory.getLogger(CokeMaterialQueryService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String serviceId;
    private final String serviceSecret;

    public CokeMaterialQueryService(RestTemplate restTemplate,
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
     * 查询指定时间范围内的焦炭材料流转节点，汇总所有节点的 transportWeight 之和
     *
     * @param startTime 开始时间戳（毫秒，13位）
     * @param endTime   结束时间戳（毫秒，13位）
     * @return 所有节点 transportWeight 的合计
     */
    public double queryTotalWeight(long startTime, long endTime) throws JsonProcessingException {
        Map<String, Object> requestBody = buildRequestBody(startTime, endTime);

        String urlTemplate = apiUrl + "?serviceId={serviceId}&serviceSecret={serviceSecret}";
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("serviceId", serviceId);
        uriVariables.put("serviceSecret", serviceSecret);

        log.info("焦炭材料查询请求URL: {}", urlTemplate);
        log.info("焦炭材料查询请求体: startTime={}, endTime={}", startTime, endTime);

        String responseJson = restTemplate.postForObject(urlTemplate, requestBody, String.class, uriVariables);

        if (responseJson == null || responseJson.trim().isEmpty()) {
            log.warn("焦炭材料查询响应为空, startTime={}, endTime={}", startTime, endTime);
            return 0;
        }

        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode dataArray = root.path("data");
        if (!dataArray.isArray()) {
            log.warn("焦炭材料查询响应中 data 不是数组, startTime={}, endTime={}", startTime, endTime);
            return 0;
        }

        List<CokeMaterialItemDTO> items = objectMapper.convertValue(
                dataArray, new TypeReference<List<CokeMaterialItemDTO>>() {});

        double totalWeight = items.stream()
                .filter(item -> item.getTransportWeight() != null)
                .mapToDouble(item -> item.getTransportWeight())
                .sum();

        log.info("焦炭材料查询完成, startTime={}, endTime={}, 条数={}, transportWeight合计={}",
                startTime, endTime, items.size(), totalWeight);
        return totalWeight;
    }

    /**
     * 构建请求体：仅 startTime / endTime 可变，其余为固定参数
     */
    private Map<String, Object> buildRequestBody(long startTime, long endTime) {
        Map<String, Object> body = new HashMap<>();
        body.put("endPosList", Collections.emptyList());
        body.put("endTime", endTime);
        body.put("fullQuery", true);
        body.put("sourceTypeList", Arrays.asList("SubFlow", "Sign", "ExtCar"));
        body.put("startPosList", Arrays.asList("CK1_LC_JC_A1", "CK1_LC_JC_B1"));
        body.put("split", false);
        body.put("matCodeList", Collections.emptyList());
        body.put("startTime", startTime);
        body.put("statusList", Arrays.asList("Completed", "Start", "End"));
        body.put("limit", 50000);
        body.put("meteringEquipmentCodeList", Collections.singletonList("CK1_PDCa_J15"));
        body.put("bizCode", "common");
        body.put("strictCheckReset", true);
        return body;
    }

}
