package com.study.ragagent.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.grpc.DataType;
import io.milvus.grpc.MutationResult;
import io.milvus.grpc.SearchResults;
import io.milvus.param.*;
import io.milvus.param.collection.*;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class MilvusService {

    @Value("${milvus.enabled:false}")
    private boolean enabled;

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;

    @Value("${milvus.collection-name}")
    private String collectionName;

    @Value("${milvus.dimension}")
    private int dimension;

    private MilvusServiceClient client;

    @PostConstruct
    public void init() {
        if (!enabled) {
            System.out.println("⚠️  Milvus 已关闭");
            return;
        }

        try {
            client = new MilvusServiceClient(
                    ConnectParam.newBuilder()
                            .withHost(host)
                            .withPort(port)
                            .build()
            );
            createCollectionIfNotExists();
            System.out.println("✅ Milvus 连接成功: " + host + ":" + port);
        } catch (Exception e) {
            System.err.println("⚠️  Milvus 连接失败: " + e.getMessage());
            client = null;
        }
    }

    public boolean isReady() {
        return enabled && client != null;
    }

    private void createCollectionIfNotExists() {
        R<Boolean> has = client.hasCollection(
                HasCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        );
        if (Boolean.TRUE.equals(has.getData())) {
            client.loadCollection(
                    LoadCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );
            return;
        }

        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build();

        FieldType vectorField = FieldType.newBuilder()
                .withName("vector")
                .withDataType(DataType.FloatVector)
                .withDimension(dimension)
                .build();

        FieldType chunkIdField = FieldType.newBuilder()
                .withName("chunk_id")
                .withDataType(DataType.Int64)
                .build();

        client.createCollection(
                CreateCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .addFieldType(idField)
                        .addFieldType(vectorField)
                        .addFieldType(chunkIdField)
                        .build()
        );

        client.createIndex(
                CreateIndexParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFieldName("vector")
                        .withIndexType(IndexType.HNSW)
                        .withMetricType(MetricType.COSINE)
                        .withExtraParam("{\"M\":16,\"efConstruction\":256}")
                        .build()
        );

        client.loadCollection(
                LoadCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        );
    }

    public void insert(long chunkId, float[] vector) {
        if (!isReady()) {
            throw new IllegalStateException("Milvus 未就绪");
        }

        List<Long> chunkIds = Collections.singletonList(chunkId);
        List<List<Float>> vectors = Collections.singletonList(toFloatList(vector));

        List<InsertParam.Field> fields = new ArrayList<>();
        fields.add(new InsertParam.Field("chunk_id", chunkIds));
        fields.add(new InsertParam.Field("vector", vectors));

        R<MutationResult> result = client.insert(
                InsertParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withFields(fields)
                        .build()
        );
        if (result.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus 插入失败: " + result.getMessage());
        }
    }

    public List<Long> search(float[] queryVector, int topK) {
        if (!isReady()) {
            return Collections.emptyList();
        }

        List<List<Float>> vectors = Collections.singletonList(toFloatList(queryVector));

        R<SearchResults> response = client.search(
                SearchParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withMetricType(MetricType.COSINE)
                        .withOutFields(Collections.singletonList("chunk_id"))
                        .withTopK(topK)
                        .withVectors(vectors)
                        .withVectorFieldName("vector")
                        .withConsistencyLevel(ConsistencyLevelEnum.BOUNDED)
                        .withParams("{\"ef\":64}")
                        .build()
        );

        if (response.getStatus() != R.Status.Success.getCode()) {
            throw new RuntimeException("Milvus 搜索失败: " + response.getMessage());
        }

        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<Long> chunkIds = new ArrayList<>();
        List<SearchResultsWrapper.IDScore> scores = wrapper.getIDScore(0);
        for (SearchResultsWrapper.IDScore score : scores) {
            Object chunkId = score.get("chunk_id");
            if (chunkId instanceof Number) {
                chunkIds.add(((Number) chunkId).longValue());
            }
        }
        return chunkIds;
    }

    public void deleteByChunkId(long chunkId) {
        if (!isReady()) {
            return;
        }
        client.delete(
                DeleteParam.newBuilder()
                        .withCollectionName(collectionName)
                        .withExpr("chunk_id == " + chunkId)
                        .build()
        );
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }
}
