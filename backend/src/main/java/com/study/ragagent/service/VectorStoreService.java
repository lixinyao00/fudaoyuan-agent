package com.study.ragagent.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量存储统一入口：优先使用 Milvus
 */
@Service
public class VectorStoreService {

    @Autowired
    private MilvusService milvusService;

    public boolean isReady() {
        return milvusService.isReady();
    }

    public void insert(long chunkId, float[] vector) {
        milvusService.insert(chunkId, vector);
    }

    public List<Long> search(float[] queryVector, int topK) {
        return milvusService.search(queryVector, topK);
    }

    public void deleteByChunkId(long chunkId) {
        milvusService.deleteByChunkId(chunkId);
    }
}
