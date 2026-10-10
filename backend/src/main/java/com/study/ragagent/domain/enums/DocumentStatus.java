package com.study.ragagent.domain.enums;

public enum DocumentStatus {
    UPLOADED("已上传"),
    PROCESSING("处理中"),
    COMPLETED("已完成"),
    FAILED("失败");

    private final String description;

    DocumentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
