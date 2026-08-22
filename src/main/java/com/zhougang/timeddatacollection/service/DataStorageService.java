package com.zhougang.timeddatacollection.service;

import com.zhougang.timeddatacollection.dto.DataItemDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 数据存储服务
 * <p>
 * 所有ID的日数据存入一张 day 表，月数据存入一张 month 表。
 * 主键为 (selectTime, dataId) 组合，防止同一ID在同一时间戳下重复插入。
 */
@Service
public class DataStorageService {

    private static final Logger log = LoggerFactory.getLogger(DataStorageService.class);

    private final JdbcTemplate jdbcTemplate;
    private final String quoteChar;

    public DataStorageService(JdbcTemplate jdbcTemplate,
                              @Value("${timedata.db.quote-char:`}") String quoteChar) {
        this.jdbcTemplate = jdbcTemplate;
        this.quoteChar = quoteChar;
    }

    /**
     * 存储采集到的数据
     *
     * @param selectTime 本次查询的时间戳（毫秒）
     * @param dataMap    响应中的 data 字段 Map<ID名, DataItemDTO>
     * @param tableName  目标表名（"day" 或 "month"）
     * @param isOld      是否为老数据（0=否，1=是）
     */
    public void storeData(long selectTime, Map<String, DataItemDTO> dataMap, String tableName, int isOld) {
        if (dataMap == null || dataMap.isEmpty()) {
            log.warn("响应数据为空，跳过存储");
            return;
        }

        log.info("开始存储数据到表[{}], 共 {} 个ID, selectTime={}, isOld={}",
                tableName, dataMap.size(), selectTime, isOld);

        // 确保表存在
        createTableIfNotExists(tableName);

        int inserted = 0;
        int skipped = 0;

        for (Map.Entry<String, DataItemDTO> entry : dataMap.entrySet()) {
            String dataId = entry.getKey();      // 如 xcepma_01_zcpcl_00_00_24
            DataItemDTO item = entry.getValue();

            try {
                if (insertIfNotExists(tableName, selectTime, dataId, item, isOld)) {
                    inserted++;
                } else {
                    skipped++;
                    log.info("表[{}]中 selectTime={}, dataId={} 已存在，跳过插入", tableName, selectTime, dataId);
                }
            } catch (Exception e) {
                log.error("存储 dataId={} 失败: {}", dataId, e.getMessage(), e);
            }
        }

        log.info("数据存储完成 -> 表[{}]: 插入={}, 跳过={}", tableName, inserted, skipped);
    }

    /**
     * 创建表（如不存在）
     */
    private void createTableIfNotExists(String tableName) {
        String q = quoteChar;
        String sql = "CREATE TABLE IF NOT EXISTS " + q + tableName + q + " ("
                + q + "selectTime" + q + "      BIGINT,"
                + q + "readableTime" + q + "    VARCHAR(255),"
                + q + "dataId" + q + "          VARCHAR(255),"
                + q + "id" + q + "              VARCHAR(255),"
                + q + "valueBigDecimal" + q + " DECIMAL(30,12),"
                + q + "value" + q + "           VARCHAR(255),"
                + q + "valueTime" + q + "       VARCHAR(255),"
                + q + "valueTimeDate" + q + "   VARCHAR(255),"
                + q + "valueUpdateTime" + q + " VARCHAR(255),"
                + q + "dataQuality" + q + "     VARCHAR(50),"
                + q + "timeDivision" + q + "    VARCHAR(255),"
                + q + "isOld" + q + "          TINYINT DEFAULT 0,"
                + "PRIMARY KEY (" + q + "selectTime" + q + ", " + q + "dataId" + q + ")"
                + ")";

        jdbcTemplate.execute(sql);
        log.debug("表[{}]已就绪", tableName);
    }

    /**
     * 插入数据（如不存在则插入）
     *
     * @param isOld 是否为老数据（0=否，1=是）
     * @return true=插入了新数据, false=已存在跳过
     */
    private boolean insertIfNotExists(String tableName, long selectTime, String dataId, DataItemDTO item, int isOld) {
        String q = quoteChar;

        // 检查该 (selectTime, dataId) 组合是否已存在
        String checkSql = "SELECT COUNT(*) FROM " + q + tableName + q
                + " WHERE " + q + "selectTime" + q + " = ? AND " + q + "dataId" + q + " = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, selectTime, dataId);

        if (count != null && count > 0) {
            return false;
        }

        // 计算可读时间
        String readableTime = Instant.ofEpochMilli(selectTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 插入
        String insertSql = "INSERT INTO " + q + tableName + q + " ("
                + q + "selectTime" + q + ","
                + q + "readableTime" + q + ","
                + q + "dataId" + q + ","
                + q + "id" + q + ","
                + q + "valueBigDecimal" + q + ","
                + q + "value" + q + ","
                + q + "valueTime" + q + ","
                + q + "valueTimeDate" + q + ","
                + q + "valueUpdateTime" + q + ","
                + q + "dataQuality" + q + ","
                + q + "timeDivision" + q + ","
                + q + "isOld" + q
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

        jdbcTemplate.update(insertSql,
                selectTime,
                readableTime,
                dataId,
                item.getId(),
                item.getValueBigDecimal(),
                item.getValue(),
                item.getValueTime(),
                item.getValueTimeDate(),
                item.getValueUpdateTime(),
                item.getDataQuality(),
                item.getTimeDivision(),
                isOld
        );

        log.debug("表[{}]插入成功, selectTime={}, dataId={}, isOld={}", tableName, selectTime, dataId, isOld);
        return true;
    }

}
