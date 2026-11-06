package com.example.flightweb;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@Controller
public class ForecastController {

    private final ForecastRepository repo;

    public ForecastController(ForecastRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/")
    public String home(
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "origin", required = false) String origin,
            @RequestParam(name = "dest", required = false) String dest,
            Model model) {

        LocalDate min = repo.minDate();
        LocalDate max = repo.maxDate();
        model.addAttribute("origins", repo.origins());
        model.addAttribute("minDate", min);
        model.addAttribute("maxDate", max);
        model.addAttribute("origin", origin);
        model.addAttribute("dest", dest);

        // ── 전체 기간 통계 (날짜 선택과 무관하게 항상 표시) ──
        var airlineRank = repo.airlineRanking();
        model.addAttribute("airlineRank", airlineRank);
        model.addAttribute("airlineLabels", airlineRank.stream().map(m -> String.valueOf(m.get("airline_name"))).toList());
        model.addAttribute("airlineValues", airlineRank.stream().map(m -> m.get("avg_prob")).toList());
        model.addAttribute("routeRank", repo.routeRanking());
        var causeDist = repo.causeDistribution();
        model.addAttribute("causeDistLabels", causeDist.stream().map(m -> String.valueOf(m.get("pred_cause"))).toList());
        model.addAttribute("causeDistValues", causeDist.stream().map(m -> m.get("cnt")).toList());

        // 월별 지연 추세
        var trend = repo.monthlyTrend();
        model.addAttribute("monthLabels", trend.stream().map(m -> String.valueOf(m.get("ym"))).toList());
        model.addAttribute("monthProb", trend.stream().map(m -> m.get("avg_prob")).toList());
        model.addAttribute("monthMin", trend.stream().map(m -> m.get("avg_min")).toList());

        // 공항 × 시간대 히트맵 (JS에서 격자 렌더)
        model.addAttribute("heatRows", repo.heatmap());

        if (date != null) {
            List<Forecast> results = repo.find(date, origin, dest);
            model.addAttribute("date", date);
            model.addAttribute("results", results);
            // 요약 통계
            int total = results.size();
            long delayed = results.stream().filter(f -> f.isDelayedPred != null && f.isDelayedPred == 1).count();
            double avgProb = results.stream().filter(f -> f.delayProb != null)
                    .mapToDouble(f -> f.delayProb).average().orElse(0);
            double avgMin = results.stream().filter(f -> f.expectedDelayMin != null)
                    .mapToDouble(f -> f.expectedDelayMin).average().orElse(0);
            String topCause = results.stream()
                    .filter(f -> f.predCause != null)
                    .collect(java.util.stream.Collectors.groupingBy(f -> f.predCause, java.util.stream.Collectors.counting()))
                    .entrySet().stream().max(java.util.Map.Entry.comparingByValue())
                    .map(java.util.Map.Entry::getKey).orElse("-");
            model.addAttribute("total", total);
            model.addAttribute("delayed", delayed);
            model.addAttribute("ontime", total - delayed);
            model.addAttribute("avgProbPct", (int) Math.round(avgProb * 100));
            model.addAttribute("avgMin", Math.round(avgMin));
            model.addAttribute("topCause", topCause);

            // ── 차트1: 시간대별 평균 지연확률(%) ──
            java.util.TreeMap<Integer, double[]> byHour = new java.util.TreeMap<>();
            for (Forecast f : results) {
                if (f.delayProb == null) continue;
                int h = f.schedDep.getHour();
                double[] acc = byHour.computeIfAbsent(h, k -> new double[2]);
                acc[0] += f.delayProb; acc[1] += 1;
            }
            java.util.List<String> hourLabels = new java.util.ArrayList<>();
            java.util.List<Integer> hourValues = new java.util.ArrayList<>();
            byHour.forEach((h, acc) -> {
                hourLabels.add(String.format("%02d시", h));
                hourValues.add((int) Math.round(acc[0] / acc[1] * 100));
            });
            model.addAttribute("hourLabels", hourLabels);
            model.addAttribute("hourValues", hourValues);

            // ── 차트2: 예상 사유 분포 ──
            java.util.Map<String, Long> causeCount = results.stream()
                    .filter(f -> f.predCause != null)
                    .collect(java.util.stream.Collectors.groupingBy(f -> f.predCause, java.util.stream.Collectors.counting()));
            model.addAttribute("causeLabels", new java.util.ArrayList<>(causeCount.keySet()));
            model.addAttribute("causeValues", new java.util.ArrayList<>(causeCount.values()));

            // ── 랭킹: 지연확률 높은 TOP 10 ──
            java.util.List<Forecast> ranking = results.stream()
                    .filter(f -> f.delayProb != null)
                    .sorted(java.util.Comparator.comparingDouble((Forecast f) -> f.delayProb).reversed())
                    .limit(10).toList();
            model.addAttribute("ranking", ranking);
        }
        return "index";
    }
}
