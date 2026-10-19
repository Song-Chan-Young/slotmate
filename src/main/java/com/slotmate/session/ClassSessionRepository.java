package com.slotmate.session;

import com.slotmate.program.ProgramType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    /** 정원·상태 변경 시 회차 행 잠금 (API 명세 10절 2·7) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ClassSession s where s.id = :id")
    Optional<ClassSession> findByIdForUpdate(@Param("id") Long id);

    /** 시작 시각이 [from, to) 인 회차. type·status 는 null 이면 조건 없음 */
    @Query(value = """
            select s from ClassSession s
            join fetch s.program p
            left join fetch s.instructor
            where s.startAt >= :from and s.startAt < :to
              and (:type is null or p.programType = :type)
              and (:status is null or s.sessionStatus = :status)
            """,
            countQuery = """
            select count(s) from ClassSession s join s.program p
            where s.startAt >= :from and s.startAt < :to
              and (:type is null or p.programType = :type)
              and (:status is null or s.sessionStatus = :status)
            """)
    Page<ClassSession> searchForAdmin(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                      @Param("type") ProgramType type, @Param("status") SessionStatus status,
                                      Pageable pageable);

    /** 회원용 — DRAFT 제외, keyword 는 프로그램명 또는 강사명 (API 명세 6.3) */
    @Query(value = """
            select s from ClassSession s
            join fetch s.program p
            left join fetch s.instructor i
            where s.startAt >= :from and s.startAt < :to
              and s.sessionStatus <> com.slotmate.session.SessionStatus.DRAFT
              and (:type is null or p.programType = :type)
              and (:keyword is null or p.name like concat('%', :keyword, '%')
                   or i.name like concat('%', :keyword, '%'))
            """,
            countQuery = """
            select count(s) from ClassSession s join s.program p left join s.instructor i
            where s.startAt >= :from and s.startAt < :to
              and s.sessionStatus <> com.slotmate.session.SessionStatus.DRAFT
              and (:type is null or p.programType = :type)
              and (:keyword is null or p.name like concat('%', :keyword, '%')
                   or i.name like concat('%', :keyword, '%'))
            """)
    Page<ClassSession> searchPublic(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                    @Param("type") ProgramType type, @Param("keyword") String keyword,
                                    Pageable pageable);

    /** 회원용 — DRAFT 는 없는 것으로 봄 */
    @Query("""
            select s from ClassSession s
            join fetch s.program
            left join fetch s.instructor
            where s.id = :id and s.sessionStatus <> com.slotmate.session.SessionStatus.DRAFT
            """)
    Optional<ClassSession> findPublicById(@Param("id") Long id);

    /** 프로그램의 공개 예정 회차 — 시작 시각이 [from, to) , to 가 null 이면 끝 없음 (API 명세 6.2) */
    @Query("""
            select s from ClassSession s
            join fetch s.program p
            left join fetch s.instructor
            where p.id = :programId
              and s.sessionStatus <> com.slotmate.session.SessionStatus.DRAFT
              and s.startAt >= :from
              and (:to is null or s.startAt < :to)
            order by s.startAt, s.id
            """)
    List<ClassSession> findUpcomingPublic(@Param("programId") Long programId, @Param("from") LocalDateTime from,
                                          @Param("to") LocalDateTime to);
}
