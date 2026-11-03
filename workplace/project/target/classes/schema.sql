-- 모델이 test 기간(2025-08-08 ~ 2025-12-31) 각 편에 대해 낸 예측 + 실제 결과
DROP TABLE IF EXISTS flight_prediction;
CREATE TABLE flight_prediction (
    flight_id          BIGINT PRIMARY KEY,
    flight_date        DATE,
    airline_code       VARCHAR(5),
    airline_name       VARCHAR(30),
    flight_no          VARCHAR(10),
    tail_no            VARCHAR(10),
    leg_seq            INT,
    origin             VARCHAR(5),
    origin_name        VARCHAR(20),
    dest               VARCHAR(5),
    dest_name          VARCHAR(20),
    sched_dep          TIMESTAMP,
    sched_arr          TIMESTAMP,
    prev_arr_delay     DOUBLE,
    o_wx_code          VARCHAR(5),
    o_gust_kt          DOUBLE,
    o_visibility_m     INT,
    delay_prob         DOUBLE,
    pred_delay         INT,
    pred_delay_min     DOUBLE,
    actual_dep_delay   DOUBLE,
    actual_is_delayed  INT,
    delay_cause        VARCHAR(30)
);
CREATE INDEX idx_fp_flight ON flight_prediction (flight_no, flight_date);
CREATE INDEX idx_fp_date   ON flight_prediction (flight_date);

-- 모델 성능 지표
DROP TABLE IF EXISTS model_metric;
CREATE TABLE model_metric (
    metric        VARCHAR(30) PRIMARY KEY,
    metric_value  DOUBLE,
    description   VARCHAR(100)
);

-- 웹에서 사용자가 직접 실행한 예측 기록 (재시작해도 유지)
CREATE TABLE IF NOT EXISTS prediction_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    airline_code        VARCHAR(5),
    origin              VARCHAR(5),
    dest                VARCHAR(5),
    sched_dep           TIMESTAMP,
    leg_seq             INT,
    prev_arr_delay      DOUBLE,
    turn_slack          DOUBLE,
    delay_prob          DOUBLE,
    is_delayed          BOOLEAN,
    expected_delay_min  DOUBLE
);
