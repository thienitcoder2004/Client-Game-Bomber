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
 * Service xử lý toàn bộ nghiệp vụ liên quan đến:
 * - tìm kiếm người chơi
 * - gửi lời mời kết bạn
 * - xem danh sách lời mời đến / đi
 * - chấp nhận lời mời
 * - từ chối / xóa lời mời
 * - lấy danh sách bạn bè
 * - xóa bạn bè
 * - lấy lịch sử chat giữa 2 người
 *
 * Service này làm việc với database thông qua các repository
 * và kết hợp với FriendChatSocketService để đẩy sự kiện realtime
 * sang frontend mà không cần reload trang.
 */
@Service
public class FriendService {

    /** Repository quản lý dữ liệu quan hệ bạn bè */
    private final FriendRepository friendRepository;

    /** Repository quản lý dữ liệu tin nhắn chat bạn bè */
    private final FriendChatMessageRepository friendChatMessageRepository;

    /** Repository quản lý tài khoản người dùng */
    private final UserAccountRepository userAccountRepository;

    /** Repository quản lý hồ sơ nhân vật */
    private final CharacterProfileRepository characterProfileRepository;

    /**
     * Service socket dùng để:
     * - kiểm tra user online/offline
     * - gửi thông báo realtime khi có thay đổi bạn bè / chat
     */
    private final FriendChatSocketService friendChatSocketService;

    /**
     * Constructor để Spring inject các dependency cần thiết.
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
     * Tìm kiếm user theo từ khóa.
     *
     * Cách tìm:
     * - tìm theo username trong bảng user
     * - tìm theo characterName trong bảng profile
     *
     * Dùng LinkedHashSet để:
     * - tránh trùng userId
     * - vẫn giữ thứ tự kết quả
     *
     * Sau đó:
     * - loại chính currentUser ra khỏi kết quả
     * - giới hạn tối đa 20 người
     * - build dữ liệu hiển thị cho từng người
     *
     * @param currentUserId id của người đang đăng nhập
     * @param keywordRaw từ khóa tìm kiếm người chơi
     * @return danh sách người chơi phù hợp với từ khóa
     */
    public List<FriendSearchItemResponse> searchUsers(String currentUserId, String keywordRaw) {
        // Chuẩn hóa từ khóa: nếu null thì chuyển thành chuỗi rỗng, sau đó trim khoảng trắng đầu/cuối
        String keyword = keywordRaw == null ? "" : keywordRaw.trim();

        // Nếu không có từ khóa thì trả về danh sách rỗng
        if (keyword.isBlank()) {
            return Collections.emptyList();
        }

        // Dùng LinkedHashSet để:
        // - loại bỏ trùng lặp
        // - giữ nguyên thứ tự thêm vào
        LinkedHashSet<String> userIds = new LinkedHashSet<>();

        // Tìm tối đa 20 user theo username gần giống keyword
        for (UserAccountDocument user : userAccountRepository.findTop20ByUsernameContainingIgnoreCase(keyword)) {
            userIds.add(user.getId());
        }

        // Tìm tối đa 20 profile theo characterName gần giống keyword
        for (CharacterProfileDocument profile : characterProfileRepository.findTop20ByCharacterNameContainingIgnoreCase(keyword)) {
            if (profile.getUserId() != null && !profile.getUserId().isBlank()) {
                userIds.add(profile.getUserId());
            }
        }

        // Chuyển danh sách userId thành response hoàn chỉnh
        return userIds.stream()
                // Không hiển thị chính bản thân người đang đăng nhập
                .filter(userId -> !userId.equals(currentUserId))

                // Giới hạn tối đa 20 kết quả trả ra
                .limit(20)

                // Build thông tin hiển thị chi tiết cho từng user
                .map(userId -> buildSearchItem(currentUserId, userId))

                // Loại bỏ kết quả null nếu có user không còn tồn tại
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Gửi lời mời kết bạn tới một user khác.
     *
     * Luồng xử lý:
     * 1. Kiểm tra targetUserId hợp lệ
     * 2. Không cho phép tự kết bạn với chính mình
     * 3. Kiểm tra user mục tiêu có tồn tại không
     * 4. Kiểm tra giữa 2 người đã có quan hệ gì chưa
     *    - nếu đã ACCEPTED => đã là bạn bè
     *    - nếu đã có request và currentUser là người gửi => đã gửi rồi
     *    - nếu đã có request ngược lại => người kia đã gửi lời mời cho bạn
     * 5. Nếu chưa có quan hệ => tạo request mới với trạng thái PENDING
     * 6. Lưu DB và phát sự kiện realtime cho 2 phía
     *
     * @param currentUserId id người đang đăng nhập
     * @param targetUserId id người muốn kết bạn
     * @return thông báo kết quả
     */
    public MessageResponse sendFriendRequest(String currentUserId, String targetUserId) {
        // Kiểm tra người cần kết bạn có được truyền lên hay không
        if (targetUserId == null || targetUserId.isBlank()) {
            throw new IllegalArgumentException("Thiếu người cần kết bạn");
        }

        // Không được gửi lời mời kết bạn cho chính mình
        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Không thể tự kết bạn với chính mình");
        }

        // Kiểm tra người chơi mục tiêu có tồn tại trong hệ thống không
        userAccountRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Người chơi không tồn tại"));

        // Tìm xem giữa 2 người đã có quan hệ trước đó chưa
        Optional<FriendDocument> relationOpt = findRelation(currentUserId, targetUserId);
        if (relationOpt.isPresent()) {
            FriendDocument relation = relationOpt.get();

            // Nếu đã là bạn bè thì không cho gửi lời mời nữa
            if ("ACCEPTED".equals(relation.getStatus())) {
                throw new IllegalArgumentException("Hai người đã là bạn bè");
            }

            // Nếu currentUser chính là người đã gửi request trước đó
            if (currentUserId.equals(relation.getRequesterId())) {
                throw new IllegalArgumentException("Bạn đã gửi lời mời trước đó rồi");
            }

            // Nếu không phải requester thì có nghĩa người kia đã gửi lời mời cho currentUser
            throw new IllegalArgumentException("Người này đã gửi lời mời cho bạn, hãy vào mục lời mời để chấp nhận");
        }

        // Chuẩn hóa cặp user để lưu thống nhất trong DB
        String[] pair = sortPair(currentUserId, targetUserId);

        // Tạo mới quan hệ bạn bè ở trạng thái chờ chấp nhận
        FriendDocument friend = new FriendDocument();
        friend.setUserAId(pair[0]);          // user đứng trước theo thứ tự
        friend.setUserBId(pair[1]);          // user đứng sau theo thứ tự
        friend.setRequesterId(currentUserId); // người gửi lời mời
        friend.setAddresseeId(targetUserId);  // người nhận lời mời
        friend.setStatus("PENDING");          // trạng thái chờ xử lý
        friend.setCreatedAt(Instant.now());   // thời điểm tạo
        friend.setUpdatedAt(Instant.now());   // thời điểm cập nhật gần nhất

        // Lưu request vào database
        friend = friendRepository.save(friend);

        // Gửi sự kiện realtime cho frontend của cả 2 phía
        friendChatSocketService.notifyFriendRequestCreated(friend);

        // Trả về thông báo thành công
        return new MessageResponse("Đã gửi lời mời kết bạn");
    }

