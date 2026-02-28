package ru.perevalov.gamerecommenderai.constant;

public final class GrpcAiMetricsConstant {

    public GrpcAiMetricsConstant() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String AI_RETRY_TOTAL = "ai_retry_total";
    public static final String AI_CB_OPEN_TOTAL = "ai_cb_open_total";
    public static final String AI_FAILURES_TOTAL = "ai_failures_total";
    public static final String AI_SERVICE_LATENCY = "ai_service_latency";

    public static final String TAG_REASON = "reason";
    public static final String TAG_OUTCOME = "outcome";

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_ERROR = "error";
}

