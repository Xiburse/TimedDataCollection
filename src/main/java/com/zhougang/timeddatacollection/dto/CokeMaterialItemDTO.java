package com.zhougang.timeddatacollection.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * 焦炭材料查询返回的单条物料流转节点
 * 对应响应中 data 数组里的每个元素
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CokeMaterialItemDTO {

    private Double transportWeight;
    private String rowUpdate;
    private String warehouseStartTime;
    private Boolean mixBatchNo;
    private String materialGraphName;
    private String meteringEquipmentCode;
    private String startPosDescr;
    private String startTime;
    private String transportType;
    private Double endScaleValue;
    private String id;
    private List<Object> mixMaterialList;
    private String rowCreate;
    private String meteringEquipmentDescr;
    private String matName;
    private Boolean mixMaterial;
    private String sampleId;
    private Double compensateWeight;
    private Double mt;
    private Double startScaleValue;
    private List<Object> mixBatchNoList;
    private Integer carNum;
    private String endPosDescr;
    private Map<String, Object> materialCategory;
    private String startPos;
    private String endPos;
    private Long parentFlowId;
    private String sourceType;
    private String transportMode;
    private Long flowConfigId;
    private String endTime;
    private String matCode;
    private String status;

    // ====== getters & setters ======

    public Double getTransportWeight() {
        return transportWeight;
    }

    public void setTransportWeight(Double transportWeight) {
        this.transportWeight = transportWeight;
    }

    public String getRowUpdate() {
        return rowUpdate;
    }

    public void setRowUpdate(String rowUpdate) {
        this.rowUpdate = rowUpdate;
    }

    public String getWarehouseStartTime() {
        return warehouseStartTime;
    }

    public void setWarehouseStartTime(String warehouseStartTime) {
        this.warehouseStartTime = warehouseStartTime;
    }

    public Boolean getMixBatchNo() {
        return mixBatchNo;
    }

    public void setMixBatchNo(Boolean mixBatchNo) {
        this.mixBatchNo = mixBatchNo;
    }

    public String getMaterialGraphName() {
        return materialGraphName;
    }

    public void setMaterialGraphName(String materialGraphName) {
        this.materialGraphName = materialGraphName;
    }

    public String getMeteringEquipmentCode() {
        return meteringEquipmentCode;
    }

    public void setMeteringEquipmentCode(String meteringEquipmentCode) {
        this.meteringEquipmentCode = meteringEquipmentCode;
    }

    public String getStartPosDescr() {
        return startPosDescr;
    }

    public void setStartPosDescr(String startPosDescr) {
        this.startPosDescr = startPosDescr;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getTransportType() {
        return transportType;
    }

    public void setTransportType(String transportType) {
        this.transportType = transportType;
    }

    public Double getEndScaleValue() {
        return endScaleValue;
    }

    public void setEndScaleValue(Double endScaleValue) {
        this.endScaleValue = endScaleValue;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Object> getMixMaterialList() {
        return mixMaterialList;
    }

    public void setMixMaterialList(List<Object> mixMaterialList) {
        this.mixMaterialList = mixMaterialList;
    }

    public String getRowCreate() {
        return rowCreate;
    }

    public void setRowCreate(String rowCreate) {
        this.rowCreate = rowCreate;
    }

    public String getMeteringEquipmentDescr() {
        return meteringEquipmentDescr;
    }

    public void setMeteringEquipmentDescr(String meteringEquipmentDescr) {
        this.meteringEquipmentDescr = meteringEquipmentDescr;
    }

    public String getMatName() {
        return matName;
    }

    public void setMatName(String matName) {
        this.matName = matName;
    }

    public Boolean getMixMaterial() {
        return mixMaterial;
    }

    public void setMixMaterial(Boolean mixMaterial) {
        this.mixMaterial = mixMaterial;
    }

    public String getSampleId() {
        return sampleId;
    }

    public void setSampleId(String sampleId) {
        this.sampleId = sampleId;
    }

    public Double getCompensateWeight() {
        return compensateWeight;
    }

    public void setCompensateWeight(Double compensateWeight) {
        this.compensateWeight = compensateWeight;
    }

    public Double getMt() {
        return mt;
    }

    public void setMt(Double mt) {
        this.mt = mt;
    }

    public Double getStartScaleValue() {
        return startScaleValue;
    }

    public void setStartScaleValue(Double startScaleValue) {
        this.startScaleValue = startScaleValue;
    }

    public List<Object> getMixBatchNoList() {
        return mixBatchNoList;
    }

    public void setMixBatchNoList(List<Object> mixBatchNoList) {
        this.mixBatchNoList = mixBatchNoList;
    }

    public Integer getCarNum() {
        return carNum;
    }

    public void setCarNum(Integer carNum) {
        this.carNum = carNum;
    }

    public String getEndPosDescr() {
        return endPosDescr;
    }

    public void setEndPosDescr(String endPosDescr) {
        this.endPosDescr = endPosDescr;
    }

    public Map<String, Object> getMaterialCategory() {
        return materialCategory;
    }

    public void setMaterialCategory(Map<String, Object> materialCategory) {
        this.materialCategory = materialCategory;
    }

    public String getStartPos() {
        return startPos;
    }

    public void setStartPos(String startPos) {
        this.startPos = startPos;
    }

    public String getEndPos() {
        return endPos;
    }

    public void setEndPos(String endPos) {
        this.endPos = endPos;
    }

    public Long getParentFlowId() {
        return parentFlowId;
    }

    public void setParentFlowId(Long parentFlowId) {
        this.parentFlowId = parentFlowId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
    }

    public Long getFlowConfigId() {
        return flowConfigId;
    }

    public void setFlowConfigId(Long flowConfigId) {
        this.flowConfigId = flowConfigId;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getMatCode() {
        return matCode;
    }

    public void setMatCode(String matCode) {
        this.matCode = matCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
