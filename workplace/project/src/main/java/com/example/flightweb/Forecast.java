package com.example.flightweb;

import java.time.LocalDateTime;

/** delay_forecast 한 행 (미래 지연 예측) */
public class Forecast {
    public Long id;
    public String airlineCode;
    public String origin;
    public String originName;
    public String dest;
    public String destName;
    public LocalDateTime schedDep;
    public Double delayProb;        // 심한 지연(60분+) 확률
    public Integer isDelayedPred;   // 판정 1/0
    public Double expectedDelayMin; // 예상 지연(분)
    public String predCause;        // 예상 사유
    public Double predCauseProb;    // 사유 확률

    // 화면 표시용 파생값
    public int delayPercent()      { return delayProb == null ? 0 : (int) Math.round(delayProb * 100); }
    public int causePercent()      { return predCauseProb == null ? 0 : (int) Math.round(predCauseProb * 100); }
    public String verdict() {
        double p = delayProb == null ? 0 : delayProb;
        if (p < 0.15) return "정상 운항 예상";
        if (p < 0.30) return "지연 가능성 있음";
        return "지연 우려";
    }
    public String verdictClass() {
        double p = delayProb == null ? 0 : delayProb;
        if (p < 0.15) return "ok";
        if (p < 0.30) return "warn";
        return "bad";
    }
}
