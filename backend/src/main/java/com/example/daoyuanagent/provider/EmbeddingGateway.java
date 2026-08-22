package com.example.daoyuanagent.provider;

import java.util.List;

public interface EmbeddingGateway {
    List<Double> embed(String text);
    String getProviderName();
}
