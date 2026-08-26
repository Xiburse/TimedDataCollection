package com.zhougang.timeddatacollection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 筛选材料查询返回的单条物料流转记录
 * 对应响应中 data.data 数组里的每个元素
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FilterMaterialItemDTO {

    private String strtStackBin;
    private String finPosition;
    private String fpName;
    private String endtime;
    private Integer runtime;
    private String starttime;
    private String finStackBin;
    private String strtType;
    private Double curWgt;
    private String fpWgtDev;
    private Double strtWgt;
    private Long id;
    private String fpNo;
    private String finType;
    private String strtPosition;
    private String matCode;
    private Double netWgt;
    private String matName;

    // ====== getters & setters ======

    public String getStrtStackBin() {
        return strtStackBin;
    }

    public void setStrtStackBin(String strtStackBin) {
        this.strtStackBin = strtStackBin;
    }

    public String getFinPosition() {
        return finPosition;
    }

    public void setFinPosition(String finPosition) {
        this.finPosition = finPosition;
    }

    public String getFpName() {
        return fpName;
    }

    public void setFpName(String fpName) {
        this.fpName = fpName;
    }

    public String getEndtime() {
        return endtime;
    }

    public void setEndtime(String endtime) {
        this.endtime = endtime;
    }

    public Integer getRuntime() {
        return runtime;
    }

    public void setRuntime(Integer runtime) {
        this.runtime = runtime;
    }

    public String getStarttime() {
        return starttime;
    }

    public void setStarttime(String starttime) {
        this.starttime = starttime;
    }

    public String getFinStackBin() {
        return finStackBin;
    }

    public void setFinStackBin(String finStackBin) {
        this.finStackBin = finStackBin;
    }

    public String getStrtType() {
        return strtType;
    }

    public void setStrtType(String strtType) {
        this.strtType = strtType;
    }

    public Double getCurWgt() {
        return curWgt;
    }

    public void setCurWgt(Double curWgt) {
        this.curWgt = curWgt;
    }

    public String getFpWgtDev() {
        return fpWgtDev;
    }

    public void setFpWgtDev(String fpWgtDev) {
        this.fpWgtDev = fpWgtDev;
    }

    public Double getStrtWgt() {
        return strtWgt;
    }

    public void setStrtWgt(Double strtWgt) {
        this.strtWgt = strtWgt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFpNo() {
        return fpNo;
    }

    public void setFpNo(String fpNo) {
        this.fpNo = fpNo;
    }

    public String getFinType() {
        return finType;
    }

    public void setFinType(String finType) {
        this.finType = finType;
    }

    public String getStrtPosition() {
        return strtPosition;
    }

    public void setStrtPosition(String strtPosition) {
        this.strtPosition = strtPosition;
    }

    public String getMatCode() {
        return matCode;
    }

    public void setMatCode(String matCode) {
        this.matCode = matCode;
    }

    public Double getNetWgt() {
        return netWgt;
    }

    public void setNetWgt(Double netWgt) {
        this.netWgt = netWgt;
    }

    public String getMatName() {
        return matName;
    }

    public void setMatName(String matName) {
        this.matName = matName;
    }
}
