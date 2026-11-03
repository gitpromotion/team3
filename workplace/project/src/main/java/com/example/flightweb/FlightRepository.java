package com.example.flightweb;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Neon PostgreSQL 데이터 조회 및 저장
 */
@Repository
public class FlightRepository {

    private final JdbcTemplate jdbc;

    public FlightRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 모델 성능 지표 */
    public List<Map<String, Object>> metrics() {
        return jdbc.queryForList("""
            SELECT metric, metric_value, description
            FROM model_metric
            """);
    }

    /** 데이터 기간 */
    public Map<String, Object> dateRange() {
        return jdbc.queryForMap("""
            SELECT
                MIN(flight_date) AS min_date,
                MAX(flight_date) AS max_date,
                COUNT(*) AS total
            FROM flight_prediction
            """);
    }

    /** 항공사별 지연율 통계 */
    public List<Map<String, Object>> airlineStats() {
        return jdbc.queryForList("""
            SELECT
                airline_name,
                COUNT(*) AS flights,
                ROUND(
                    AVG(CAST(actual_is_delayed AS NUMERIC)) * 100,
                    1
                ) AS actual_rate,
                ROUND(
                    AVG(CAST(pred_delay AS NUMERIC)) * 100,
                    1
                ) AS pred_rate,
                ROUND(
                    AVG(
                        CASE
                            WHEN actual_is_delayed = 1
                            THEN CAST(actual_dep_delay AS NUMERIC)
                        END
                    ),
                    1
                ) AS avg_delay_min
            FROM flight_prediction
            GROUP BY airline_name
            ORDER BY actual_rate DESC
            """);
    }

    /** 출발 공항별 지연율 */
    public List<Map<String, Object>> airportStats() {
        return jdbc.queryForList("""
            SELECT
                origin_name,
                COUNT(*) AS flights,
                ROUND(
                    AVG(CAST(actual_is_delayed AS NUMERIC)) * 100,
                    1
                ) AS actual_rate,
                ROUND(
                    AVG(CAST(pred_delay AS NUMERIC)) * 100,
                    1
                ) AS pred_rate
            FROM flight_prediction
            GROUP BY origin_name
            HAVING COUNT(*) >= 500
            ORDER BY actual_rate DESC
            """);
    }

    /** 특정 날짜의 지연 위험 상위 항공편 */
    public List<Map<String, Object>> topRisk(
            LocalDate date, int limit) {

        return jdbc.queryForList("""
            SELECT *
            FROM flight_prediction
            WHERE flight_date = ?
            ORDER BY delay_prob DESC, sched_dep
            LIMIT ?
            """,
            Date.valueOf(date),
            limit
        );
    }

    /** 항공편 검색 */
    public List<Map<String, Object>> searchFlight(
            String flightNo, LocalDate date) {

        if (date != null) {
            return jdbc.queryForList("""
                SELECT *
                FROM flight_prediction
                WHERE flight_no = ?
                  AND flight_date = ?
                """,
                flightNo,
                Date.valueOf(date)
            );
        }

        return jdbc.queryForList("""
            SELECT *
            FROM flight_prediction
            WHERE flight_no = ?
            ORDER BY sched_dep DESC
            LIMIT 30
            """,
            flightNo
        );
    }

    /** 동일 기체의 당일 운항 조회 */
    public List<Map<String, Object>> tailDay(
            String tailNo, Object flightDate) {

        return jdbc.queryForList("""
            SELECT *
            FROM flight_prediction
            WHERE tail_no = ?
              AND flight_date = ?
            ORDER BY sched_dep
            """,
            tailNo,
            flightDate
        );
    }

    /** 직접 예측 결과 저장 */
    public void saveLog(
            String airline,
            String origin,
            String dest,
            LocalDateTime schedDep,
            int legSeq,
            Double prevArrDelay,
            Double turnSlack,
            double prob,
            boolean delayed,
            double minutes) {

        jdbc.update("""
            INSERT INTO prediction_log
            (
                airline_code,
                origin,
                dest,
                sched_dep,
                leg_seq,
                prev_arr_delay,
                turn_slack,
                delay_prob,
                is_delayed,
                expected_delay_min
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            airline,
            origin,
            dest,
            Timestamp.valueOf(schedDep),
            legSeq,
            prevArrDelay,
            turnSlack,
            prob,
            delayed,
            minutes
        );
    }

    /** 최근 예측 기록 */
    public List<Map<String, Object>> recentLogs() {
        return jdbc.queryForList("""
            SELECT *
            FROM prediction_log
            ORDER BY id DESC
            LIMIT 100
            """);
    }
}
