package com.bomberserver.backend.service;

import com.example.bomberserver.document.CharacterProfileDocument;
import com.example.bomberserver.document.UserAccountDocument;
import com.example.bomberserver.dto.auth.AuthResponse;
import com.example.bomberserver.dto.auth.LoginRequest;
import com.example.bomberserver.dto.auth.RegisterRequest;
import com.example.bomberserver.repository.CharacterProfileRepository;
import com.example.bomberserver.repository.UserAccountRepository;
import com.example.bomberserver.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final CharacterProfileRepository characterProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            CharacterProfileRepository characterProfileRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.characterProfileRepository = characterProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = request.email.trim().toLowerCase();
        String username = request.username.trim();

        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email đã tồn tại");
        }

        if (userAccountRepository.existsByUsernameIgnoreCase(username)) {
            throw new IllegalArgumentException("Username đã tồn tại");
        }

        UserAccountDocument user = new UserAccountDocument(
                email,
                username,
                passwordEncoder.encode(request.password)
        );
        user = userAccountRepository.save(user);

        // Tạo profile rỗng, chưa chọn nhân vật
        CharacterProfileDocument profile = new CharacterProfileDocument(user.getId());
        profile = characterProfileRepository.save(profile);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getUsername());

        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                profile.getCharacterName(),
                profile.getGender(),
                profile.getAvatarCode(),
                false
        );
    }

    public AuthResponse login(LoginRequest request) {
        String login = request.login.trim();

        UserAccountDocument user = userAccountRepository.findByEmailIgnoreCase(login)
                .or(() -> userAccountRepository.findByUsernameIgnoreCase(login))
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));

        if (!passwordEncoder.matches(request.password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Sai mật khẩu");
        }

        CharacterProfileDocument profile = characterProfileRepository.findByUserId(user.getId())
                .orElseGet(() -> characterProfileRepository.save(new CharacterProfileDocument(user.getId())));

        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getUsername());

        return new AuthResponse(
                token,
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