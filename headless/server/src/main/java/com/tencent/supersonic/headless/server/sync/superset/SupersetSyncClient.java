package com.tencent.supersonic.headless.server.sync.superset;

import java.util.List;

public interface SupersetSyncClient {

    List<SupersetDatabaseInfo> listDatabases();

    SupersetDatabaseInfo fetchDatabase(Long id);

    Long createDatabase(SupersetDatabaseInfo databaseInfo);

    void updateDatabase(Long id, SupersetDatabaseInfo databaseInfo);

    List<SupersetDatasetInfo> listDatasets();

    SupersetDatasetInfo fetchDataset(Long id);

    Long createDataset(SupersetDatasetInfo datasetInfo);

    void updateDataset(Long id, SupersetDatasetInfo datasetInfo);

    void deleteDataset(Long id);

    void deleteDatasetColumn(Long datasetId, Long columnId);

    void deleteDatasetMetric(Long datasetId, Long metricId);
}
