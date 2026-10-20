package com.slotmate.reservation;

import com.slotmate.common.error.BusinessException;
import com.slotmate.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 로드맵 단계 5 — 동시 요청에서 정원 초과·중복 활성 예약이 생기지 않는지 (명세 10절 2·4·5).
 * 스레드마다 별도 트랜잭션이 커밋되어야 해서 롤백 테스트(ApiTestSupport)를 쓰지 않고, 만든 데이터는 직접 지움
 */
@SpringBootTest
class ReservationConcurrencyTest {

    private static final int THREADS = 20;

    @Autowired ReservationService reservationService;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;

    private long programId;
    private long sessionId;
    private final List<Long> userIds = new ArrayList<>();

    /** LAST_INSERT_ID 는 같은 커넥션에서만 맞으므로 한 트랜잭션으로 넣고 커밋 */
    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(status -> insertFixtures());
    }

    private void insertFixtures() {
        long adminId = jdbc.queryForObject("SELECT user_id FROM users WHERE role = 'ADMIN' ORDER BY user_id LIMIT 1", Long.class);
        jdbc.update("INSERT INTO programs (program_type, name, default_duration_minutes, default_capacity) "
                + "VALUES ('YOGA', '동시성테스트', 50, 1)");
        programId = lastId();
        jdbc.update("INSERT INTO class_sessions (program_id, start_at, end_at, capacity, session_status, created_by_user_id) "
                + "VALUES (?, '2099-05-01 10:00:00', '2099-05-01 10:50:00', 1, 'OPEN', ?)", programId, adminId);
        sessionId = lastId();
        for (int i = 0; i < THREADS; i++) {
            jdbc.update("INSERT INTO users (email, password_hash, display_name, role) VALUES (?, 'x', '동시성', 'MEMBER')",
                    "concurrency-" + System.nanoTime() + "-" + i + "@example.com");
            userIds.add(lastId());
        }
    }

    @AfterEach
    void cleanUp() {
        String reservations = "SELECT reservation_id FROM reservations WHERE session_id = " + sessionId;
        jdbc.update("DELETE FROM notifications WHERE reservation_id IN (" + reservations + ")");
        jdbc.update("DELETE FROM reservation_histories WHERE reservation_id IN (" + reservations + ")");
        jdbc.update("DELETE FROM reservations WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM class_sessions WHERE session_id = ?", sessionId);
        jdbc.update("DELETE FROM programs WHERE program_id = ?", programId);
        userIds.forEach(id -> jdbc.update("DELETE FROM users WHERE user_id = ?", id));
    }

    @Test
    void 마지막_한_자리에_동시_신청하면_확정은_정원만큼_나머지는_대기() throws Exception {
        List<Callable<ReservationStatus>> tasks = userIds.stream()
                .<Callable<ReservationStatus>>map(userId -> () -> reservationService.reserve(sessionId, userId).reservationStatus())
                .toList();

        List<ReservationStatus> results = runTogether(tasks);

        assertThat(results).filteredOn(s -> s == ReservationStatus.CONFIRMED).hasSize(1);
        assertThat(results).filteredOn(s -> s == ReservationStatus.WAITING).hasSize(THREADS - 1);
        assertThat(count("CONFIRMED")).isEqualTo(1);
        // 대기 순번 1..N-1 이 겹치지 않게 계산되는지 — requested_at, id 순서가 모두 다름
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT reservation_id) FROM reservations "
                + "WHERE session_id = ? AND reservation_status = 'WAITING'", Integer.class, sessionId)).isEqualTo(THREADS - 1);
    }

    @Test
    void 같은_회원이_동시에_여러_번_신청해도_활성_예약은_하나() throws Exception {
        long userId = userIds.getFirst();
        List<Callable<ReservationStatus>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tasks.add(() -> {
                try {
                    return reservationService.reserve(sessionId, userId).reservationStatus();
                } catch (BusinessException e) {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_ACTIVE_RESERVATION);
                    return null;
                }
            });
        }

        List<ReservationStatus> results = runTogether(tasks);

        assertThat(results).filteredOn(s -> s != null).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE session_id = ? AND user_id = ?",
                Integer.class, sessionId, userId)).isEqualTo(1);
    }

    @Test
    void 확정_취소와_새_신청이_동시에_와도_정원을_넘지_않고_대기자가_승격() throws Exception {
        long ownerId = userIds.get(0);
        long reservationId = reservationService.reserve(sessionId, ownerId).reservationId();
        reservationService.reserve(sessionId, userIds.get(1));

        List<Callable<ReservationStatus>> tasks = new ArrayList<>();
        tasks.add(() -> reservationService.cancel(reservationId, ownerId).reservationStatus());
        userIds.subList(2, THREADS).forEach(userId ->
                tasks.add(() -> reservationService.reserve(sessionId, userId).reservationStatus()));

        runTogether(tasks);

        assertThat(count("CONFIRMED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT user_id FROM reservations WHERE session_id = ? "
                + "AND reservation_status = 'CONFIRMED'", Long.class, sessionId)).isEqualTo(userIds.get(1));
    }

    /** k6 부하 테스트에서 찾은 문제 — 동시 취소 4건에 같은 대기자만 4번 승격되고 3자리가 비던 것 */
    @Test
    void 확정자_여러_명이_동시에_취소하면_취소한_수만큼_서로_다른_대기자가_순서대로_승격() throws Exception {
        int capacity = 4;
        jdbc.update("UPDATE class_sessions SET capacity = ? WHERE session_id = ?", capacity, sessionId);
        List<Long> confirmedReservations = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            long reservationId = reservationService.reserve(sessionId, userIds.get(i)).reservationId();
            if (i < capacity) confirmedReservations.add(reservationId);
        }

        List<Callable<ReservationStatus>> tasks = new ArrayList<>();
        for (int i = 0; i < capacity; i++) {
            long reservationId = confirmedReservations.get(i);
            long ownerId = userIds.get(i);
            tasks.add(() -> reservationService.cancel(reservationId, ownerId).reservationStatus());
        }
        runTogether(tasks);

        assertThat(count("CONFIRMED")).isEqualTo(capacity);
        assertThat(jdbc.queryForList("SELECT user_id FROM reservations WHERE session_id = ? "
                + "AND reservation_status = 'CONFIRMED' ORDER BY user_id", Long.class, sessionId))
                .containsExactlyElementsOf(userIds.subList(capacity, capacity * 2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reservation_histories h JOIN reservations r "
                + "USING (reservation_id) WHERE r.session_id = ? AND h.event_type = 'PROMOTED'", Integer.class, sessionId))
                .isEqualTo(capacity);
        assertThat(count("WAITING")).isEqualTo(THREADS - capacity * 2);
    }

    private <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = tasks.stream().map(task -> pool.submit(() -> {
                start.await();
                return task.call();
            })).toList();
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) results.add(f.get());
            return results;
        } finally {
            pool.shutdown();
        }
    }

    private int count(String status) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM reservations WHERE session_id = ? AND reservation_status = ?",
                Integer.class, sessionId, status);
    }

    private long lastId() {
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
