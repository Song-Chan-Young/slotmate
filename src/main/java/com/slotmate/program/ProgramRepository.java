package com.slotmate.program;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProgramRepository extends JpaRepository<Program, Long> {

    /** type·keyword 는 null 이면 조건 없음. keyword 는 프로그램명 부분 일치 */
    @Query("""
            select p from Program p
            where (:type is null or p.programType = :type)
              and (:keyword is null or p.name like concat('%', :keyword, '%'))
            """)
    Page<Program> search(@Param("type") ProgramType type, @Param("keyword") String keyword, Pageable pageable);

    /** 회원용 — 활성 프로그램만 (API 명세 6.1) */
    @Query("""
            select p from Program p
            where p.active = true
              and (:type is null or p.programType = :type)
              and (:keyword is null or p.name like concat('%', :keyword, '%'))
            """)
    Page<Program> searchActive(@Param("type") ProgramType type, @Param("keyword") String keyword, Pageable pageable);
}
