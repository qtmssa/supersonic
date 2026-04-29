package com.tencent.supersonic.headless.core.adaptor.db;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class PostgresqlAdaptorTest {

    @Test
    void rewriteSqlShouldAdaptYearAndWeekFunctionsInsideWithQuery() {
        PostgresqlAdaptor adaptor = new PostgresqlAdaptor();
        String sql = "WITH weekly_visits AS (SELECT YEAR(imp_date) AS _year_, "
                + "WEEK(imp_date) AS _week_, count(1) AS total_visits FROM t_1 "
                + "GROUP BY YEAR(imp_date), WEEK(imp_date)) "
                + "SELECT _year_, _week_ FROM weekly_visits "
                + "WHERE _year_ = YEAR(CURRENT_DATE)";

        String rewritten = adaptor.rewriteSql(sql);

        Assertions.assertFalse(rewritten.contains("YEAR("));
        Assertions.assertFalse(rewritten.contains("WEEK("));
        Assertions.assertTrue(rewritten.contains("TO_CHAR(imp_date, 'YYYY')"));
        Assertions.assertTrue(rewritten.contains("TO_CHAR(imp_date, 'IW')"));
        Assertions.assertTrue(rewritten.contains("TO_CHAR(CURRENT_DATE, 'YYYY')"));
    }

    @Test
    void rewriteSqlShouldAdaptYearAndWeekFunctionsForFormattedWithQuery() {
        PostgresqlAdaptor adaptor = new PostgresqlAdaptor();
        String sql = String.join("\n",
                "WITH",
                "  weekly_visits AS (",
                "    SELECT",
                "      YEAR (imp_date) AS _year_,",
                "      WEEK (imp_date) AS _week_,",
                "      count(1) AS total_visits",
                "    FROM",
                "      t_1",
                "    GROUP BY",
                "      YEAR (imp_date),",
                "      WEEK (imp_date)",
                "  )",
                "SELECT",
                "  _year_,",
                "  _week_,",
                "  total_visits",
                "FROM",
                "  weekly_visits",
                "WHERE",
                "  (_year_ = YEAR (CURRENT_DATE))");

        String rewritten = adaptor.rewriteSql(sql);

        Assertions.assertFalse(rewritten.contains("YEAR("));
        Assertions.assertFalse(rewritten.contains("WEEK("));
        Assertions.assertFalse(rewritten.contains("YEAR ("));
        Assertions.assertFalse(rewritten.contains("WEEK ("));
        Assertions.assertTrue(rewritten.contains("TO_CHAR(imp_date, 'YYYY')"));
        Assertions.assertTrue(rewritten.contains("TO_CHAR(imp_date, 'IW')"));
        Assertions.assertTrue(rewritten.contains("TO_CHAR(CURRENT_DATE, 'YYYY')"));
    }
}
