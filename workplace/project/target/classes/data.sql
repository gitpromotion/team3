-- Python(train_and_export.py)이 만든 CSV를 H2로 적재
-- 경로는 앱을 실행하는 폴더(flight-web) 기준
INSERT INTO flight_prediction SELECT * FROM CSVREAD('./data/predictions.csv', NULL, 'charset=UTF-8');
INSERT INTO model_metric SELECT * FROM CSVREAD('./data/model_metrics.csv', NULL, 'charset=UTF-8');
