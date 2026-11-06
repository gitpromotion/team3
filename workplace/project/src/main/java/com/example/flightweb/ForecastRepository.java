package com.example.flightweb;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public class ForecastRepository {

    private final JdbcTemplate jdbc;

    public ForecastRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Forecast> MAPPER = (rs, i) -> {
        Forecast f = new Forecast();
        f.id = rs.getLong("id");
        f.airlineCode = rs.getString("airline_code");
        f.origin = rs.getString("origin");
        f.originName = rs.getString("origin_name");
        f.dest = rs.getString("dest");
        f.destName = rs.getString("dest_name");
        f.schedDep = rs.getTimestamp("sched_dep").toLocalDateTime();
        f.delayProb = (Double) rs.getObject("delay_prob");
        f.isDelayedPred = (Integer) rs.getObject("is_delayed_pred");
        f.expectedDelayMin = (Double) rs.getObject("expected_delay_min");
        f.predCause = rs.getString("pred_cause");
        f.predCauseProb = (Double) rs.getObject("pred_cause_prob");
        return f;
    };

    // airport 테이블로 공항 한글명 붙여 조회 (airport: airport_code, airport_name)
    private static final String BASE =
        "SELECT d.id, d.airline_code, d.origin, ao.airport_name AS origin_name, " +
        "       d.dest, ad.airport_name AS dest_name, d.sched_dep, " +
        "       d.delay_prob::float8 AS delay_prob, d.is_delayed_pred, " +
        "       d.expected_delay_min::float8 AS expected_delay_min, " +
        "       d.pred_cause, d.pred_cause_prob::float8 AS pred_cause_prob " +
        "  FROM delay_forecast d " +
        "  LEFT JOIN airport ao ON ao.airport_code = d.origin " +
        "  LEFT JOIN airport ad ON ad.airport_code = d.dest ";

    /** 날짜 + (선택)출발/도착 공항으로 예측 조회 */
    public List<Forecast> find(LocalDate date, String origin, String dest) {
        StringBuilder sql = new StringBuilder(BASE + " WHERE d.sched_dep::date = ? ");
        java.util.List<Object> args = new java.util.ArrayList<>();
        args.add(java.sql.Date.valueOf(date));
        if (origin != null && !origin.isBlank()) { sql.append(" AND d.origin = ? "); args.add(origin); }
        if (dest   != null && !dest.isBlank())   { sql.append(" AND d.dest = ? ");   args.add(dest); }
        sql.append(" ORDER BY d.sched_dep LIMIT 300");
        return jdbc.query(sql.toString(), MAPPER, args.toArray());
    }

    /** 드롭다운용 공항 목록 */
    public List<String> origins() {
        return jdbc.queryForList(
            "SELECT DISTINCT origin FROM delay_forecast ORDER BY origin", String.class);
    }

    /** 예측 가능한 날짜 범위 */
    public LocalDate minDate() {
        return jdbc.queryForObject("SELECT min(sched_dep)::date FROM delay_forecast", LocalDate.class);
    }
    public LocalDate maxDate() {
        return jdbc.queryForObject("SELECT max(sched_dep)::date FROM delay_forecast", LocalDate.class);
    }

    /** 항공사별 평균 지연확률 순위 (전체 기간) */
    public List<java.util.Map<String, Object>> airlineRanking() {
        return jdbc.queryForList(
            "SELECT d.airline_code, " +
            "       COALESCE(a.airline_name, d.airline_code) AS airline_name, " +
            "       ROUND(AVG(d.delay_prob)::numeric * 100, 1) AS avg_prob, " +
            "       ROUND(AVG(d.expected_delay_min)::numeric, 0) AS avg_min, " +
            "       COUNT(*) AS cnt " +
            "  FROM delay_forecast d " +
            "  LEFT JOIN airline a ON a.airline_code = d.airline_code " +
            " GROUP BY d.airline_code, a.airline_name " +
            " ORDER BY avg_prob DESC");
    }

    /** 노선별 지연 위험 TOP 10 (전체 기간, 편수 50 이상) */
    public List<java.util.Map<String, Object>> routeRanking() {
        return jdbc.queryForList(
            "SELECT d.origin, ao.airport_name AS origin_name, d.dest, ad.airport_name AS dest_name, " +
            "       ROUND(AVG(d.delay_prob)::numeric * 100, 1) AS avg_prob, COUNT(*) AS cnt " +
            "  FROM delay_forecast d " +
            "  LEFT JOIN airport ao ON ao.airport_code = d.origin " +
            "  LEFT JOIN airport ad ON ad.airport_code = d.dest " +
            " GROUP BY d.origin, ao.airport_name, d.dest, ad.airport_name " +
            " HAVING COUNT(*) >= 50 " +
            " ORDER BY avg_prob DESC LIMIT 10");
    }

    /** 전체 예상 사유 분포 */
    public List<java.util.Map<String, Object>> causeDistribution() {
        return jdbc.queryForList(
            "SELECT pred_cause, COUNT(*) AS cnt FROM delay_forecast " +
            " WHERE pred_cause IS NOT NULL GROUP BY pred_cause ORDER BY cnt DESC");
    }

    /** 월별 지연 추세 (전체 기간) */
    public List<java.util.Map<String, Object>> monthlyTrend() {
        return jdbc.queryForList(
            "SELECT to_char(sched_dep,'YYYY-MM') AS ym, " +
            "       ROUND(AVG(delay_prob)::numeric * 100, 1) AS avg_prob, " +
            "       ROUND(AVG(expected_delay_min)::numeric, 1) AS avg_min " +
            "  FROM delay_forecast GROUP BY ym ORDER BY ym");
    }

    /** 공항 × 시간대 지연 히트맵 (출발공항별 시간대 평균 지연확률%) */
    public List<java.util.Map<String, Object>> heatmap() {
        return jdbc.queryForList(
            "SELECT origin, EXTRACT(HOUR FROM sched_dep)::int AS hr, " +
            "       ROUND(AVG(delay_prob)::numeric * 100, 0) AS v, COUNT(*) AS cnt " +
            "  FROM delay_forecast GROUP BY origin, hr ORDER BY origin, hr");
    }
}
