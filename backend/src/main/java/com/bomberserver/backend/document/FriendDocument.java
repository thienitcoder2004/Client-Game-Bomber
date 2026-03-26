package com.bomberserver.backend.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Document lưu mối quan hệ bạn bè giữa 2 user.
 *
 * Collection trong MongoDB:
 * friends
 *
 * Class này lưu cả:
 * - lời mời kết bạn đang chờ (PENDING)
 * - quan hệ bạn bè đã chấp nhận (ACCEPTED)
 */
@Document("friends")
@CompoundIndex(name = "uniq_friend_pair", def = "{ 'userAId': 1, 'userBId': 1 }", unique = true)
public class FriendDocument {

    /**
     * ID chính của document.
     */
    @Id
    private String id;

    /**
     * ID user thứ nhất trong cặp bạn bè.
     *
     * Thường sẽ lưu theo quy tắc cố định:
     * userAId < userBId
     * để tránh trùng cặp.
     */
    private String userAId;

    /**
     * ID user thứ hai trong cặp bạn bè.
     */
    private String userBId;

    /**
     * ID người đã gửi lời mời kết bạn.
     */
    private String requesterId;

    /**
     * ID người nhận lời mời kết bạn.
     */
    private String addresseeId;

    /**
     * Trạng thái quan hệ bạn bè.
     *
     * Ví dụ:
     * - PENDING  : đang chờ chấp nhận
     * - ACCEPTED : đã là bạn bè
     */
    private String status;

    /**
     * Thời điểm tạo lời mời / quan hệ.
     */
    private Instant createdAt;

    /**
     * Thời điểm cập nhật gần nhất.
     */
    private Instant updatedAt;

    /**
     * Thời điểm chấp nhận kết bạn.
     * Chỉ có giá trị khi status = ACCEPTED.
     */
    private Instant acceptedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserAId() {
        return userAId;
    }

    public void setUserAId(String userAId) {
        this.userAId = userAId;
    }

    public String getUserBId() {
        return userBId;
    }

    public void setUserBId(String userBId) {
        this.userBId = userBId;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(String requesterId) {
        this.requesterId = requesterId;
    }

    public String getAddresseeId() {
        return addresseeId;
    }

    public void setAddresseeId(String addresseeId) {
        this.addresseeId = addresseeId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(Instant acceptedAt) {
        this.acceptedAt = acceptedAt;
    }
}