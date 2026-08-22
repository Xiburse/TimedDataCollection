package com.zhougang.timeddatacollection.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhougang.timeddatacollection.dto.CollectResponseDTO;
import com.zhougang.timeddatacollection.service.DataStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时数据采集调度器
 * 每天0点发送POST请求到指定API，采集数据并存入数据库
 */
@Component
public class DataCollectionScheduler {

    private static final Logger log = LoggerFactory.getLogger(DataCollectionScheduler.class);

    @Value("${timedata.api.url}")
    private String apiUrl;

    @Value("${timedata.time-division}")
    private String timeDivision;

    private final RestTemplate restTemplate;
    private final DataStorageService dataStorageService;
    private final ObjectMapper objectMapper;

    public DataCollectionScheduler(RestTemplate restTemplate,
                                   DataStorageService dataStorageService,
                                   ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.dataStorageService = dataStorageService;
        this.objectMapper = objectMapper;
    }

    /**
     * 应用启动时检查外部 ID 配置文件是否可读取，并在控制台输出前几条 ID 作为预览。
     * 仅做检查与预览输出，读取失败不影响应用启动。
     */
    @PostConstruct
    public void checkIdsConfig() {
        log.info("========== 启动时检查 ID 配置文件 ==========");
        File externalFile = new File("ids.properties");
        if (externalFile.isFile()) {
            log.info("检测到外部配置文件: {}", externalFile.getAbsolutePath());
        } else {
            log.warn("未检测到外部配置文件，当前工作目录: {}，将回退使用 jar 内置 ids.properties",
                    new File(".").getAbsolutePath());
        }
        try {
            List<String> ids = loadIds();
            log.info("ID 配置加载成功，共 {} 个ID", ids.size());
        } catch (Exception e) {
            log.error("ID 配置加载失败: {}", e.getMessage(), e);
        }
        log.info("========== ID 配置文件检查结束 ==========");
    }

    /**
     * 每天凌晨自动执行：采集前一天的日数据
     */
    @Scheduled(cron = "0 30 0 * * ?")
    public void scheduledCollect() {
        long yesterdayZeroTimestamp = LocalDate.now()
                .minusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        collectData(yesterdayZeroTimestamp);
    }

    /**
     * 每月1号凌晨自动执行：采集上个月的月数据
     */
    @Scheduled(cron = "0 30 0 1 * ?")
    public void scheduledMonthlyCollect() {
        // 上个月1号0点的时间戳
        long lastMonthFirstTimestamp = LocalDate.now()
                .minusMonths(1)
                .withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();

        log.info("========== 月数据采集触发, lastMonthFirst={} ==========", lastMonthFirstTimestamp);
        doCollect(lastMonthFirstTimestamp, "time_division,0,M", "month", null, 0);
    }

    /**
     * 执行一次日数据采集（供定时任务、Controller和 --update 命令行调用）
     *
     * @param dataTime 查询时间戳（毫秒）
     */
    public void collectData(long dataTime) {
        doCollect(dataTime, timeDivision, "day", null, 0);
    }

    /**
     * 执行一次月数据采集（供 /update/timemonth 接口调用）
     *
     * @param dataTime 月份最早一天的时间戳（毫秒）
     */
    public void collectMonthData(long dataTime) {
        doCollect(dataTime, "time_division,0,M", "month", null, 0);
    }

    /**
     * 执行一次老ID日数据采集（供老ID接口调用）
     *
     * @param dataTime 查询时间戳（毫秒）
     * @param oldId    老ID，仅采集该ID的数据，存储时 isOld=1
     */
    public void collectOldData(long dataTime, String oldId) {
        doCollect(dataTime, timeDivision, "day", Collections.singletonList(oldId), 1);
    }

    /**
     * 执行一次老ID月数据采集（供老ID接口调用）
     *
     * @param dataTime 月份最早一天的时间戳（毫秒）
     * @param oldId    老ID，仅采集该ID的数据，存储时 isOld=1
     */
    public void collectOldMonthData(long dataTime, String oldId) {
        doCollect(dataTime, "time_division,0,M", "month", Collections.singletonList(oldId), 1);
    }

    /**
     * 通用数据采集逻辑
     *
     * @param dataTime     查询时间戳（毫秒）
     * @param timeDivision 时间维度类型（time_division,0,D 或 time_division,0,M）
     * @param tableName    目标表名（"day" 或 "month"）
     * @param ids          要采集的ID列表，传 null 表示从配置文件读取全部ID
     * @param isOld        是否为老数据（0=否，1=是）
     */
    private void doCollect(long dataTime, String timeDivision, String tableName, List<String> ids, int isOld) {
        log.info("========== 数据采集开始, dataTime={}, timeDivision={}, tableName={}, isOld={} ==========",
                dataTime, timeDivision, tableName, isOld);
        try {
            // 1. 确定ids列表：未显式传入时，从配置文件读取
            if (ids == null) {
                ids = loadIds();
            }

            // 2. 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("timeDivision", timeDivision);
            requestBody.put("dataTime", dataTime);
            requestBody.put("ids", ids);

            log.info("请求URL: {}", apiUrl);
            log.info("请求体: {}", requestBody);

            // 3. 发送POST请求
            String responseJson = restTemplate.postForObject(apiUrl, requestBody, String.class);
            log.info("接收到响应（前500字符）: {}",
                    responseJson != null && responseJson.length() > 500
                            ? responseJson.substring(0, 500) + "..."
                            : responseJson);

            // 4. 解析响应
            CollectResponseDTO responseDTO = objectMapper.readValue(responseJson, CollectResponseDTO.class);

            if (responseDTO.getData() == null || responseDTO.getData().isEmpty()) {
                log.warn("响应中data字段为空，跳过存储");
                return;
            }

            // 5. 存储数据到数据库（day表 或 month表），带 isOld 标记
            dataStorageService.storeData(dataTime, responseDTO.getData(), tableName, isOld);

            log.info("========== 数据采集完成, dataTime={} ==========", dataTime);

        } catch (Exception e) {
            log.error("数据采集失败, dataTime={}: {}", dataTime, e.getMessage(), e);
        }
    }

    /**
     * 从ids.properties文件加载ID列表
     * 每行一个ID，忽略空行和以#开头的注释行
     * <p>
     * 优先读取 jar 同目录下的外部 ids.properties（便于不改包直接调整ID），
     * 若不存在则回退到 classpath 内置的 ids.properties。
     */
    private List<String> loadIds() throws Exception {
        List<String> ids = new ArrayList<>();

        // 外部文件：jar 所在目录（即运行时的当前工作目录）下的 ids.properties
        File externalFile = new File("ids.properties");
        InputStream inputStream;
        String source;
        if (externalFile.isFile()) {
            inputStream = new FileInputStream(externalFile);
            source = "外部文件 " + externalFile.getAbsolutePath();
        } else {
            inputStream = new ClassPathResource("ids.properties").getInputStream();
            source = "classpath 内置 ids.properties";
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // 跳过空行和注释行
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                ids.add(line);
            }
        }

        log.info("从 {} 加载了 {} 个ID", source, ids.size());
        return ids;
    }

}
