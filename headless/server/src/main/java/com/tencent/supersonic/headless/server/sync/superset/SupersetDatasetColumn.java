package com.tencent.supersonic.headless.server.sync.superset;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Data
public class SupersetDatasetColumn {

    private Long id;
    private String columnName;
    private String expression;
    private String type;
    private Boolean isDttm;
    private Boolean filterable;
    private Boolean groupby;
    private Boolean isActive;
    private String verboseName;
    private String description;
    private String pythonDateFormat;

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        if (id != null) {
            payload.put("id", id);
        }
        if (StringUtils.isNotBlank(columnName)) {
            payload.put("column_name", columnName);
        }
        if (StringUtils.isNotBlank(expression)) {
            payload.put("expression", expression);
        }
        if (StringUtils.isNotBlank(type)) {
            payload.put("type", type);
        }
        if (isDttm != null) {
            payload.put("is_dttm", isDttm);
        }
        if (filterable != null) {
            payload.put("filterable", filterable);
        }
        if (groupby != null) {
            payload.put("groupby", groupby);
        }
        if (isActive != null) {
            payload.put("is_active", isActive);
        }
        if (StringUtils.isNotBlank(verboseName)) {
            payload.put("verbose_name", verboseName);
        }
        if (StringUtils.isNotBlank(description)) {
            payload.put("description", description);
        }
        if (StringUtils.isNotBlank(pythonDateFormat)) {
            payload.put("python_date_format", pythonDateFormat);
        }
        return payload;
    }
}
