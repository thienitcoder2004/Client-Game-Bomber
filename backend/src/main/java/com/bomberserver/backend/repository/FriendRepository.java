package com.bomberserver.backend.repository;

import com.bomberserver.backend.document.FriendDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository thao tác với collection friends.
 *
 * Collection này dùng để lưu:
 * - lời mời kết bạn đang chờ
 * - quan hệ bạn bè đã chấp nhận
 */
public interface FriendRepository extends MongoRepository<FriendDocument, String> {

    /**
     * Tìm quan hệ bạn bè / lời mời kết bạn giữa 2 user theo cặp userAId, userBId.
     *
     * Thường userAId và userBId đã được chuẩn hóa sẵn theo thứ tự cố định
     * để tránh trùng dữ liệu.
     *
     * @param userAId user A
     * @param userBId user B
     * @return Optional chứa quan hệ nếu tồn tại
     */
    Optional<FriendDocument> findByUserAIdAndUserBId(String userAId, String userBId);

    /**
     * Lấy danh sách quan hệ theo userAId và status, sắp xếp mới cập nhật gần nhất trước.
     *
     * @param userAId id user A
     * @param status trạng thái quan hệ, ví dụ PENDING / ACCEPTED
     * @return danh sách quan hệ phù hợp
     */
    List<FriendDocument> findByUserAIdAndStatusOrderByUpdatedAtDesc(String userAId, String status);

    /**
     * Lấy danh sách quan hệ theo userBId và status, sắp xếp mới cập nhật gần nhất trước.
     *
     * @param userBId id user B
     * @param status trạng thái quan hệ
     * @return danh sách quan hệ phù hợp
     */
    List<FriendDocument> findByUserBIdAndStatusOrderByUpdatedAtDesc(String userBId, String status);

    /**
     * Lấy danh sách lời mời kết bạn mà user hiện tại là người nhận,
     * sắp xếp theo thời gian tạo mới nhất trước.
     *
     * Dùng cho danh sách lời mời đến.
     *
     * @param addresseeId id người nhận lời mời
     * @param status trạng thái, thường là PENDING
     * @return danh sách lời mời đến
     */
    List<FriendDocument> findByAddresseeIdAndStatusOrderByCreatedAtDesc(String addresseeId, String status);

    /**
     * Lấy danh sách lời mời kết bạn mà user hiện tại là người gửi,
     * sắp xếp theo thời gian tạo mới nhất trước.
     *
     * Dùng cho danh sách lời mời đã gửi.
     *
     * @param requesterId id người gửi lời mời
     * @param status trạng thái, thường là PENDING
     * @return danh sách lời mời đã gửi
     */
    List<FriendDocument> findByRequesterIdAndStatusOrderByCreatedAtDesc(String requesterId, String status);
}