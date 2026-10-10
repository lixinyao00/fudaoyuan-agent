package com.study.ragagent.domain.enums;

public enum UserStatus {
    ACTIVE("活跃"),
    INACTIVE("停用"),
    LOCKED("锁定");

    private final String description;

    UserStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}