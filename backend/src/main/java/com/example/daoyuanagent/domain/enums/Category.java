package com.example.daoyuanagent.domain.enums;

public enum Category {
    SCHOLARSHIP("奖助评优"),
    ATTENDANCE("考勤请假"),
    DORMITORY("宿舍管理"),
    ACADEMIC("学业教务"),
    MENTAL_HEALTH("心理健康"),
    EMPLOYMENT("就业毕业"),
    SAFETY("安全应急"),
    DAILY("日常事务");

    private final String label;

    Category(String label) { this.label = label; }
    public String getLabel() { return label; }
}
