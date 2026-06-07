package com.innercosmos.service;

import com.innercosmos.ai.self.AuroraConstitutionVO;
import java.util.List;

public interface AuroraConstitutionService {
    /** Get the single Constitution record (never null after init) */
    AuroraConstitutionVO get();

    /** Get Constitution as prompt-ready string block */
    String toPromptBlock();

    /** Get 4 hard boundaries as comma-separated string */
    String getHardBoundariesString();

    /** Get product internal rights as list */
    List<String> getProductRights();
}