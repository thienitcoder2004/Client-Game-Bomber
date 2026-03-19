package com.bomberserver.backend.service;

import com.example.bomberserver.document.CharacterProfileDocument;
import com.example.bomberserver.document.UserAccountDocument;
import com.example.bomberserver.dto.profile.ProfileResponse;
import com.example.bomberserver.dto.profile.UpdateProfileRequest;
import com.example.bomberserver.repository.CharacterProfileRepository;
import com.example.bomberserver.repository.UserAccountRepository;
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