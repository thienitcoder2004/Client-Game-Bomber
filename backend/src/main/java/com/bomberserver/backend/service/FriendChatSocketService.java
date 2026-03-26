package com.bomberserver.backend.service;

import com.bomberserver.backend.document.CharacterProfileDocument;
import com.bomberserver.backend.document.FriendChatMessageDocument;
import com.bomberserver.backend.document.FriendDocument;
import com.bomberserver.backend.document.UserAccountDocument;
import com.bomberserver.backend.dto.common.MessageResponse;
import com.bomberserver.backend.dto.friend.FriendChatMessageResponse;
import com.bomberserver.backend.dto.friend.FriendListItemResponse;
import com.bomberserver.backend.dto.friend.FriendRequestResponse;
import com.bomberserver.backend.dto.friend.FriendSearchItemResponse;
import com.bomberserver.backend.dto.friend.FriendUserSummaryResponse;
import com.bomberserver.backend.repository.CharacterProfileRepository;
import com.bomberserver.backend.repository.FriendChatMessageRepository;
import com.bomberserver.backend.repository.FriendRepository;
import com.bomberserver.backend.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service xử lý toàn bộ nghiệp vụ liên quan đến bạn bè và lịch sử chat.
 *
 * Chức năng chính:
 * - Tìm kiếm người chơi
 * - Gửi lời mời kết bạn
 * - Xem danh sách lời mời đến / đi
 * - Chấp nhận / từ chối lời mời
 * - Lấy danh sách bạn bè
 * - Xóa bạn bè
 * - Lấy lịch sử chat giữa 2 người
 *
 * Ngoài ra service này còn kết hợp với FriendChatSocketService
 * để phát realtime các sự kiện sang frontend mà không cần F5.
 */
@Service
public class FriendService {

    /** Repository quản lý quan hệ bạn bè */
    private final FriendRepository friendRepository;

    /** Repository quản lý lịch sử tin nhắn chat */
    private final FriendChatMessageRepository friendChatMessageRepository;

    /** Repository quản lý tài khoản */
    private final UserAccountRepository userAccountRepository;

    /** Repository quản lý profile nhân vật */
    private final CharacterProfileRepository characterProfileRepository;

    /** Service socket để phát sự kiện realtime */
    private final FriendChatSocketService friendChatSocketService;

    /**
     * Constructor inject dependency.
     */
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

    /**
     * Tìm kiếm user theo keyword.
     *
     * Cách tìm:
     * - tìm theo username
     * - tìm theo characterName
     *
     * Dùng LinkedHashSet để:
     * - không bị trùng userId
     * - vẫn giữ thứ tự thêm vào
     *
     * Kết quả:
     * - loại chính currentUser ra
     * - tối đa 20 user
     * - trả về kèm trạng thái quan hệ với người hiện tại
     *   như FRIEND, PENDING_IN, PENDING_OUT, NONE
     */
    public List<FriendSearchItemResponse> searchUsers(String currentUserId, String keywordRaw) {
        // Chuẩn hóa keyword
        String keyword = keywordRaw == null ? "" : keywordRaw.trim();

        // Nếu keyword rỗng thì không tìm gì
        if (keyword.isBlank()) {
            return Collections.emptyList();
        }

        // Dùng LinkedHashSet để vừa khử trùng lặp vừa giữ thứ tự
        LinkedHashSet<String> userIds = new LinkedHashSet<>();

        // Tìm theo username
        for (UserAccountDocument user : userAccountRepository.findTop20ByUsernameContainingIgnoreCase(keyword)) {
            userIds.add(user.getId());
        }

        // Tìm theo characterName
        for (CharacterProfileDocument profile : characterProfileRepository.findTop20ByCharacterNameContainingIgnoreCase(keyword)) {
            if (profile.getUserId() != null && !profile.getUserId().isBlank()) {
                userIds.add(profile.getUserId());
            }
        }

        // Build response
        return userIds.stream()
                // Không cho chính mình hiện trong danh sách tìm kiếm
                .filter(userId -> !userId.equals(currentUserId))

                // Giới hạn tối đa 20
                .limit(20)

                // Build item chi tiết
                .map(userId -> buildSearchItem(currentUserId, userId))

                // Loại bỏ null nếu user không tồn tại
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Gửi lời mời kết bạn.
     *
     * Validate:
     * - targetUserId phải hợp lệ
     * - không được tự kết bạn với chính mình
     * - người nhận phải tồn tại
     * - chưa có quan hệ trước đó
     *
     * Nếu đã có quan hệ:
     * - ACCEPTED   => báo đã là bạn bè
     * - currentUser là requester => báo đã gửi lời mời rồi
     * - ngược lại => báo người kia đã gửi lời mời cho bạn
     *
     * Khi tạo mới:
     * - lưu quan hệ PENDING
     * - requesterId = người gửi
     * - addresseeId = người nhận
     * - phát realtime bằng socket
     */
    public MessageResponse sendFriendRequest(String currentUserId, String targetUserId) {
        // Validate targetUserId
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalArgumentException("Thiếu người cần kết bạn");
        }

        // Không cho tự kết bạn
        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Không thể tự kết bạn với chính mình");
        }

