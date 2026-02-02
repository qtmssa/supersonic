package com.tencent.supersonic.headless.server.sync.superset;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Data
public class SupersetDatasetMetric {

    private Long id;
    private String metricName;
    private String expression;
    private String metricType;
    private String verboseName;
    private String description;

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        if (id != null) {
            payload.put("id", id);
        }
        if (StringUtils.isNotBlank(metricName)) {
            payload.put("metric_name", metricName);
        }
        if (StringUtils.isNotBlank(expression)) {
            payload.put("expression", expression);
        }
        if (StringUtils.isNotBlank(metricType)) {
            payload.put("metric_type", metricType);
        }
        if (StringUtils.isNotBlank(verboseName)) {
            payload.put("verbose_name", verboseName);
        }
        if (StringUtils.isNotBlank(description)) {
            payload.put("description", description);
        }
        return payload;
    }
}
