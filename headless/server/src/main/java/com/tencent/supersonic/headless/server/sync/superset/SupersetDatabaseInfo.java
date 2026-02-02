package com.tencent.supersonic.headless.server.sync.superset;

import lombok.Data;

@Data
public class SupersetDatabaseInfo {

    private Long id;
    private String name;
    private String sqlalchemyUri;
    private String schema;
}
