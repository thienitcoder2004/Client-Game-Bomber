package com.bomberserver.backend.repository;

import com.bomberserver.backend.document.MatchHistoryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Repository thao tác với collection match_histories.
 *
 * Dùng để lưu và truy vấn lịch sử các trận đấu đã kết thúc.
 */
public interface MatchHistoryRepository extends MongoRepository<MatchHistoryDocument, String> {

    /**
     * Tìm tất cả lịch sử trận đấu mà 1 user đã tham gia,
     * sắp xếp theo thời gian kết thúc mới nhất trước.
     *
     * participantUserIds là danh sách user tham gia trận.
     *
     * @param userId id người chơi
     * @return danh sách lịch sử trận đấu của user
     */
    List<MatchHistoryDocument> findByParticipantUserIdsContainsOrderByEndedAtDesc(String userId);
}