package com.tencent.supersonic.headless.server.sync.superset;

import com.tencent.supersonic.common.pojo.DimensionConstants;
import com.tencent.supersonic.common.pojo.User;
import com.tencent.supersonic.headless.api.pojo.DataSetDetail;
import com.tencent.supersonic.headless.api.pojo.DataSetModelConfig;
import com.tencent.supersonic.headless.api.pojo.DimensionTimeTypeParams;
import com.tencent.supersonic.headless.api.pojo.Measure;
import com.tencent.supersonic.headless.api.pojo.MetricDefineByMeasureParams;
import com.tencent.supersonic.headless.api.pojo.ModelDetail;
import com.tencent.supersonic.headless.api.pojo.enums.DimensionType;
import com.tencent.supersonic.headless.api.pojo.enums.MetricDefineType;
import com.tencent.supersonic.headless.api.pojo.response.DataSetResp;
import com.tencent.supersonic.headless.api.pojo.response.DatabaseResp;
import com.tencent.supersonic.headless.api.pojo.response.DimSchemaResp;
import com.tencent.supersonic.headless.api.pojo.response.MetricSchemaResp;
import com.tencent.supersonic.headless.api.pojo.response.ModelResp;
import com.tencent.supersonic.headless.api.pojo.response.ModelSchemaResp;
import com.tencent.supersonic.headless.server.service.DataSetService;
import com.tencent.supersonic.headless.server.service.DatabaseService;
import com.tencent.supersonic.headless.server.service.ModelService;
import com.tencent.supersonic.headless.server.service.SchemaService;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupersetSyncServiceTest {

    @Test
    void syncDatasetsUpdatesWhenSqlChanges() {
        SupersetSyncClient client = Mockito.mock(SupersetSyncClient.class);
        DatabaseService databaseService = Mockito.mock(DatabaseService.class);
        ModelService modelService = Mockito.mock(ModelService.class);
        SchemaService schemaService = Mockito.mock(SchemaService.class);
        DataSetService dataSetService = Mockito.mock(DataSetService.class);
        SupersetSyncProperties properties = buildProperties();

        DatabaseResp databaseResp = buildDatabaseResp();
        when(databaseService.getDatabaseList(User.getDefaultUser()))
                .thenReturn(Collections.singletonList(databaseResp));
        when(databaseService.getDatabase(eq(1L), any(User.class))).thenReturn(databaseResp);

        SupersetDatabaseInfo supersetDatabase = new SupersetDatabaseInfo();
        supersetDatabase.setId(10L);
        supersetDatabase.setName("supersonic_db_1_demo");
        when(client.listDatabases()).thenReturn(Collections.singletonList(supersetDatabase));

        SupersetDatasetInfo existingDataset = new SupersetDatasetInfo();
        existingDataset.setId(99L);
        existingDataset.setDatabaseId(10L);
        existingDataset.setTableName("supersonic_model_1_modelA");
        existingDataset.setSchema("public");
        existingDataset.setSql("select * from old_table");
        when(client.listDatasets()).thenReturn(Collections.singletonList(existingDataset));

        ModelResp modelResp = buildModelResp("select * from new_table");
        when(modelService.getModelList(any())).thenReturn(Collections.singletonList(modelResp));

        SupersetSyncService service = new SupersetSyncService(client, properties, databaseService,
                modelService, schemaService, dataSetService);
        SupersetSyncResult result =
                service.triggerDatasetSyncByModelIds(Set.of(1L), SupersetSyncTrigger.MANUAL);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(result.getStats().getUpdated(), 1);
        verify(client, times(1)).updateDataset(eq(99L), any(SupersetDatasetInfo.class));
    }

    @Test
    void syncDatasetsCreatesWithMetricsAndDimensions() {
        SupersetSyncClient client = Mockito.mock(SupersetSyncClient.class);
        DatabaseService databaseService = Mockito.mock(DatabaseService.class);
        ModelService modelService = Mockito.mock(ModelService.class);
        SchemaService schemaService = Mockito.mock(SchemaService.class);
        DataSetService dataSetService = Mockito.mock(DataSetService.class);
        SupersetSyncProperties properties = buildProperties();

        DatabaseResp databaseResp = buildDatabaseResp();
        when(databaseService.getDatabaseList(User.getDefaultUser()))
                .thenReturn(Collections.singletonList(databaseResp));
        when(databaseService.getDatabase(eq(1L), any(User.class))).thenReturn(databaseResp);

        SupersetDatabaseInfo supersetDatabase = new SupersetDatabaseInfo();
        supersetDatabase.setId(10L);
        supersetDatabase.setName("supersonic_db_1_demo");
        when(client.listDatabases()).thenReturn(Collections.singletonList(supersetDatabase));
        when(client.listDatasets()).thenReturn(Collections.emptyList());
        when(client.createDataset(any())).thenReturn(100L);
        SupersetDatasetInfo created = new SupersetDatasetInfo();
        created.setId(100L);
        created.setDatabaseId(10L);
        created.setTableName("supersonic_model_1_modelA");
        SupersetDatasetColumn column1 = new SupersetDatasetColumn();
        column1.setId(1L);
        column1.setColumnName("visit_date");
        SupersetDatasetColumn column2 = new SupersetDatasetColumn();
        column2.setId(2L);
        column2.setColumnName("stay_hours");
        created.setColumns(Arrays.asList(column1, column2));
        when(client.fetchDataset(100L)).thenReturn(created);

        ModelResp modelResp = buildModelResp("select * from new_table");
        Measure measure = new Measure("停留时长", "stay_hours", "stay_hours", "SUM", 1);
        ModelDetail detail = modelResp.getModelDetail();
        detail.setMeasures(Collections.singletonList(measure));
        modelResp.setModelDetail(detail);
        when(modelService.getModelList(any())).thenReturn(Collections.singletonList(modelResp));

        DimSchemaResp dim = new DimSchemaResp();
        dim.setBizName("visit_date");
        dim.setName("访问日期");
        dim.setExpr("visit_date");
        dim.setType(DimensionType.partition_time);
        dim.setTypeParams(new DimensionTimeTypeParams());
        Map<String, Object> dimExt = new HashMap<>();
        dimExt.put(DimensionConstants.DIMENSION_TIME_FORMAT, "yyyy-MM-dd");
        dim.setExt(dimExt);

        MetricDefineByMeasureParams params = new MetricDefineByMeasureParams();
        params.setMeasures(Collections.singletonList(measure));
        params.setExpr("stay_hours");

        MetricSchemaResp metric = new MetricSchemaResp();
        metric.setBizName("stay_sum");
        metric.setName("停留时长");
        metric.setMetricDefineType(MetricDefineType.MEASURE);
        metric.setMetricDefineByMeasureParams(params);

        ModelSchemaResp schemaResp = new ModelSchemaResp();
        schemaResp.setId(1L);
        schemaResp.setDimensions(Collections.singletonList(dim));
        schemaResp.setMetrics(Collections.singletonList(metric));

        when(schemaService.fetchModelSchemaResps(any()))
                .thenReturn(Collections.singletonList(schemaResp));

        SupersetSyncService service = new SupersetSyncService(client, properties, databaseService,
                modelService, schemaService, dataSetService);
        SupersetSyncResult result =
                service.triggerDatasetSyncByModelIds(Set.of(1L), SupersetSyncTrigger.MANUAL);

        Assert.assertTrue(result.isSuccess());
        ArgumentCaptor<SupersetDatasetInfo> captor =
                ArgumentCaptor.forClass(SupersetDatasetInfo.class);
        verify(client, times(1)).updateDataset(eq(100L), captor.capture());
        SupersetDatasetInfo datasetInfo = captor.getValue();
        Assert.assertEquals(datasetInfo.getMainDttmCol(), "visit_date");
        SupersetDatasetColumn mappedColumn = datasetInfo.getColumns().stream()
                .filter(column -> "visit_date".equals(column.getColumnName())).findFirst()
                .orElse(null);
        Assert.assertNotNull(mappedColumn);
        Assert.assertEquals(mappedColumn.getPythonDateFormat(), "%Y-%m-%d");
        SupersetDatasetMetric mappedMetric = datasetInfo.getMetrics().stream()
                .filter(item -> "stay_sum".equals(item.getMetricName())).findFirst().orElse(null);
        Assert.assertNotNull(mappedMetric);
        Assert.assertTrue(mappedMetric.getExpression().toUpperCase().contains("SUM"));
    }

    @Test
    void syncDatasetsSkipsWhenSqlMissing() {
        SupersetSyncClient client = Mockito.mock(SupersetSyncClient.class);
        DatabaseService databaseService = Mockito.mock(DatabaseService.class);
        ModelService modelService = Mockito.mock(ModelService.class);
        SchemaService schemaService = Mockito.mock(SchemaService.class);
        DataSetService dataSetService = Mockito.mock(DataSetService.class);
        SupersetSyncProperties properties = buildProperties();

        DatabaseResp databaseResp = buildDatabaseResp();
        when(databaseService.getDatabaseList(User.getDefaultUser()))
                .thenReturn(Collections.singletonList(databaseResp));
        when(databaseService.getDatabase(eq(1L), any(User.class))).thenReturn(databaseResp);

        SupersetDatabaseInfo supersetDatabase = new SupersetDatabaseInfo();
        supersetDatabase.setId(10L);
        supersetDatabase.setName("supersonic_db_1_demo");
        when(client.listDatabases()).thenReturn(Collections.singletonList(supersetDatabase));
        when(client.listDatasets()).thenReturn(Collections.emptyList());

        ModelResp modelResp = buildModelResp(null);
        when(modelService.getModelList(any())).thenReturn(Collections.singletonList(modelResp));

        SupersetSyncService service = new SupersetSyncService(client, properties, databaseService,
                modelService, schemaService, dataSetService);
        SupersetSyncResult result =
                service.triggerDatasetSyncByModelIds(Set.of(1L), SupersetSyncTrigger.MANUAL);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(result.getStats().getSkipped(), 1);
        verify(client, times(0)).createDataset(any());
    }

    @Test
    void syncDatabasesReturnsFailureOnClientError() {
        SupersetSyncClient client = Mockito.mock(SupersetSyncClient.class);
        DatabaseService databaseService = Mockito.mock(DatabaseService.class);
        ModelService modelService = Mockito.mock(ModelService.class);
        SchemaService schemaService = Mockito.mock(SchemaService.class);
        DataSetService dataSetService = Mockito.mock(DataSetService.class);
        SupersetSyncProperties properties = buildProperties();

        DatabaseResp databaseResp = buildDatabaseResp();
        when(databaseService.getDatabaseList(User.getDefaultUser()))
                .thenReturn(Collections.singletonList(databaseResp));
        when(client.listDatabases()).thenReturn(Collections.emptyList());
        when(client.createDatabase(any())).thenThrow(new RuntimeException("boom"));

        SupersetSyncService service = new SupersetSyncService(client, properties, databaseService,
                modelService, schemaService, dataSetService);
        SupersetSyncResult result =
                service.triggerDatabaseSync(Collections.emptySet(), SupersetSyncTrigger.MANUAL);

        Assert.assertFalse(result.isSuccess());
    }

    @Test
    void syncDatabasesIgnoresDuplicateCreateOnSuperset6() {
        SupersetSyncClient client = Mockito.mock(SupersetSyncClient.class);
        DatabaseService databaseService = Mockito.mock(DatabaseService.class);
        ModelService modelService = Mockito.mock(ModelService.class);
        SchemaService schemaService = Mockito.mock(SchemaService.class);
        DataSetService dataSetService = Mockito.mock(DataSetService.class);
        SupersetSyncProperties properties = buildProperties();

        DatabaseResp databaseResp = buildDatabaseResp();
        when(databaseService.getDatabaseList(User.getDefaultUser()))
                .thenReturn(Collections.singletonList(databaseResp));
        when(client.listDatabases()).thenReturn(Collections.emptyList());
        when(client.getSupersetVersion()).thenReturn("6.0.0");
        when(client.createDatabase(any())).thenThrow(buildDuplicateException(
                "{\"message\":{\"database_name\":\"A database with the same name already exists.\"}}"));

        SupersetSyncService service = new SupersetSyncService(client, properties, databaseService,
                modelService, schemaService, dataSetService);
        SupersetSyncResult result =
                service.triggerDatabaseSync(Collections.emptySet(), SupersetSyncTrigger.MANUAL);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(result.getStats().getSkipped(), 1);
        Assert.assertEquals(result.getStats().getFailed(), 0);
    }

    @Test
    void syncDatasetsIgnoresDuplicateCreateOnSuperset6() {
        SupersetSyncClient client = Mockito.mock(SupersetSyncClient.class);
        DatabaseService databaseService = Mockito.mock(DatabaseService.class);
        ModelService modelService = Mockito.mock(ModelService.class);
        SchemaService schemaService = Mockito.mock(SchemaService.class);
        DataSetService dataSetService = Mockito.mock(DataSetService.class);
        SupersetSyncProperties properties = buildProperties();

        DatabaseResp databaseResp = buildDatabaseResp();
        when(databaseService.getDatabaseList(User.getDefaultUser()))
                .thenReturn(Collections.singletonList(databaseResp));
        when(databaseService.getDatabase(eq(1L), any(User.class))).thenReturn(databaseResp);

        SupersetDatabaseInfo supersetDatabase = new SupersetDatabaseInfo();
        supersetDatabase.setId(10L);
        supersetDatabase.setName("supersonic_db_1_demo");
        when(client.listDatabases()).thenReturn(Collections.singletonList(supersetDatabase));
        when(client.listDatasets()).thenReturn(Collections.emptyList());
        when(client.getSupersetVersion()).thenReturn("6.0.0");
        when(client.createDataset(any())).thenThrow(buildDuplicateException(
                "{\"message\":{\"table_name\":\"A dataset with the same name already exists.\"}}"));
        when(schemaService.fetchModelSchemaResps(any())).thenReturn(Collections.emptyList());

        ModelResp modelResp = buildModelResp("select * from new_table");
        when(modelService.getModelList(any())).thenReturn(Collections.singletonList(modelResp));

        SupersetSyncService service = new SupersetSyncService(client, properties, databaseService,
                modelService, schemaService, dataSetService);
        SupersetSyncResult result =
                service.triggerDatasetSyncByModelIds(Set.of(1L), SupersetSyncTrigger.MANUAL);

        Assert.assertTrue(result.isSuccess());
        Assert.assertEquals(result.getStats().getSkipped(), 1);
        Assert.assertEquals(result.getStats().getFailed(), 0);
    }

    @Test
    void resolveDatasetByDataSetIdUsesIncludeAllModel() {
        SupersetSyncClient client = Mockito.mock(SupersetSyncClient.class);
        DatabaseService databaseService = Mockito.mock(DatabaseService.class);
        ModelService modelService = Mockito.mock(ModelService.class);
        SchemaService schemaService = Mockito.mock(SchemaService.class);
        DataSetService dataSetService = Mockito.mock(DataSetService.class);
        SupersetSyncProperties properties = buildProperties();

        DataSetResp dataSetResp = new DataSetResp();
        dataSetResp.setId(11L);
        DataSetModelConfig modelConfig = new DataSetModelConfig();
        modelConfig.setId(5L);
        modelConfig.setIncludesAll(true);
        DataSetDetail detail = new DataSetDetail();
        detail.setDataSetModelConfigs(Collections.singletonList(modelConfig));
        dataSetResp.setDataSetDetail(detail);
        when(dataSetService.getDataSet(11L)).thenReturn(dataSetResp);

        DatabaseResp databaseResp = buildDatabaseResp();
        when(databaseService.getDatabaseList(User.getDefaultUser()))
                .thenReturn(Collections.singletonList(databaseResp));
        when(databaseService.getDatabase(eq(1L), any(User.class))).thenReturn(databaseResp);

        SupersetDatabaseInfo supersetDatabase = new SupersetDatabaseInfo();
        supersetDatabase.setId(10L);
        supersetDatabase.setName("supersonic_db_1_demo");
        when(client.listDatabases()).thenReturn(Collections.singletonList(supersetDatabase));

        when(client.listDatasets()).thenReturn(Collections.emptyList());
        when(client.createDataset(any())).thenReturn(101L);

        ModelResp modelResp = buildModelResp("select * from new_table");
        modelResp.setId(5L);
        modelResp.setName("model5");
        when(modelService.getModel(5L)).thenReturn(modelResp);

        SupersetSyncService service = new SupersetSyncService(client, properties, databaseService,
                modelService, schemaService, dataSetService);
        SupersetDatasetInfo datasetInfo = service.resolveDatasetByDataSetId(11L);

        Assert.assertNotNull(datasetInfo);
        Assert.assertEquals(datasetInfo.getId(), Long.valueOf(101L));
        verify(modelService, times(1)).getModel(5L);
    }

    @Test
    void syncDatabasesFiltersUnsupportedJdbcParams() {
        SupersetSyncClient client = Mockito.mock(SupersetSyncClient.class);
        DatabaseService databaseService = Mockito.mock(DatabaseService.class);
        ModelService modelService = Mockito.mock(ModelService.class);
        SchemaService schemaService = Mockito.mock(SchemaService.class);
        DataSetService dataSetService = Mockito.mock(DataSetService.class);
        SupersetSyncProperties properties = buildProperties();

        DatabaseResp databaseResp = DatabaseResp.builder().id(1L).name("demo").type("POSTGRESQL")
                .url("jdbc:postgresql://localhost:5432/demo?stringtype=unspecified&sslmode=disable")
                .username("user").password("pass").database("demo").schema("public").build();
        when(databaseService.getDatabaseList(User.getDefaultUser()))
                .thenReturn(Collections.singletonList(databaseResp));
        when(client.listDatabases()).thenReturn(Collections.emptyList());
        when(client.createDatabase(any())).thenReturn(10L);

        SupersetSyncService service = new SupersetSyncService(client, properties, databaseService,
                modelService, schemaService, dataSetService);
        SupersetSyncResult result =
                service.triggerDatabaseSync(Collections.emptySet(), SupersetSyncTrigger.MANUAL);

        Assert.assertTrue(result.isSuccess());
        ArgumentCaptor<SupersetDatabaseInfo> captor =
                ArgumentCaptor.forClass(SupersetDatabaseInfo.class);
        verify(client, times(1)).createDatabase(captor.capture());
        SupersetDatabaseInfo databaseInfo = captor.getValue();
        Assert.assertNotNull(databaseInfo);
        Assert.assertFalse(databaseInfo.getSqlalchemyUri().contains("stringtype"));
        Assert.assertTrue(databaseInfo.getSqlalchemyUri().contains("sslmode=disable"));
    }

    private SupersetSyncProperties buildProperties() {
        SupersetSyncProperties properties = new SupersetSyncProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://localhost:8088");
        return properties;
    }

    private DatabaseResp buildDatabaseResp() {
        return DatabaseResp.builder().id(1L).name("demo").type("POSTGRESQL")
                .url("jdbc:postgresql://localhost:5432/demo").username("user").password("pass")
                .database("demo").schema("public").build();
    }

    private ModelResp buildModelResp(String sql) {
        ModelDetail detail = new ModelDetail();
        detail.setSqlQuery(sql);
        detail.setDbType("POSTGRESQL");
        ModelResp modelResp = new ModelResp();
        modelResp.setId(1L);
        modelResp.setName("modelA");
        modelResp.setDatabaseId(1L);
        modelResp.setModelDetail(detail);
        return modelResp;
    }

    private HttpClientErrorException buildDuplicateException(String body) {
        return HttpClientErrorException.create(HttpStatus.UNPROCESSABLE_ENTITY,
                "Unprocessable Entity", HttpHeaders.EMPTY, body.getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8);
    }
}
