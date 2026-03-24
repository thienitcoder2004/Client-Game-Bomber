package com.bomberserver.backend.service;

import com.bomberserver.backend.document.CharacterProfileDocument;
import com.bomberserver.backend.document.MatchHistoryDocument;
import com.bomberserver.backend.document.UserAccountDocument;
import com.bomberserver.backend.dto.admin.AdminMatchResponse;
import com.bomberserver.backend.dto.admin.AdminUserResponse;
import com.bomberserver.backend.repository.CharacterProfileRepository;
import com.bomberserver.backend.repository.MatchHistoryRepository;
import com.bomberserver.backend.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class AdminService {

    private final UserAccountRepository userAccountRepository;
    private final CharacterProfileRepository characterProfileRepository;
    private final MatchHistoryRepository matchHistoryRepository;

    public AdminService(
            UserAccountRepository userAccountRepository,
            CharacterProfileRepository characterProfileRepository,
            MatchHistoryRepository matchHistoryRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.characterProfileRepository = characterProfileRepository;
        this.matchHistoryRepository = matchHistoryRepository;
    }

    public List<AdminUserResponse> getUsers() {
        return userAccountRepository.findAll()
                .stream()
                .sorted(
                        Comparator.comparing(
                                UserAccountDocument::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ).reversed()
                )
                .map(this::toAdminUserResponse)
                .toList();
    }

    public AdminUserResponse lockUser(String userId, String currentAdminId) {
        UserAccountDocument user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user"));

        if (user.getEmail() != null && user.getEmail().equalsIgnoreCase("admin@gmail.com")) {
            throw new IllegalArgumentException("Không thể khóa tài khoản admin mặc định");
        }

        if (user.getId() != null && user.getId().equals(currentAdminId)) {
            throw new IllegalArgumentException("Bạn không thể tự khóa chính mình");
        }

        user.setActive(false);
        user = userAccountRepository.save(user);
        return toAdminUserResponse(user);
    }

    public AdminUserResponse unlockUser(String userId) {
        UserAccountDocument user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user"));

        user.setActive(true);
        user = userAccountRepository.save(user);
        return toAdminUserResponse(user);
    }

    public List<AdminMatchResponse> getMatches() {
        return matchHistoryRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(
                        MatchHistoryDocument::getEndedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .map(this::toAdminMatchResponse)
                .toList();
    }

    private AdminUserResponse toAdminUserResponse(UserAccountDocument user) {
        Optional<CharacterProfileDocument> profileOpt = user.getId() == null
                ? Optional.empty()
                : characterProfileRepository.findByUserId(user.getId());

        String characterName = profileOpt.map(CharacterProfileDocument::getCharacterName).orElse("");
        String gender = profileOpt.map(CharacterProfileDocument::getGender).orElse("");

        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                characterName,
                gender,
                user.getRole() == null ? "USER" : user.getRole(),
                !Boolean.FALSE.equals(user.getActive()),
                user.getCreatedAt()
        );
    }

    private AdminMatchResponse toAdminMatchResponse(MatchHistoryDocument match) {
        List<String> playerNames = match.getPlayers() == null
                ? List.of()
                : match.getPlayers().stream()
                .map(MatchHistoryDocument.PlayerMatchResult::getCharacterName)
                .filter(Objects::nonNull)
                .filter(name -> !name.isBlank())
                .toList();

        return new AdminMatchResponse(
                match.getId(),
                match.getRoomCode(),
                match.getWinnerUserId(),
                match.getWinnerCharacterName(),
                match.getStartedAt(),
                match.getEndedAt(),
                playerNames.size(),
                playerNames
        );
    }
}