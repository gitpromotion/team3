package com.example.flightweb;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller // 수정사항 ( 테스트 )
public class FlightController {

    static final Map<String, String> AIRLINES = new LinkedHashMap<>();
    static final Map<String, String> AIRPORTS = new LinkedHashMap<>();
    static {
        AIRLINES.put("KE", "대한항공"); AIRLINES.put("OZ", "아시아나항공"); AIRLINES.put("7C", "제주항공");
        AIRLINES.put("LJ", "진에어");   AIRLINES.put("TW", "티웨이항공");   AIRLINES.put("BX", "에어부산");
        AIRLINES.put("RS", "에어서울"); AIRLINES.put("ZE", "이스타항공");
        AIRPORTS.put("GMP", "김포"); AIRPORTS.put("CJU", "제주"); AIRPORTS.put("PUS", "김해");
        AIRPORTS.put("TAE", "대구"); AIRPORTS.put("CJJ", "청주"); AIRPORTS.put("KWJ", "광주");
        AIRPORTS.put("RSU", "여수"); AIRPORTS.put("USN", "울산"); AIRPORTS.put("KPO", "포항경주");
        AIRPORTS.put("YNY", "양양"); AIRPORTS.put("WJU", "원주"); AIRPORTS.put("KUV", "군산");
        AIRPORTS.put("HIN", "사천");
    }

    private final FlightRepository repo;
    private final PredictionClient client;

    public FlightController(FlightRepository repo, PredictionClient client) {
        this.repo = repo;
        this.client = client;
    }

    /** 대시보드: 모델 성능, 항공사·공항별 실제 vs 예측, 날짜별 위험 Top 20 */
    @GetMapping("/")
    public String dashboard(@RequestParam(name = "date", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                            Model model) {
        Map<String, Object> range = repo.dateRange();
        LocalDate maxDate = LocalDate.parse(String.valueOf(range.get("max_date")).substring(0, 10));
        LocalDate day = (date != null) ? date : maxDate;

        Map<String, Object> metrics = new HashMap<>();
        for (Map<String, Object> m : repo.metrics()) {
            metrics.put(String.valueOf(m.get("metric")), m.get("metric_value"));
        }
        List<Map<String, Object>> airlineStats = repo.airlineStats();
        // 차트용 배열 (JavaScript로 넘김)
        List<Object> chartLabels = new java.util.ArrayList<>();
        List<Object> chartActual = new java.util.ArrayList<>();
        List<Object> chartPred = new java.util.ArrayList<>();
        for (Map<String, Object> s : airlineStats) {
            chartLabels.add(s.get("airline_name"));
            chartActual.add(s.get("actual_rate"));
            chartPred.add(s.get("pred_rate"));
        }
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartActual", chartActual);
        model.addAttribute("chartPred", chartPred);
        model.addAttribute("range", range);
        model.addAttribute("metrics", metrics);
        model.addAttribute("airlineStats", airlineStats);
        model.addAttribute("airportStats", repo.airportStats());
        model.addAttribute("day", day);
        model.addAttribute("topRisk", repo.topRisk(day, 20));
        return "dashboard";
    }

    /** 편명 검색 */
    @GetMapping("/search")
    public String search(@RequestParam(name = "flightNo", required = false) String flightNo,
                         @RequestParam(name = "date", required = false)
                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                         Model model) {
        model.addAttribute("flightNo", flightNo);
        model.addAttribute("date", date);
        if (flightNo != null && !flightNo.isBlank()) {
            String fno = flightNo.trim().toUpperCase();
            List<Map<String, Object>> rows = repo.searchFlight(fno, date);
            model.addAttribute("rows", rows);
            // 날짜까지 지정해서 1건이면, 같은 기체의 그날 운항 흐름도 보여준다
            if (date != null && rows.size() == 1) {
                Map<String, Object> r = rows.get(0);
                model.addAttribute("tailDay", repo.tailDay((String) r.get("tail_no"), r.get("flight_date")));
                model.addAttribute("selected", r);
            }
        }
        return "search";
    }

    /** 직접 예측 입력 폼 */
    @GetMapping("/predict")
    public String predictForm(Model model) {
        model.addAttribute("airlines", AIRLINES);
        model.addAttribute("airports", AIRPORTS);
        model.addAttribute("apiOnline", client.meta() != null);
        return "predict";
    }

    /** 직접 예측 실행 → Flask 호출 → H2에 기록 */
    @PostMapping("/predict")
    public String predict(@RequestParam String airlineCode,
                          @RequestParam String origin,
                          @RequestParam String dest,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime schedDep,
                          @RequestParam(defaultValue = "1") int legSeq,
                          @RequestParam(required = false) Double prevArrDelay,
                          @RequestParam(required = false) Double turnSlack,
                          @RequestParam(required = false) Double gustKt,
                          @RequestParam(required = false) Double visibilityM,
                          @RequestParam(required = false) String wxCode,
                          Model model) {
        Map<String, Object> req = new HashMap<>();
        req.put("airline_code", airlineCode);
        req.put("origin", origin);
        req.put("dest", dest);
        req.put("sched_dep", schedDep.toString());
        req.put("leg_seq", legSeq);
        req.put("prev_arr_delay", prevArrDelay == null ? 0 : prevArrDelay);
        if (turnSlack != null) req.put("turn_slack", turnSlack);

        Map<String, Object> weather = new HashMap<>();
        if (gustKt != null) weather.put("o_gust_kt", gustKt);
        if (visibilityM != null) weather.put("o_visibility_m", visibilityM);
        if (wxCode != null && !wxCode.isBlank()) weather.put("o_wx_code", wxCode);
        if (!weather.isEmpty()) req.put("weather", weather);

        model.addAttribute("airlines", AIRLINES);
        model.addAttribute("airports", AIRPORTS);
        model.addAttribute("apiOnline", true);
        model.addAttribute("input", req);
        model.addAttribute("airlineName", AIRLINES.get(airlineCode));
        model.addAttribute("originName", AIRPORTS.get(origin));
        model.addAttribute("destName", AIRPORTS.get(dest));
        try {
            Map<String, Object> res = client.predict(req);
            double prob = ((Number) res.get("delay_prob")).doubleValue();
            boolean delayed = Boolean.TRUE.equals(res.get("is_delayed"));
            double minutes = ((Number) res.get("expected_delay_min")).doubleValue();
            repo.saveLog(airlineCode, origin, dest, schedDep, legSeq, prevArrDelay, turnSlack, prob, delayed, minutes);
            model.addAttribute("result", res);
            model.addAttribute("probPct", Math.round(prob * 1000) / 10.0);
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "predict";
    }

    /** 예측 기록 */
    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("logs", repo.recentLogs());
        model.addAttribute("airlines", AIRLINES);
        model.addAttribute("airports", AIRPORTS);
        return "history";
    }
}