        // Kiểm tra target có tồn tại hay không
        userAccountRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Người chơi không tồn tại"));

        // Kiểm tra đã có quan hệ trước đó chưa
        Optional<FriendDocument> relationOpt = findRelation(currentUserId, targetUserId);
        if (relationOpt.isPresent()) {
            FriendDocument relation = relationOpt.get();

            // Đã là bạn bè
            if ("ACCEPTED".equals(relation.getStatus())) {
                throw new IllegalArgumentException("Hai người đã là bạn bè");
            }

            // Người hiện tại là người gửi trước đó
            if (currentUserId.equals(relation.getRequesterId())) {
                throw new IllegalArgumentException("Bạn đã gửi lời mời trước đó rồi");
            }

            // Người kia đã gửi lời mời cho currentUser
            throw new IllegalArgumentException("Người này đã gửi lời mời cho bạn, hãy vào mục lời mời để chấp nhận");
        }

        // Chuẩn hóa thứ tự cặp user
        String[] pair = sortPair(currentUserId, targetUserId);

        // Tạo quan hệ mới
        FriendDocument friend = new FriendDocument();
        friend.setUserAId(pair[0]);
        friend.setUserBId(pair[1]);
        friend.setRequesterId(currentUserId);
        friend.setAddresseeId(targetUserId);
        friend.setStatus("PENDING");
        friend.setCreatedAt(Instant.now());
        friend.setUpdatedAt(Instant.now());

        // Lưu DB
        friend = friendRepository.save(friend);

        // Phát sự kiện realtime
        friendChatSocketService.notifyFriendRequestCreated(friend);

