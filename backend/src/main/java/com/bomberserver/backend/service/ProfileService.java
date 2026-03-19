package com.bomberserver.backend.service;

import com.bomberserver.backend.document.CharacterProfileDocument;
import com.bomberserver.backend.document.UserAccountDocument;
import com.bomberserver.backend.dto.profile.ProfileResponse;
import com.bomberserver.backend.dto.profile.UpdateProfileRequest;
import com.bomberserver.backend.repository.CharacterProfileRepository;
import com.bomberserver.backend.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class ProfileService {

    private final UserAccountRepository userAccountRepository;
    private final CharacterProfileRepository characterProfileRepository;

    public ProfileService(
            UserAccountRepository userAccountRepository,
            CharacterProfileRepository characterProfileRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.characterProfileRepository = characterProfileRepository;
    }

    public ProfileResponse getMyProfile(String userId) {
        UserAccountDocument user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        CharacterProfileDocument profile = characterProfileRepository.findByUserId(userId)
                .orElseGet(() -> characterProfileRepository.save(new CharacterProfileDocument(userId)));

        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                profile.getCharacterName(),
                profile.getGender(),
                profile.getAvatarCode(),
                profile.isProfileCompleted()
        );
    }

    public ProfileResponse updateMyProfile(String userId, UpdateProfileRequest request) {
        UserAccountDocument user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User không tồn tại"));

        CharacterProfileDocument profile = characterProfileRepository.findByUserId(userId)
                .orElseGet(() -> characterProfileRepository.save(new CharacterProfileDocument(userId)));

        String nextCharacterName = request.characterName.trim();
        String nextGender = request.gender.trim().toUpperCase();
        String nextAvatarCode = request.avatarCode.trim();

        if (!nextCharacterName.equalsIgnoreCase(profile.getCharacterName())
                && characterProfileRepository.existsByCharacterNameIgnoreCase(nextCharacterName)) {
            throw new IllegalArgumentException("Tên nhân vật đã tồn tại");
        }

        profile.setCharacterName(nextCharacterName);
        profile.setGender(nextGender);
        profile.setAvatarCode(nextAvatarCode);
        profile.setUpdatedAt(Instant.now());

        profile = characterProfileRepository.save(profile);

        return new ProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                profile.getCharacterName(),
                profile.getGender(),
                profile.getAvatarCode(),
                profile.isProfileCompleted()
        );
    }
}