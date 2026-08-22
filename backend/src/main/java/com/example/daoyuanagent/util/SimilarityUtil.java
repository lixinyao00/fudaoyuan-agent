package com.example.daoyuanagent.util;

import java.util.List;

public class SimilarityUtil {
    public static double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a == null || b == null || a.size() != b.size()) return 0.0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }

    public static List<Double> parseEmbedding(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            String[] parts = json.replace("[", "").replace("]", "").split(",");
            return java.util.Arrays.stream(parts)
                    .map(s -> Double.parseDouble(s.trim()))
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
