package com.zhougang.timeddatacollection.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * jm 日/月数据存储服务
 * <p>
 * 日数据存 jm_day 表，月数据存 jm_month 表。
 * 两表结构一致：type（材料类型）、total（数据之和）、time（数据日期毫秒时间戳）、readableTime（可读时间），time + type 为联合主键
 */
@Service
public class JmStorageService {

    private static final Logger log = LoggerFactory.getLogger(JmStorageService.class);

    private static final String DAY_TABLE = "jm_day";
    private static final String MONTH_TABLE = "jm_month";

    /** 可读时间格式，与 time（毫秒时间戳）对应 */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final String quoteChar;

    public JmStorageService(JdbcTemplate jdbcTemplate,
                            @Value("${timedata.db.quote-char:`}") String quoteChar) {
        this.jdbcTemplate = jdbcTemplate;
        this.quoteChar = quoteChar;
    }

    /**
     * 存储日数据到 jm_day 表
     *
     * @param type  材料类型（焦炭 / 喷煤）
     * @param total 采集到的数据之和
     * @param time  数据日期（当天0点0分0秒的时间戳，毫秒）
     */
    public void storeDay(String type, double total, long time) {
        store(DAY_TABLE, type, total, time);
    }

    /**
     * 存储月数据到 jm_month 表
     *
     * @param type  材料类型（焦炭 / 喷煤）
     * @param total 采集到的数据之和
     * @param time  数据月份（当月1号0点0分0秒的时间戳，毫秒）
     */
    public void storeMonth(String type, double total, long time) {
        store(MONTH_TABLE, type, total, time);
    }

    /**
     * 存储数据（time + type 已存在则更新）
     */
    private void store(String tableName, String type, double total, long time) {
        createTableIfNotExists(tableName);

        String readableTime = Instant.ofEpochMilli(time)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(TIME_FORMATTER);

        String q = quoteChar;
        String checkSql = "SELECT COUNT(*) FROM " + q + tableName + q
                + " WHERE " + q + "time" + q + " = ? AND " + q + "type" + q + " = ?";
        Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, time, type);

        if (count != null && count > 0) {
            String updateSql = "UPDATE " + q + tableName + q + " SET "
                    + q + "total" + q + " = ?, " + q + "readableTime" + q + " = ? "
                    + "WHERE " + q + "time" + q + " = ? AND " + q + "type" + q + " = ?";
            jdbcTemplate.update(updateSql, total, readableTime, time, type);
            log.info("{} 已存在 time={}, type={}, 更新 total={}", tableName, time, type, total);
        } else {
            String insertSql = "INSERT INTO " + q + tableName + q + " ("
                    + q + "type" + q + ", " + q + "total" + q + ", " + q + "time" + q + ", "
                    + q + "readableTime" + q + ") VALUES (?,?,?,?)";
            jdbcTemplate.update(insertSql, type, total, time, readableTime);
            log.info("{} 插入成功, time={}, type={}, total={}", tableName, time, type, total);
        }
    }

    /**
     * 创建表（如不存在）
     */
    private void createTableIfNotExists(String tableName) {
        String q = quoteChar;
        String sql = "CREATE TABLE IF NOT EXISTS " + q + tableName + q + " ("
                + q + "type" + q + "  VARCHAR(50),"
                + q + "total" + q + " DOUBLE,"
                + q + "time" + q + "  BIGINT,"
                + q + "readableTime" + q + " VARCHAR(255),"
                + "PRIMARY KEY (" + q + "time" + q + ", " + q + "type" + q + ")"
                + ")";

        jdbcTemplate.execute(sql);
        log.debug("{} 表已就绪", tableName);
    }

}
