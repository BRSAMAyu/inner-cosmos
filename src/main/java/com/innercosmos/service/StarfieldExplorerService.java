package com.innercosmos.service;

import com.innercosmos.vo.StarfieldSceneVO;

public interface StarfieldExplorerService {
    StarfieldSceneVO explore(Long userId, String mode, String query, String layer, String person);

    /**
     * CP-24: same scene with the emotional encoding switch. When off, gravity-driven sizing
     * is flattened to a neutral constant and the legend says so — the starfield never reads
     * as a psychological score either way (disclaimer always present).
     */
    StarfieldSceneVO explore(Long userId, String mode, String query, String layer, String person,
                             boolean emotionEncoding);
}
