package com.slotmate.program;

/** programs.program_type — 세부 종류. 카테고리는 종류에서 정해짐 (확장 설계 v0.1 2절) */
public enum ProgramType {
    PT(ProgramCategory.EXERCISE),
    YOGA(ProgramCategory.EXERCISE),
    GX(ProgramCategory.EXERCISE),
    PILATES(ProgramCategory.EXERCISE),
    ETC(ProgramCategory.EXERCISE),
    CRAFT(ProgramCategory.CLASS),
    COOKING(ProgramCategory.CLASS),
    ART(ProgramCategory.CLASS),
    CLASS_ETC(ProgramCategory.CLASS),
    TOUR(ProgramCategory.ACTIVITY),
    EXPERIENCE(ProgramCategory.ACTIVITY),
    LEISURE(ProgramCategory.ACTIVITY);

    private final ProgramCategory category;

    ProgramType(ProgramCategory category) {
        this.category = category;
    }

    public ProgramCategory category() {
        return category;
    }
}
