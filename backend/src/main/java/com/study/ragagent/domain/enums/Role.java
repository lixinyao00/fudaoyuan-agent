package com.study.ragagent.domain.enums;

public enum Role {
    ADMIN("管理员"),
    USER("普通用户");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}