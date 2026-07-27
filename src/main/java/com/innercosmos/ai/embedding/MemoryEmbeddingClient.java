package com.innercosmos.ai.embedding;

import java.util.ArrayList;
import java.util.List;

public interface MemoryEmbeddingClient {
    boolean available();
    default String providerName() { return "unknown"; }
    String modelName();
    String modelVersion();
    int dimensions();
    float[] embed(String text);

    /**
     * Embed several texts in as few provider round-trips as the implementation supports. The
     * default keeps every existing implementation (and test fake) working unchanged by looping;
     * providers whose API accepts an input array should override this with one request, which is
     * what makes a cold index warm up in seconds instead of minutes.
     *
     * @return one vector per input, in input order.
     */
    default List<float[]> embedBatch(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) vectors.add(embed(text));
        return vectors;
    }
}
