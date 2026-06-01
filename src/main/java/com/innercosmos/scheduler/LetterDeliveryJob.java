package com.innercosmos.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.innercosmos.entity.SlowLetter;
import com.innercosmos.mapper.SlowLetterMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class LetterDeliveryJob {
    private static final Logger log = LoggerFactory.getLogger(LetterDeliveryJob.class);
    private final SlowLetterMapper letterMapper;

    public LetterDeliveryJob(SlowLetterMapper letterMapper) {
        this.letterMapper = letterMapper;
    }

    @Scheduled(fixedRate = 60000)
    public void deliverArrivedLetters() {
        QueryWrapper<SlowLetter> query = new QueryWrapper<>();
        query.eq("status", "SENT")
                .le("estimated_arrival_at", LocalDateTime.now());
        List<SlowLetter> letters = letterMapper.selectList(query);
        for (SlowLetter letter : letters) {
            letter.status = "DELIVERED";
            letter.deliveredAt = LocalDateTime.now();
            letterMapper.updateById(letter);
            log.info("Slow letter {} delivered", letter.id);
        }
    }
}
