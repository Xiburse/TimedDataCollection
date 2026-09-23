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
 * 烧结焦炭查询服务
 * <p>
 * 调用焦炭材料流转历史查询接口（与焦炭材料查询同一接口、同一鉴权），
 * 请求条件为 endPos=ST1_LC_RP、计量设备 RA_PDCa_B401 / RA_PDCa_B402，
 * 汇总指定时间范围内所有节点的 transportWeight 之和，作为该时间段的烧结焦炭数据。
 */
@Service
public class SinteredCokeQueryService {

    private static final Logger log = LoggerFactory.getLogger(SinteredCokeQueryService.class);

    /** 烧结焦炭的终点位置 */
    private static final String END_POS = "ST1_LC_RP";

    /** 计量设备编码 */
    private static final List<String> METERING_EQUIPMENT_CODES =
            Arrays.asList("RA_PDCa_B401", "RA_PDCa_B402");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String serviceId;
    private final String serviceSecret;

    public SinteredCokeQueryService(RestTemplate restTemplate,
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
     * 查询指定时间范围内的烧结焦炭合计
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

        log.info("烧结焦炭查询请求URL: {}", urlTemplate);
        log.info("烧结焦炭查询时间范围: startTime={}, endTime={}", startTime, endTime);

        String responseJson = restTemplate.postForObject(urlTemplate, requestBody, String.class, uriVariables);

        if (responseJson == null || responseJson.trim().isEmpty()) {
            log.warn("烧结焦炭查询响应为空, startTime={}, endTime={}", startTime, endTime);
            return 0;
        }

        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode dataArray = root.path("data");
        if (!dataArray.isArray()) {
            log.warn("烧结焦炭查询响应中 data 不是数组, startTime={}, endTime={}", startTime, endTime);
            return 0;
        }

        List<CokeMaterialItemDTO> items = objectMapper.convertValue(
                dataArray, new TypeReference<List<CokeMaterialItemDTO>>() {});

        double totalWeight = items.stream()
                .filter(item -> item.getTransportWeight() != null)
                .mapToDouble(item -> item.getTransportWeight())
                .sum();

        log.info("烧结焦炭查询完成, startTime={}, endTime={}, 条数={}, transportWeight合计={}",
                startTime, endTime, items.size(), totalWeight);
        return totalWeight;
    }

    /**
     * 构建请求体：仅 startTime / endTime 可变，其余为烧结焦炭固定参数
     */
    private Map<String, Object> buildRequestBody(long startTime, long endTime) {
        Map<String, Object> body = new HashMap<>();
        body.put("endPosList", Collections.singletonList(END_POS));
        body.put("endTime", endTime);
        body.put("fullQuery", true);
        body.put("sourceTypeList", Arrays.asList("SubFlow", "Sign", "ExtCar"));
        body.put("startPosList", Collections.emptyList());
        body.put("split", false);
        body.put("matCodeList", Collections.emptyList());
        body.put("startTime", startTime);
        body.put("statusList", Arrays.asList("Completed", "Start", "End"));
        body.put("limit", 50000);
        body.put("meteringEquipmentCodeList", METERING_EQUIPMENT_CODES);
        body.put("bizCode", "common");
        body.put("strictCheckReset", true);
        return body;
    }

}
