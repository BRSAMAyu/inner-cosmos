package com.innercosmos.asr;

public interface AsrClient {
    AsrResult transcribe(byte[] audioBytes, String hintText);
}