    /**
     * Lấy danh sách lời mời kết bạn mà currentUser nhận được.
     *
     * Chỉ lấy các request:
     * - addresseeId = currentUserId
     * - status = PENDING
     *
     * @param currentUserId id người đang đăng nhập
     * @return danh sách lời mời đến
     */
    public List<FriendRequestResponse> getIncomingRequests(String currentUserId) {
        return friendRepository.findByAddresseeIdAndStatusOrderByCreatedAtDesc(currentUserId, "PENDING")
                .stream()
                .map(item -> new FriendRequestResponse(
                        item.getId(),                       // id của request
                        "INCOMING",                         // loại request: lời mời đến
                        toUserSummary(item.getRequesterId()), // thông tin người gửi
                        item.getCreatedAt()                 // thời gian gửi
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
     * @param currentUserId id người đang đăng nhập
     * @return danh sách lời mời đã gửi
     */
    public List<FriendRequestResponse> getOutgoingRequests(String currentUserId) {
        return friendRepository.findByRequesterIdAndStatusOrderByCreatedAtDesc(currentUserId, "PENDING")
                .stream()
                .map(item -> new FriendRequestResponse(
                        item.getId(),                        // id của request
                        "OUTGOING",                          // loại request: lời mời đi
                        toUserSummary(item.getAddresseeId()), // thông tin người nhận
                        item.getCreatedAt()                  // thời gian gửi
                ))
                .toList();
    }

    /**
     * Chấp nhận một lời mời kết bạn.
     *
     * Điều kiện:
     * - request phải tồn tại
     * - request phải đang ở trạng thái PENDING
     * - currentUser phải là người nhận lời mời
     *
     * Sau khi chấp nhận:
     * - cập nhật status thành ACCEPTED
     * - set thời gian acceptedAt
     * - cập nhật updatedAt
     * - lưu DB
     * - phát sự kiện realtime cho cả 2 phía
     *
     * @param currentUserId id người đang đăng nhập
     * @param requestId id lời mời kết bạn
     * @return thông báo kết quả
     */
    public MessageResponse acceptRequest(String currentUserId, String requestId) {
        // Tìm request theo id, nếu không có thì báo lỗi
        FriendDocument request = friendRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lời mời"));

        // Chỉ cho phép xử lý nếu request còn đang chờ
        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalArgumentException("Lời mời này không còn hợp lệ");
        }

        // Chỉ người nhận lời mời mới được quyền chấp nhận
        if (!currentUserId.equals(request.getAddresseeId())) {
            throw new IllegalArgumentException("Bạn không có quyền chấp nhận lời mời này");
        }

        // Cập nhật trạng thái thành đã chấp nhận
        request.setStatus("ACCEPTED");

        // Ghi lại thời điểm chấp nhận
        request.setAcceptedAt(Instant.now());

        // Cập nhật thời gian chỉnh sửa gần nhất
        request.setUpdatedAt(Instant.now());

        // Lưu thay đổi xuống DB
        request = friendRepository.save(request);

        // Gửi sự kiện realtime để frontend cập nhật ngay
        friendChatSocketService.notifyFriendRequestAccepted(request);

        // Trả về thông báo thành công
        return new MessageResponse("Đã chấp nhận lời mời kết bạn");
    }

    /**
     * Xóa / từ chối một lời mời kết bạn.
     *
     * Trường hợp áp dụng:
     * - người nhận từ chối lời mời
     * - người gửi muốn hủy lời mời đã gửi
     *
     * Điều kiện:
     * - request phải tồn tại
     * - request phải đang ở trạng thái PENDING
     * - currentUser phải là requester hoặc addressee
     *
     * @param currentUserId id người đang đăng nhập
     * @param requestId id của lời mời
     * @return thông báo kết quả
     */
    public MessageResponse rejectRequest(String currentUserId, String requestId) {
        // Tìm request theo id
        FriendDocument request = friendRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lời mời"));

        // Chỉ cho phép xóa nếu request còn đang chờ
        if (!"PENDING".equals(request.getStatus())) {
            throw new IllegalArgumentException("Lời mời này không còn hợp lệ");
        }

        // Chỉ người gửi hoặc người nhận mới được xóa request
        if (!currentUserId.equals(request.getAddresseeId()) && !currentUserId.equals(request.getRequesterId())) {
            throw new IllegalArgumentException("Bạn không có quyền xóa lời mời này");
        }

        // Xóa request khỏi DB
        friendRepository.deleteById(requestId);

        // Gửi sự kiện realtime cho frontend cập nhật
        friendChatSocketService.notifyFriendRequestDeleted(request);

        // Trả về thông báo thành công
        return new MessageResponse("Đã xóa lời mời kết bạn");
    }

    /**
     * Lấy danh sách bạn bè của currentUser.
     *
     * Vì quan hệ bạn bè được lưu theo dạng cặp userA - userB,
     * currentUser có thể nằm ở userA hoặc userB.
     *
     * Do đó cần:
     * - query danh sách currentUser là userA
     * - query danh sách currentUser là userB
     * - gộp cả 2 danh sách lại
     * - sắp xếp theo updatedAt giảm dần
     *
     * @param currentUserId id người đang đăng nhập
     * @return danh sách bạn bè
     */
    public List<FriendListItemResponse> getFriends(String currentUserId) {
        // Danh sách gộp tất cả quan hệ bạn bè ACCEPTED
        List<FriendDocument> all = new ArrayList<>();

        // Lấy các quan hệ mà currentUser đang là userA
        all.addAll(friendRepository.findByUserAIdAndStatusOrderByUpdatedAtDesc(currentUserId, "ACCEPTED"));

        // Lấy các quan hệ mà currentUser đang là userB
        all.addAll(friendRepository.findByUserBIdAndStatusOrderByUpdatedAtDesc(currentUserId, "ACCEPTED"));

        // Chuyển dữ liệu sang dạng response cho frontend
        return all.stream()
                // Sắp xếp theo thời gian cập nhật mới nhất trước
                .sorted(Comparator.comparing(FriendDocument::getUpdatedAt).reversed())

                // Map từng quan hệ thành thông tin bạn bè
                .map(item -> {
                    // Xác định id của người bạn còn lại trong cặp quan hệ
                    String friendUserId = currentUserId.equals(item.getUserAId()) ? item.getUserBId() : item.getUserAId();

                    return new FriendListItemResponse(
                            item.getId(),                // id của quan hệ bạn bè
                            toUserSummary(friendUserId), // thông tin tóm tắt của người bạn
                            item.getAcceptedAt()         // thời gian trở thành bạn bè
                    );
                })
                .toList();
    }

    /**
     * Xóa một người khỏi danh sách bạn bè.
     *
     * Điều kiện:
     * - quan hệ giữa 2 người phải tồn tại
     * - trạng thái phải là ACCEPTED
     *
     * Sau đó:
     * - xóa bản ghi quan hệ khỏi DB
     * - phát sự kiện realtime cho cả 2 phía
     *
     * @param currentUserId id người đang đăng nhập
     * @param friendUserId id người bạn cần xóa
     * @return thông báo kết quả
     */
    public MessageResponse removeFriend(String currentUserId, String friendUserId) {
        // Tìm quan hệ giữa 2 người
        FriendDocument relation = findRelation(currentUserId, friendUserId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy quan hệ bạn bè"));

        // Chỉ cho xóa nếu hiện tại thật sự là bạn bè
        if (!"ACCEPTED".equals(relation.getStatus())) {
            throw new IllegalArgumentException("Hai người chưa là bạn bè");
        }

        // Xóa quan hệ khỏi DB
        friendRepository.deleteById(relation.getId());

        // Gửi sự kiện realtime để giao diện cập nhật ngay
        friendChatSocketService.notifyFriendRemoved(currentUserId, friendUserId);

        // Trả thông báo thành công
        return new MessageResponse("Đã xóa bạn bè");
    }

    /**
     * Lấy lịch sử chat giữa currentUser và một người bạn.
     *
     * Điều kiện:
     * - 2 người phải có quan hệ bạn bè ACCEPTED
     *
     * Cách lấy:
     * - tạo conversationKey chung cho 2 user
     * - lấy 50 tin nhắn mới nhất theo createdAt giảm dần
     * - đảo ngược danh sách để frontend hiển thị từ cũ đến mới
     *
     * Với tin nhắn đã thu hồi:
     * - trả về content = ""
     * - vẫn giữ cờ recalled = true để frontend nhận biết
     *
     * @param currentUserId id người đang đăng nhập
     * @param friendUserId id người bạn đang chat
     * @return danh sách tin nhắn
     */
    public List<FriendChatMessageResponse> getChatHistory(String currentUserId, String friendUserId) {
        // Kiểm tra quan hệ giữa 2 người có tồn tại hay không
        FriendDocument relation = findRelation(currentUserId, friendUserId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy quan hệ bạn bè"));

        // Chỉ cho xem lịch sử chat nếu đã là bạn bè
        if (!"ACCEPTED".equals(relation.getStatus())) {
            throw new IllegalArgumentException("Chỉ xem chat với bạn bè đã chấp nhận");
        }

        // Lấy tối đa 50 tin nhắn gần nhất của cuộc trò chuyện
        List<FriendChatMessageDocument> messages = friendChatMessageRepository
                .findTop50ByConversationKeyOrderByCreatedAtDesc(
                        FriendChatSocketService.buildConversationKey(currentUserId, friendUserId)
                );

        // Vì query lấy theo thứ tự mới nhất -> cũ nhất,
        // nên đảo ngược để hiển thị từ cũ -> mới cho giao diện chat
        Collections.reverse(messages);

        // Convert dữ liệu document sang response
        return messages.stream()
                .map(item -> new FriendChatMessageResponse(
                        item.getId(),                        // id tin nhắn
                        item.getSenderId(),                  // người gửi
                        item.getReceiverId(),                // người nhận
                        item.isRecalled() ? "" : item.getContent(), // nếu đã thu hồi thì không trả nội dung
                        item.getCreatedAt(),                 // thời điểm gửi
                        item.isRecalled(),                   // trạng thái đã thu hồi hay chưa
                        item.getRecalledAt()                 // thời điểm thu hồi
                ))
                .collect(Collectors.toList());
    }

    /**
     * Build dữ liệu hiển thị cho 1 user trong kết quả tìm kiếm.
     *
     * Kết quả trả về gồm:
     * - id người dùng
     * - username
     * - characterName
     * - avatarCode
     * - trạng thái online
     * - trạng thái quan hệ với currentUser
     * - requestId nếu đã có quan hệ / lời mời
     *
     * relationshipStatus có thể là:
     * - NONE        : chưa có quan hệ gì
     * - FRIEND      : đã là bạn bè
     * - PENDING_OUT : currentUser đã gửi lời mời
     * - PENDING_IN  : currentUser nhận lời mời từ người kia
     *
     * @param currentUserId id người hiện tại
     * @param targetUserId id người cần build dữ liệu
     * @return object hiển thị cho tìm kiếm bạn bè
     */
    private FriendSearchItemResponse buildSearchItem(String currentUserId, String targetUserId) {
        // Lấy thông tin user
        UserAccountDocument user = userAccountRepository.findById(targetUserId).orElse(null);

        // Nếu user không tồn tại thì trả null
        if (user == null) return null;

        // Lấy profile của user
        // Nếu chưa có profile thì tự tạo profile mặc định để tránh null
        CharacterProfileDocument profile = characterProfileRepository.findByUserId(targetUserId)
                .orElseGet(() -> characterProfileRepository.save(new CharacterProfileDocument(targetUserId)));

        // Tìm quan hệ giữa currentUser và targetUser
        Optional<FriendDocument> relation = findRelation(currentUserId, targetUserId);

        // Mặc định là chưa có quan hệ
        String relationshipStatus = "NONE";
        String requestId = null;

        // Nếu đã có quan hệ / lời mời trước đó
        if (relation.isPresent()) {
            FriendDocument item = relation.get();
            requestId = item.getId();

            // Nếu đã được chấp nhận thì là bạn bè
            if ("ACCEPTED".equals(item.getStatus())) {
                relationshipStatus = "FRIEND";

            // Nếu currentUser là người gửi lời mời thì là đang chờ đi
            } else if (currentUserId.equals(item.getRequesterId())) {
                relationshipStatus = "PENDING_OUT";

            // Còn lại là currentUser đang nhận lời mời đến
            } else {
                relationshipStatus = "PENDING_IN";
            }
        }

        // Trả dữ liệu hoàn chỉnh cho frontend
        return new FriendSearchItemResponse(
                user.getId(),                                   // id người dùng
                user.getUsername(),                             // username
                profile.getCharacterName(),                     // tên nhân vật
                profile.getAvatarCode(),                        // mã avatar
                friendChatSocketService.isUserOnline(user.getId()), // trạng thái online
                relationshipStatus,                             // trạng thái quan hệ
                requestId                                       // id request nếu có
        );
    }

    /**
     * Chuyển userId thành object thông tin rút gọn để trả cho frontend.
     *
     * Hàm này dùng FriendChatSocketService.getUserView()
     * để lấy luôn:
     * - userId
     * - username
     * - characterName
     * - avatarCode
     * - online
     *
     * @param userId id người dùng
     * @return dữ liệu tóm tắt người dùng
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
     * Vì dữ liệu quan hệ được lưu theo thứ tự cố định userA-userB,
     * nên trước khi query phải sortPair để đảm bảo:
     * - A-B và B-A được hiểu là cùng một quan hệ
     *
     * @param userId1 user thứ nhất
     * @param userId2 user thứ hai
     * @return Optional chứa quan hệ nếu tồn tại
     */
    private Optional<FriendDocument> findRelation(String userId1, String userId2) {
        String[] pair = sortPair(userId1, userId2);
        return friendRepository.findByUserAIdAndUserBId(pair[0], pair[1]);
    }

    /**
     * Sắp xếp 2 userId theo thứ tự từ điển tăng dần.
     *
     * Mục đích:
     * - chuẩn hóa dữ liệu khi lưu / truy vấn
     * - tránh trùng lặp quan hệ kiểu A-B và B-A
     *
     * Ví dụ:
     * - sortPair("u9", "u2") => ["u2", "u9"]
     *
     * @param userId1 user thứ nhất
     * @param userId2 user thứ hai
     * @return mảng gồm 2 phần tử đã sắp xếp
     */
    private String[] sortPair(String userId1, String userId2) {
        if (userId1.compareTo(userId2) <= 0) {
            return new String[]{userId1, userId2};
        }
        return new String[]{userId2, userId1};
    }
}