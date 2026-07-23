package com.innercosmos.service.impl;

import com.innercosmos.asr.AsrResult;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.VoiceTranscription;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.mapper.VoiceTranscriptionMapper;
import com.innercosmos.service.MemorySettlementService;
import com.innercosmos.service.VoiceTranscriptionService;
import org.springframework.stereotype.Service;

@Service
public class VoiceTranscriptionServiceImpl implements VoiceTranscriptionService {

    private final VoiceTranscriptionMapper transcriptionMapper;
    private final MemorySettlementService memorySettlementService;

    public VoiceTranscriptionServiceImpl(VoiceTranscriptionMapper transcriptionMapper,
                                         MemorySettlementService memorySettlementService) {
        this.transcriptionMapper = transcriptionMapper;
        this.memorySettlementService = memorySettlementService;
    }

    @Override
    public VoiceTranscription create(Long userId, String text, AsrResult asr, String emotionHint) {
        VoiceTranscription vt = new VoiceTranscription();
        vt.userId = userId;
        vt.originalText = text;
        vt.editedText = text;
        vt.audioDurationSec = asr.audioDurationSec;
        vt.speechRate = asr.speechRate;
        vt.pauseCount = asr.pauseCount;
        vt.emotionHint = emotionHint;
        vt.status = "RAW";
        transcriptionMapper.insert(vt);
        return vt;
    }

    @Override
    public VoiceTranscription getOwned(Long id, Long userId) {
        VoiceTranscription vt = transcriptionMapper.selectById(id);
        if (vt == null || !userId.equals(vt.userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "日记转写记录不存在");
        }
        return vt;
    }

    @Override
    public void submitFinal(Long id, Long userId, String finalContent) {
        VoiceTranscription vt = getOwned(id, userId);
        vt.editedText = finalContent;
        vt.status = "SUBMITTED";
        transcriptionMapper.updateById(vt);
        memorySettlementService.settleDiary(userId, finalContent);
    }
}
