package com.innercosmos.service;

import com.innercosmos.vo.StarfieldSceneVO;

public interface StarfieldExplorerService {
    StarfieldSceneVO explore(Long userId, String mode, String query, String layer, String person);
}
