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
 * 炼铁焦炭查询服务
 * <p>
 * 调用焦炭材料流转历史查询接口（与焦炭材料查询同一接口、同一鉴权），
 * 需要分别用两组 matCodeList 各发一次请求，再把两次结果的 transportWeight 全部相加，
 * 总和即为该时间段的炼铁焦炭数据。
 * <p>
 * 两组物料编码：
 * 一组 SDZJ202601130001 / SDZJ202601130002，另一组 5310001223。
 */
@Service
public class IronCokeQueryService {

    private static final Logger log = LoggerFactory.getLogger(IronCokeQueryService.class);

    /** 炼铁焦炭第一组物料编码 */
    private static final List<String> MAT_CODES_GROUP_1 =
            Arrays.asList("SDZJ202601130001", "SDZJ202601130002");

    /** 炼铁焦炭第二组物料编码 */
    private static final List<String> MAT_CODES_GROUP_2 =
            Collections.singletonList("5310001223");

    /** 计量设备编码 */
    private static final List<String> METERING_EQUIPMENT_CODES =
            Arrays.asList("RA_PDCa_B401", "RA_PDCa_B402");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String serviceId;
    private final String serviceSecret;

    public IronCokeQueryService(RestTemplate restTemplate,
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
     * 查询指定时间范围内的炼铁焦炭合计
     * <p>
     * 两组 matCodeList 各查一次，两次 transportWeight 合计相加。
     * 任意一次查询失败都会抛出异常，由调用方决定如何处理，避免写入不完整的合计值。
     *
     * @param startTime 开始时间戳（毫秒，13位）
     * @param endTime   结束时间戳（毫秒，13位）
     * @return 两次查询 transportWeight 合计之和
     */
    public double queryTotalWeight(long startTime, long endTime) throws JsonProcessingException {
        double group1 = queryByMatCodeList(startTime, endTime, MAT_CODES_GROUP_1);
        double group2 = queryByMatCodeList(startTime, endTime, MAT_CODES_GROUP_2);

        double total = group1 + group2;
        log.info("炼铁焦炭查询完成(两组合计), startTime={}, endTime={}, 第一组={}, 第二组={}, 总计={}",
                startTime, endTime, group1, group2, total);
        return total;
    }

    /**
     * 用指定 matCodeList 查询一次，返回 transportWeight 合计
     */
    private double queryByMatCodeList(long startTime, long endTime, List<String> matCodeList)
            throws JsonProcessingException {
        Map<String, Object> requestBody = buildRequestBody(startTime, endTime, matCodeList);

        String urlTemplate = apiUrl + "?serviceId={serviceId}&serviceSecret={serviceSecret}";
        Map<String, String> uriVariables = new HashMap<>();
        uriVariables.put("serviceId", serviceId);
        uriVariables.put("serviceSecret", serviceSecret);

        log.info("炼铁焦炭查询请求URL: {}", urlTemplate);
        log.info("炼铁焦炭查询参数: matCodeList={}, startTime={}, endTime={}",
                matCodeList, startTime, endTime);

        String responseJson = restTemplate.postForObject(urlTemplate, requestBody, String.class, uriVariables);

        if (responseJson == null || responseJson.trim().isEmpty()) {
            log.warn("炼铁焦炭查询响应为空, matCodeList={}, startTime={}, endTime={}",
                    matCodeList, startTime, endTime);
            return 0;
        }

        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode dataArray = root.path("data");
        if (!dataArray.isArray()) {
            log.warn("炼铁焦炭查询响应中 data 不是数组, matCodeList={}, startTime={}, endTime={}",
                    matCodeList, startTime, endTime);
            return 0;
        }

        List<CokeMaterialItemDTO> items = objectMapper.convertValue(
                dataArray, new TypeReference<List<CokeMaterialItemDTO>>() {});

        double totalWeight = items.stream()
                .filter(item -> item.getTransportWeight() != null)
                .mapToDouble(item -> item.getTransportWeight())
                .sum();

        log.info("炼铁焦炭单组查询完成, matCodeList={}, 条数={}, transportWeight合计={}",
                matCodeList, items.size(), totalWeight);
        return totalWeight;
    }

    /**
     * 构建请求体：仅 startTime / endTime / matCodeList 可变，其余为炼铁焦炭固定参数
     */
    private Map<String, Object> buildRequestBody(long startTime, long endTime, List<String> matCodeList) {
        Map<String, Object> body = new HashMap<>();
        body.put("endPosList", Collections.emptyList());
        body.put("endTime", endTime);
        body.put("fullQuery", true);
        body.put("sourceTypeList", Arrays.asList("SubFlow", "Sign", "ExtCar"));
        body.put("startPosList", Collections.emptyList());
        body.put("split", false);
        body.put("matCodeList", matCodeList);
        body.put("startTime", startTime);
        body.put("statusList", Arrays.asList("Completed", "Start", "End"));
        body.put("limit", 50000);
        body.put("meteringEquipmentCodeList", METERING_EQUIPMENT_CODES);
        body.put("bizCode", "common");
        body.put("strictCheckReset", true);
        return body;
    }

}
