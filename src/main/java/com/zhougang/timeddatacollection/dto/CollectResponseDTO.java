package com.zhougang.timeddatacollection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * 采集API的完整响应体
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CollectResponseDTO {

    private Boolean status;
    private String message;
    private String code;
    private String traceId;
    private Object possibleReason;
    private Object suggestMeasure;
    /**
     * key: ID名称（如表名 xcepma_qc237_ggi_00_00_1）
     * value: 对应的数据项
     */
    private Map<String, DataItemDTO> data;
    private String formatError;
    private Boolean success4CodeZeroDataNotNull;
    private Boolean success4CodeZero;

    // ====== getters & setters ======

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Object getPossibleReason() {
        return possibleReason;
    }

    public void setPossibleReason(Object possibleReason) {
        this.possibleReason = possibleReason;
    }

    public Object getSuggestMeasure() {
        return suggestMeasure;
    }

    public void setSuggestMeasure(Object suggestMeasure) {
        this.suggestMeasure = suggestMeasure;
    }

    public Map<String, DataItemDTO> getData() {
        return data;
    }

    public void setData(Map<String, DataItemDTO> data) {
        this.data = data;
    }

    public String getFormatError() {
        return formatError;
    }

    public void setFormatError(String formatError) {
        this.formatError = formatError;
    }

    public Boolean getSuccess4CodeZeroDataNotNull() {
        return success4CodeZeroDataNotNull;
    }

    public void setSuccess4CodeZeroDataNotNull(Boolean success4CodeZeroDataNotNull) {
        this.success4CodeZeroDataNotNull = success4CodeZeroDataNotNull;
    }

    public Boolean getSuccess4CodeZero() {
        return success4CodeZero;
    }

    public void setSuccess4CodeZero(Boolean success4CodeZero) {
        this.success4CodeZero = success4CodeZero;
    }
}
