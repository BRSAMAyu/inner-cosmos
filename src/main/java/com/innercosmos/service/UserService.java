package com.innercosmos.service;

import com.innercosmos.dto.LoginRequest;
import com.innercosmos.dto.RegisterRequest;
import com.innercosmos.entity.User;
import com.innercosmos.entity.UserProfile;
import com.innercosmos.vo.UserProfileVO;

import java.util.Map;

public interface UserService {
    User register(RegisterRequest request);

    User login(LoginRequest request);

    User current(Long userId);

    void updateProfile(Long userId, UserProfileVO profile);

    /** The raw {@code tb_user_profile} row for a user (by user_id, the FK), or {@code null}. */
    UserProfile getProfile(Long userId);

    Map<String, Object> exportData(Long userId);

    void deleteAccount(Long userId);

    /** M-032: change the current user's password (requires the old password). */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * Set (or clear, when {@code provider} is null/blank) the user's default LLM provider
     * preference. Throws if the user has no profile row yet.
     */
    void setPreferredModel(Long userId, String provider);

    /**
     * Upsert this user's TTS voice + Aurora inner-voice (心声) delivery preferences (creates the
     * profile row if this is the user's first preference write, mirroring {@link #updateProfile}).
     * Any {@code null} parameter leaves that field unchanged; callers are responsible for
     * validating {@code preferredTtsVoiceId}/{@code innerVoiceMode} before calling.
     */
    void updateTtsPreferences(Long userId, String preferredTtsVoiceId, Boolean innerVoiceEnabled, String innerVoiceMode);
}
