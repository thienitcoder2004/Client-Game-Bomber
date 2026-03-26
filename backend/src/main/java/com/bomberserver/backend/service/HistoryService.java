package com.bomberserver.backend.service;

import com.bomberserver.backend.document.MatchHistoryDocument;
import com.bomberserver.backend.repository.MatchHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service xử lý nghiệp vụ liên quan đến lịch sử trận đấu.
 *
 * Nhiệm vụ chính:
 * - Lấy danh sách lịch sử các trận mà người chơi đã tham gia
 * - Lưu thông tin trận đấu sau khi trận kết thúc
 *
 * Lớp này hoạt động như tầng trung gian giữa:
 * - Controller: nơi nhận request từ frontend
 * - Repository: nơi thao tác trực tiếp với MongoDB
 */
@Service
public class HistoryService {

    /**
     * Repository dùng để thao tác với collection lưu lịch sử trận đấu.
     *
     * Chức năng:
     * - tìm lịch sử trận theo userId
     * - lưu mới một bản ghi lịch sử trận
     */
    private final MatchHistoryRepository matchHistoryRepository;

    /**
     * Constructor inject MatchHistoryRepository vào service.
     *
     * Spring sẽ tự truyền repository này vào khi khởi tạo bean.
     *
     * @param matchHistoryRepository repository xử lý dữ liệu lịch sử trận
     */
    public HistoryService(MatchHistoryRepository matchHistoryRepository) {
        this.matchHistoryRepository = matchHistoryRepository;
    }

    /**
     * Lấy lịch sử thi đấu của user hiện tại.
     *
     * Ý nghĩa:
     * - Trả về danh sách các trận mà user này đã tham gia
     * - Kết quả được sắp xếp theo thời gian kết thúc trận giảm dần
     *   => trận mới nhất sẽ nằm ở đầu danh sách
     *
     * Repository sử dụng:
     * findByParticipantUserIdsContainsOrderByEndedAtDesc(userId)
     *
     * Giải thích tên hàm repository:
     * - findByParticipantUserIdsContains:
     *   tìm các document mà danh sách participantUserIds có chứa userId
     * - OrderByEndedAtDesc:
     *   sắp xếp theo endedAt giảm dần
     *
     * @param userId id của người chơi hiện tại
     * @return danh sách lịch sử trận đấu của user
     */
    public List<MatchHistoryDocument> getMyHistory(String userId) {
        return matchHistoryRepository.findByParticipantUserIdsContainsOrderByEndedAtDesc(userId);
    }

    /**
     * Lưu lịch sử trận đấu vào database.
     *
     * Hàm này thường sẽ được gọi sau khi trận đấu kết thúc,
     * ví dụ:
     * - đã xác định đội thắng / người thắng
     * - đã tổng hợp chỉ số trận đấu
     * - đã có thời gian bắt đầu và kết thúc
     *
     * Dữ liệu lưu có thể bao gồm:
     * - roomCode
     * - winnerUserId
     * - participantUserIds
     * - startedAt / endedAt
     * - kết quả từng người chơi
     *
     * @param history object chứa toàn bộ thông tin lịch sử trận cần lưu
     * @return document đã được lưu trong database
     */
    public MatchHistoryDocument saveHistory(MatchHistoryDocument history) {
        return matchHistoryRepository.save(history);
    }
}