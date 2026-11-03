package com.example.flightweb;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/** Python Flask 예측 API(/predict, /meta) 호출 */
@Component
public class PredictionClient {

    private final RestClient client;

    public PredictionClient(@Value("${ml.api.url}") String baseUrl) {
        this.client = RestClient.builder().baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> predict(Map<String, Object> request) {
        try {
            return client.post()
                .uri("/predict")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);
        } catch (RestClientResponseException e) {
            // Flask가 400으로 돌려준 {"error": "..."} 메시지를 그대로 보여준다
            throw new IllegalArgumentException("예측 API 오류: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new IllegalStateException(
                "예측 API에 연결할 수 없습니다. ml_api/app.py 가 실행 중인지 확인하세요. (" + e.getMessage() + ")");
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> meta() {
        try {
            return client.get().uri("/meta").retrieve().body(Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
