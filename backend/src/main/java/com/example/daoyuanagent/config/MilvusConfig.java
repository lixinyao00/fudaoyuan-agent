package com.example.daoyuanagent.config;

import io.milvus.client.MilvusClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.index.CreateIndexParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MilvusConfig {
    private static final Logger log = LoggerFactory.getLogger(MilvusConfig.class);

    @Value("${milvus.host:localhost}")
    private String host;

    @Value("${milvus.port:19530}")
    private int port;

    @Value("${milvus.collection.name:student_handbook}")
    private String collectionName;

    @Value("${rag.chat.use.vector.search:false}")
    private boolean useVectorSearch;

    @Bean
    public MilvusClient milvusClient() {
        if (!useVectorSearch) {
            log.info("向量检索已禁用，跳过Milvus初始化");
            return null;
        }

        try {
            ConnectParam connectParam = ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .build();
            MilvusClient client = new MilvusServiceClient(connectParam);
            
            // 初始化集合
            initCollection(client);
            
            log.info("Milvus连接成功: {}:{}", host, port);
            return client;
        } catch (Exception e) {
            log.warn("Milvus连接失败，向量检索将不可用: {}", e.getMessage());
            return null;
        }
    }

    private void initCollection(MilvusClient client) {
        try {
            // 检查集合是否存在
            R<Boolean> hasCollection = client.hasCollection(
                    HasCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );
            
            if (hasCollection.getData()) {
                log.info("Milvus集合已存在: {}", collectionName);
                return;
            }

            // 定义字段
            FieldType idField = FieldType.newBuilder()
                    .withName("id")
                    .withDataType(DataType.Int64)
                    .withPrimaryKey(true)
                    .withAutoID(true)
                    .build();

            FieldType contentField = FieldType.newBuilder()
                    .withName("content")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(2000)
                    .build();

            FieldType pageNumberField = FieldType.newBuilder()
                    .withName("page_number")
                    .withDataType(DataType.Int32)
                    .build();

            FieldType sourceField = FieldType.newBuilder()
                    .withName("source")
                    .withDataType(DataType.VarChar)
                    .withMaxLength(500)
                    .build();

            FieldType vectorField = FieldType.newBuilder()
                    .withName("vector")
                    .withDataType(DataType.FloatVector)
                    .withDimension(1536) // text-embedding-v4维度
                    .build();

            // 创建集合
            CreateCollectionParam createParam = CreateCollectionParam.newBuilder()
                    .withCollectionName(collectionName)
                    .addFieldType(idField)
                    .addFieldType(contentField)
                    .addFieldType(pageNumberField)
                    .addFieldType(sourceField)
                    .addFieldType(vectorField)
                    .build();

            client.createCollection(createParam);

            // 创建索引
            CreateIndexParam indexParam = CreateIndexParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withFieldName("vector")
                    .withIndexType(IndexType.HNSW)
                    .withMetricType(MetricType.COSINE)
                    .withExtraParam("{\"M\":16,\"efConstruction\":256}")
                    .build();

            client.createIndex(indexParam);
            
            log.info("Milvus集合创建成功: {}", collectionName);
        } catch (Exception e) {
            log.error("Milvus集合初始化失败", e);
        }
    }
}