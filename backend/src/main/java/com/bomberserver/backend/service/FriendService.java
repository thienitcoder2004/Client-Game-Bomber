package com.bomberserver.backend.service;

import com.bomberserver.backend.document.CharacterProfileDocument;
import com.bomberserver.backend.document.FriendChatMessageDocument;
import com.bomberserver.backend.document.FriendDocument;
import com.bomberserver.backend.document.UserAccountDocument;
import com.bomberserver.backend.dto.common.MessageResponse;
import com.bomberserver.backend.dto.friend.*;
import com.bomberserver.backend.repository.CharacterProfileRepository;
import com.bomberserver.backend.repository.FriendChatMessageRepository;
import com.bomberserver.backend.repository.FriendRepository;
import com.bomberserver.backend.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FriendService {

    private final FriendRepository friendRepository;
    private final FriendChatMessageRepository friendChatMessageRepository;
    private final UserAccountRepository userAccountRepository;
    private final CharacterProfileRepository characterProfileRepository;
    private final FriendChatSocketService friendChatSocketService;

    public FriendService(
            FriendRepository friendRepository,
            FriendChatMessageRepository friendChatMessageRepository,
            UserAccountRepository userAccountRepository,
            CharacterProfileRepository characterProfileRepository,
            FriendChatSocketService friendChatSocketService
    ) {
        this.friendRepository = friendRepository;
        this.friendChatMessageRepository = friendChatMessageRepository;
        this.userAccountRepository = userAccountRepository;
        this.characterProfileRepository = characterProfileRepository;
        this.friendChatSocketService = friendChatSocketService;
    }

    public List<FriendSearchItemResponse> searchUsers(String currentUserId, String keywordRaw) {
        String keyword = keywordRaw == null ? "" : keywordRaw.trim();
        if (keyword.isBlank()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> userIds = new LinkedHashSet<>();

        for (UserAccountDocument user : userAccountRepository.findTop20ByUsernameContainingIgnoreCase(keyword)) {
            userIds.add(user.getId());
        }

        for (CharacterProfileDocument profile : characterProfileRepository.findTop20ByCharacterNameContainingIgnoreCase(keyword)) {
            if (profile.getUserId() != null && !profile.getUserId().isBlank()) {
                userIds.add(profile.getUserId());
            }
        }

        return userIds.stream()
                .filter(userId -> !userId.equals(currentUserId))
                .limit(20)
                .map(userId -> buildSearchItem(currentUserId, userId))
                .filter(Objects::nonNull)
                .toList();
    }

    public MessageResponse sendFriendRequest(String currentUserId, String targetUserId) {
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalArgumentException("Thiếu người cần kết bạn");
        }

        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Không thể tự kết bạn với chính mình");
        }

        userAccountRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Người chơi không tồn tại"));

        Optional<FriendDocument> relationOpt = findRelation(currentUserId, targetUserId);
        if (relationOpt.isPresent()) {
            FriendDocument relation = relationOpt.get();
            if ("ACCEPTED".equals(relation.getStatus())) {
                throw new IllegalArgumentException("Hai người đã là bạn bè");
            }

            if (currentUserId.equals(relation.getRequesterId())) {
                throw new IllegalArgumentException("Bạn đã gửi lời mời trước đó rồi");
            }

            throw new IllegalArgumentException("Người này đã gửi lời mời cho bạn, hãy vào mục lời mời để chấp nhận");
        }

        String[] pair = sortPair(currentUserId, targetUserId);
        FriendDocument friend = new FriendDocument();
        friend.setUserAId(pair[0]);
        friend.setUserBId(pair[1]);
        friend.setRequesterId(currentUserId);
        friend.setAddresseeId(targetUserId);
        friend.setStatus("PENDING");
        friend.setCreatedAt(Instant.now());
        friend.setUpdatedAt(Instant.now());
        friendRepository.save(friend);

        return new MessageResponse("Đã gửi lời mời kết bạn");
    }

    public List<FriendRequestResponse> getIncomingRequests(String currentUserId) {
        return friendRepository.findByAddresseeIdAndStatusOrderByCreatedAtDesc(currentUserId, "PENDING")
                .stream()
                .map(item -> new FriendRequestResponse(
                        item.getId(),
                        "INCOMING",
                        toUserSummary(item.getRequesterId()),
                        item.getCreatedAt()
                ))
                .toList();
    }

    public List<FriendRequestResponse> getOutgoingRequests(String currentUserId) {
        return friendRepository.findByRequesterIdAndStatusOrderByCreatedAtDesc(currentUserId, "PENDING")
                .stream()
                .map(item -> new FriendRequestResponse(
                        item.getId(),
                        "OUTGOING",
                        toUserSummary(item.getAddresseeId()),
                        item.getCreatedAt()
                ))
                .toList();
    }

    public MessageResponse acceptRequest(String currentUserId, String requestId) {
        FriendDocument request = friendRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lời mời"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalArgumentException("Lời mời này không còn hợp lệ");
        }

        if (!currentUserId.equals(request.getAddresseeId())) {
            throw new IllegalArgumentException("Bạn không có quyền chấp nhận lời mời này");
        }

        request.setStatus("ACCEPTED");
        request.setAcceptedAt(Instant.now());
        request.setUpdatedAt(Instant.now());
        friendRepository.save(request);

        return new MessageResponse("Đã chấp nhận lời mời kết bạn");
    }

    public MessageResponse rejectRequest(String currentUserId, String requestId) {
        FriendDocument request = friendRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lời mời"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalArgumentException("Lời mời này không còn hợp lệ");
        }

        if (!currentUserId.equals(request.getAddresseeId()) && !currentUserId.equals(request.getRequesterId())) {
            throw new IllegalArgumentException("Bạn không có quyền xóa lời mời này");
        }

        friendRepository.deleteById(requestId);
        return new MessageResponse("Đã xóa lời mời kết bạn");
    }

    public List<FriendListItemResponse> getFriends(String currentUserId) {
        List<FriendDocument> all = new ArrayList<>();
        all.addAll(friendRepository.findByUserAIdAndStatusOrderByUpdatedAtDesc(currentUserId, "ACCEPTED"));
        all.addAll(friendRepository.findByUserBIdAndStatusOrderByUpdatedAtDesc(currentUserId, "ACCEPTED"));

        return all.stream()
                .sorted(Comparator.comparing(FriendDocument::getUpdatedAt).reversed())
                .map(item -> {
                    String friendUserId = currentUserId.equals(item.getUserAId()) ? item.getUserBId() : item.getUserAId();
                    return new FriendListItemResponse(
                            item.getId(),
                            toUserSummary(friendUserId),
                            item.getAcceptedAt()
                    );
                })
                .toList();
    }

    public MessageResponse removeFriend(String currentUserId, String friendUserId) {
        FriendDocument relation = findRelation(currentUserId, friendUserId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy quan hệ bạn bè"));

        if (!"ACCEPTED".equals(relation.getStatus())) {
            throw new IllegalArgumentException("Hai người chưa là bạn bè");
        }

        friendRepository.deleteById(relation.getId());
        return new MessageResponse("Đã xóa bạn bè");
    }

    public List<FriendChatMessageResponse> getChatHistory(String currentUserId, String friendUserId) {
        FriendDocument relation = findRelation(currentUserId, friendUserId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy quan hệ bạn bè"));

        if (!"ACCEPTED".equals(relation.getStatus())) {
            throw new IllegalArgumentException("Chỉ xem chat với bạn bè đã chấp nhận");
        }

        List<FriendChatMessageDocument> messages = friendChatMessageRepository
                .findTop50ByConversationKeyOrderByCreatedAtDesc(FriendChatSocketService.buildConversationKey(currentUserId, friendUserId));

        Collections.reverse(messages);

        return messages.stream()
                .map(item -> new FriendChatMessageResponse(
                        item.getId(),
                        item.getSenderId(),
                        item.getReceiverId(),
                        item.getContent(),
                        item.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    private FriendSearchItemResponse buildSearchItem(String currentUserId, String targetUserId) {
        UserAccountDocument user = userAccountRepository.findById(targetUserId).orElse(null);
        if (user == null) return null;

        CharacterProfileDocument profile = characterProfileRepository.findByUserId(targetUserId)
                .orElseGet(() -> characterProfileRepository.save(new CharacterProfileDocument(targetUserId)));

        Optional<FriendDocument> relation = findRelation(currentUserId, targetUserId);
        String relationshipStatus = "NONE";
        String requestId = null;

        if (relation.isPresent()) {
            FriendDocument item = relation.get();
            requestId = item.getId();

            if ("ACCEPTED".equals(item.getStatus())) {
                relationshipStatus = "FRIEND";
            } else if (currentUserId.equals(item.getRequesterId())) {
                relationshipStatus = "PENDING_OUT";
            } else {
                relationshipStatus = "PENDING_IN";
            }
        }

        return new FriendSearchItemResponse(
                user.getId(),
                user.getUsername(),
                profile.getCharacterName(),
                profile.getAvatarCode(),
                friendChatSocketService.isUserOnline(user.getId()),
                relationshipStatus,
                requestId
        );
    }

    private FriendUserSummaryResponse toUserSummary(String userId) {
        FriendChatSocketService.FriendUserView view = friendChatSocketService.getUserView(userId);
        return new FriendUserSummaryResponse(
                view.userId(),
                view.username(),
                view.characterName(),
                view.avatarCode(),
                view.online()
        );
    }

    private Optional<FriendDocument> findRelation(String userId1, String userId2) {
        String[] pair = sortPair(userId1, userId2);
        return friendRepository.findByUserAIdAndUserBId(pair[0], pair[1]);
    }

    private String[] sortPair(String userId1, String userId2) {
        if (userId1.compareTo(userId2) <= 0) {
            return new String[]{userId1, userId2};
        }
        return new String[]{userId2, userId1};
    }
}