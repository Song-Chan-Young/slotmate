package com.slotmate.program;

import java.util.Arrays;
import java.util.List;

/** 카테고리 — 컬럼 없이 세부 종류(ProgramType)가 속한 곳으로 정함 (확장 설계 v0.1 2절) */
public enum ProgramCategory {
    EXERCISE, CLASS, ACTIVITY;

    public List<ProgramType> types() {
        return Arrays.stream(ProgramType.values()).filter(t -> t.category() == this).toList();
    }

    /** 조회 조건용 — null 이면 전체 종류 (쿼리는 항상 programType in :types) */
    public static List<ProgramType> typesOrAll(ProgramCategory category) {
        return category == null ? List.of(ProgramType.values()) : category.types();
    }
}
