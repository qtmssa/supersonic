package com.tencent.supersonic;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {StandaloneLauncher.class}, properties = {
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.url=jdbc:h2:mem:semantic;DATABASE_TO_UPPER=false;QUERY_TIMEOUT=30",
                "spring.datasource.username=root", "spring.datasource.password=semantic",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:db/schema-h2.sql,classpath:db/schema-h2-demo.sql",
                "spring.sql.init.data-locations=classpath:db/data-h2.sql,classpath:db/data-h2-demo.sql",
                "s2.superset.enabled=false", "s2.superset.sync.enabled=false"})
public class BaseApplication {
}