        return new MessageResponse("Đã gửi lời mời kết bạn");
    }

    /**
     * Lấy danh sách lời mời kết bạn đến currentUser.
     *
     * Chỉ lấy các request:
     * - addresseeId = currentUserId
     * - status = PENDING
     *
     * type = INCOMING để frontend phân biệt
     */
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

    /**
     * Lấy danh sách lời mời kết bạn mà currentUser đã gửi đi.
     *
     * Chỉ lấy các request:
     * - requesterId = currentUserId
     * - status = PENDING
     *
     * type = OUTGOING để frontend phân biệt
     */
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

    /**
     * Chấp nhận lời mời kết bạn.
     *
     * Điều kiện:
     * - request phải tồn tại
     * - status phải là PENDING
     * - currentUser phải là người được nhận lời mời
     *
     * Khi chấp nhận:
     * - chuyển status thành ACCEPTED
     * - set acceptedAt
     * - update updatedAt
     * - phát realtime để 2 client cập nhật ngay
     */
    public MessageResponse acceptRequest(String currentUserId, String requestId) {
        // Tìm request
        FriendDocument request = friendRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lời mời"));

        // Chỉ xử lý request còn pending
        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalArgumentException("Lời mời này không còn hợp lệ");
        }

        // Chỉ người nhận lời mời mới được chấp nhận
        if (!currentUserId.equals(request.getAddresseeId())) {
            throw new IllegalArgumentException("Bạn không có quyền chấp nhận lời mời này");
        }

        // Cập nhật trạng thái
        request.setStatus("ACCEPTED");
        request.setAcceptedAt(Instant.now());
        request.setUpdatedAt(Instant.now());

        // Lưu DB
        request = friendRepository.save(request);

        // Phát realtime
        friendChatSocketService.notifyFriendRequestAccepted(request);

        return new MessageResponse("Đã chấp nhận lời mời kết bạn");
    }

    /**
     * Từ chối lời mời hoặc hủy lời mời đã gửi.
     *
     * Điều kiện:
     * - request phải tồn tại
     * - status phải là PENDING
     * - currentUser phải là requester hoặc addressee
     *
     * Cách xử lý:
     * - xóa hẳn document request khỏi DB
     * - phát realtime cho 2 phía
     */
    public MessageResponse rejectRequest(String currentUserId, String requestId) {
        // Tìm request
        FriendDocument request = friendRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lời mời"));

        // Chỉ xử lý request còn pending
        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalArgumentException("Lời mời này không còn hợp lệ");
        }

        // Chỉ người gửi hoặc người nhận mới được xóa request
        if (!currentUserId.equals(request.getAddresseeId()) && !currentUserId.equals(request.getRequesterId())) {
            throw new IllegalArgumentException("Bạn không có quyền xóa lời mời này");
        }

        // Xóa DB
        friendRepository.deleteById(requestId);

        // Phát realtime
        friendChatSocketService.notifyFriendRequestDeleted(request);

        return new MessageResponse("Đã xóa lời mời kết bạn");
    }

    /**
     * Lấy danh sách bạn bè của currentUser.
     *
     * Vì dữ liệu có thể nằm ở 2 phía:
     * - currentUser là userA
     * - currentUser là userB
     *
     * nên cần query cả 2 danh sách rồi gộp lại.
     *
     * Sau đó:
     * - sort giảm dần theo updatedAt
     * - xác định friendUserId là người còn lại trong cặp
     * - trả về thông tin summary của bạn bè
     */
    public List<FriendListItemResponse> getFriends(String currentUserId) {
        // Lấy toàn bộ quan hệ ACCEPTED ở cả 2 phía
        List<FriendDocument> all = new ArrayList<>();
        all.addAll(friendRepository.findByUserAIdAndStatusOrderByUpdatedAtDesc(currentUserId, "ACCEPTED"));
        all.addAll(friendRepository.findByUserBIdAndStatusOrderByUpdatedAtDesc(currentUserId, "ACCEPTED"));

        // Convert sang response
        return all.stream()
                .sorted(Comparator.comparing(FriendDocument::getUpdatedAt).reversed())
                .map(item -> {
                    // Xác định người bạn là ai trong cặp
                    String friendUserId = currentUserId.equals(item.getUserAId()) ? item.getUserBId() : item.getUserAId();

                    return new FriendListItemResponse(
                            item.getId(),
                            toUserSummary(friendUserId),
                            item.getAcceptedAt()
                    );
                })
                .toList();
    }

    /**
     * Xóa bạn bè.
     *
     * Điều kiện:
     * - quan hệ giữa 2 người phải tồn tại
     * - status phải là ACCEPTED
     *
     * Khi xóa:
     * - delete document quan hệ
     * - phát realtime cho cả 2 phía cập nhật giao diện ngay
     */
    public MessageResponse removeFriend(String currentUserId, String friendUserId) {
        // Tìm quan hệ
        FriendDocument relation = findRelation(currentUserId, friendUserId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy quan hệ bạn bè"));

        // Chỉ xóa nếu thật sự đang là bạn bè
        if (!"ACCEPTED".equals(relation.getStatus())) {
            throw new IllegalArgumentException("Hai người chưa là bạn bè");
        }

        // Xóa DB
        friendRepository.deleteById(relation.getId());

        // Phát realtime
        friendChatSocketService.notifyFriendRemoved(currentUserId, friendUserId);

        return new MessageResponse("Đã xóa bạn bè");
    }

    /**
     * Lấy lịch sử chat giữa currentUser và friendUserId.
     *
     * Điều kiện:
     * - 2 người phải có quan hệ bạn bè ACCEPTED
     *
     * Cách lấy:
     * - dùng conversationKey chung cho 2 người
     * - lấy 50 tin nhắn mới nhất theo thứ tự giảm dần createdAt
     * - reverse lại để frontend hiển thị từ cũ -> mới
     *
     * Với tin nhắn đã thu hồi:
     * - trả content = ""
     * - giữ recalled = true để frontend biết đó là tin đã thu hồi
     */
    public List<FriendChatMessageResponse> getChatHistory(String currentUserId, String friendUserId) {
        // Kiểm tra quan hệ
        FriendDocument relation = findRelation(currentUserId, friendUserId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy quan hệ bạn bè"));

        // Chỉ cho xem nếu đã là bạn bè
        if (!"ACCEPTED".equals(relation.getStatus())) {
            throw new IllegalArgumentException("Chỉ xem chat với bạn bè đã chấp nhận");
        }

        // Lấy 50 tin nhắn gần nhất
        List<FriendChatMessageDocument> messages = friendChatMessageRepository
                .findTop50ByConversationKeyOrderByCreatedAtDesc(
                        FriendChatSocketService.buildConversationKey(currentUserId, friendUserId)
                );

        // Đảo lại để hiển thị từ cũ đến mới
        Collections.reverse(messages);

        // Convert sang response
        return messages.stream()
                .map(item -> new FriendChatMessageResponse(
                        item.getId(),
                        item.getSenderId(),
                        item.getReceiverId(),
                        item.isRecalled() ? "" : item.getContent(),
                        item.getCreatedAt(),
                        item.isRecalled(),
                        item.getRecalledAt()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Build dữ liệu 1 item tìm kiếm user.
     *
     * Trả về thông tin:
     * - userId
     * - username
     * - characterName
     * - avatarCode
     * - online/offline
     * - relationshipStatus
     * - requestId (nếu đang có pending/friend relation)
     *
     * relationshipStatus có thể là:
     * - NONE
     * - FRIEND
     * - PENDING_OUT : currentUser đã gửi lời mời
     * - PENDING_IN  : targetUser đã gửi lời mời tới currentUser
     */
    private FriendSearchItemResponse buildSearchItem(String currentUserId, String targetUserId) {
        // Lấy user
        UserAccountDocument user = userAccountRepository.findById(targetUserId).orElse(null);
        if (user == null) return null;

        // Lấy hoặc tạo profile mặc định
        CharacterProfileDocument profile = characterProfileRepository.findByUserId(targetUserId)
                .orElseGet(() -> characterProfileRepository.save(new CharacterProfileDocument(targetUserId)));

        // Tìm quan hệ giữa currentUser và targetUser
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

        // Trả kết quả
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

    /**
     * Chuyển userId thành object summary dùng chung cho giao diện bạn bè.
     *
     * Hàm này tận dụng FriendChatSocketService.getUserView()
     * để lấy luôn cả trạng thái online realtime.
     */
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

    /**
     * Tìm quan hệ giữa 2 user.
     *
     * Do quan hệ được lưu theo cặp đã sắp xếp sẵn,
     * nên cần sortPair trước khi query.
     */
    private Optional<FriendDocument> findRelation(String userId1, String userId2) {
        String[] pair = sortPair(userId1, userId2);
        return friendRepository.findByUserAIdAndUserBId(pair[0], pair[1]);
    }

    /**
     * Sắp xếp 2 userId theo thứ tự tăng dần để:
     * - lưu dữ liệu nhất quán
     * - tránh trùng cặp A-B và B-A
     */
    private String[] sortPair(String userId1, String userId2) {
        if (userId1.compareTo(userId2) <= 0) {
            return new String[]{userId1, userId2};
        }
        return new String[]{userId2, userId1};
    }
}