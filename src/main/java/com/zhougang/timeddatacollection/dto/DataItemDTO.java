package com.zhougang.timeddatacollection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * 响应数据中的单个数据项
 * 对应JSON中 data 字段下每个ID对应的对象
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataItemDTO {

    private String id;
    private BigDecimal valueBigDecimal;
    private String value;
    private String valueTime;
    private String valueTimeDate;
    private String valueUpdateTime;
    private String dataQuality;
    private String timeDivision;

    // ====== getters & setters ======

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BigDecimal getValueBigDecimal() {
        return valueBigDecimal;
    }

    public void setValueBigDecimal(BigDecimal valueBigDecimal) {
        this.valueBigDecimal = valueBigDecimal;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getValueTime() {
        return valueTime;
    }

    public void setValueTime(String valueTime) {
        this.valueTime = valueTime;
    }

    public String getValueTimeDate() {
        return valueTimeDate;
    }

    public void setValueTimeDate(String valueTimeDate) {
        this.valueTimeDate = valueTimeDate;
    }

    public String getValueUpdateTime() {
        return valueUpdateTime;
    }

    public void setValueUpdateTime(String valueUpdateTime) {
        this.valueUpdateTime = valueUpdateTime;
    }

    public String getDataQuality() {
        return dataQuality;
    }

    public void setDataQuality(String dataQuality) {
        this.dataQuality = dataQuality;
    }

    public String getTimeDivision() {
        return timeDivision;
    }

    public void setTimeDivision(String timeDivision) {
        this.timeDivision = timeDivision;
    }
}
