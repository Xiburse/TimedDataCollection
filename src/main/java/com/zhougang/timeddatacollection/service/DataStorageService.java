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
import java.util.List;
import java.util.Map;

/**
 * 数据存储服务
 * <p>
 * 所有ID的日数据存入一张 day 表，月数据存入一张 month 表。
 * 主键为 (selectTime, dataId) 组合；同一主键再次采集时直接覆盖原有数据。
 * <p>
 * 覆盖时 isOld 标记取"已有值"与"本次值"的较大者：
 * 只要该记录曾被标记为老数据（isOld=1），后续普通采集不会把这个标记抹掉。
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
     * 存储采集到的数据（已存在则覆盖）
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
        int updated = 0;

        for (Map.Entry<String, DataItemDTO> entry : dataMap.entrySet()) {
            String dataId = entry.getKey();      // 如 xcepma_01_zcpcl_00_00_24
            DataItemDTO item = entry.getValue();

            try {
                if (upsert(tableName, selectTime, dataId, item, isOld)) {
                    inserted++;
                } else {
                    updated++;
                }
            } catch (Exception e) {
                log.error("存储 dataId={} 失败: {}", dataId, e.getMessage(), e);
            }
        }

        log.info("数据存储完成 -> 表[{}]: 插入={}, 覆盖={}", tableName, inserted, updated);
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
     * 写入数据：不存在则插入，已存在则用新采集的值覆盖
     * <p>
     * 覆盖时 isOld 取"已有值"与"本次值"的较大者，保证老ID标记不会被普通采集重置为 0。
     *
     * @param isOld 是否为老数据（0=否，1=是）
     * @return true=新插入, false=覆盖已有记录
     */
    private boolean upsert(String tableName, long selectTime, String dataId, DataItemDTO item, int isOld) {
        String q = quoteChar;

        // 查询该 (selectTime, dataId) 是否已存在，并取出已有的 isOld 标记
        String checkSql = "SELECT " + q + "isOld" + q + " FROM " + q + tableName + q
                + " WHERE " + q + "selectTime" + q + " = ? AND " + q + "dataId" + q + " = ?";
        List<Integer> existingIsOldList = jdbcTemplate.queryForList(checkSql, Integer.class, selectTime, dataId);

        // 老ID标记只增不减：已有 isOld=1 的记录，不会被本次 isOld=0 的采集抹掉
        int finalIsOld = isOld;
        if (!existingIsOldList.isEmpty() && existingIsOldList.get(0) != null) {
            finalIsOld = Math.max(existingIsOldList.get(0), isOld);
        }

        // 计算可读时间
        String readableTime = Instant.ofEpochMilli(selectTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        if (existingIsOldList.isEmpty()) {
            // 不存在 -> 插入
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
                    finalIsOld
            );

            log.debug("表[{}]插入成功, selectTime={}, dataId={}, isOld={}", tableName, selectTime, dataId, finalIsOld);
            return true;
        }

        // 已存在 -> 覆盖全部数据字段
        String updateSql = "UPDATE " + q + tableName + q + " SET "
                + q + "readableTime" + q + " = ?,"
                + q + "id" + q + " = ?,"
                + q + "valueBigDecimal" + q + " = ?,"
                + q + "value" + q + " = ?,"
                + q + "valueTime" + q + " = ?,"
                + q + "valueTimeDate" + q + " = ?,"
                + q + "valueUpdateTime" + q + " = ?,"
                + q + "dataQuality" + q + " = ?,"
                + q + "timeDivision" + q + " = ?,"
                + q + "isOld" + q + " = ? "
                + "WHERE " + q + "selectTime" + q + " = ? AND " + q + "dataId" + q + " = ?";

        jdbcTemplate.update(updateSql,
                readableTime,
                item.getId(),
                item.getValueBigDecimal(),
                item.getValue(),
                item.getValueTime(),
                item.getValueTimeDate(),
                item.getValueUpdateTime(),
                item.getDataQuality(),
                item.getTimeDivision(),
                finalIsOld,
                selectTime,
                dataId
        );

        log.debug("表[{}]覆盖成功, selectTime={}, dataId={}, isOld={}", tableName, selectTime, dataId, finalIsOld);
        return false;
    }

}
