package com.innercosmos.asr;

import org.springframework.stereotype.Component;

@Component
public class MockAsrClient implements AsrClient {
    @Override
    public AsrResult transcribe(byte[] audioBytes, String hintText) {
        AsrResult result = new AsrResult();
        result.text = hintText == null || hintText.isBlank() ? "今天有点累，但我想把事情慢慢说清楚。" : hintText;
        result.audioDurationSec = Math.max(3, result.text.length() / 3);
        result.speechRate = Math.max(1.0, result.text.length() / (double) result.audioDurationSec);
        result.pauseCount = result.text.contains("，") ? 2 : 1;
        result.longPauseCount = result.text.contains("不知道") ? 1 : 0;
        result.inputConfidence = 0.86;
        return result;
    }
}
