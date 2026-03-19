package com.bomberserver.backend.service;

import com.bomberserver.backend.document.MatchHistoryDocument;
import com.bomberserver.backend.repository.MatchHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoryService {

    private final MatchHistoryRepository matchHistoryRepository;

    public HistoryService(MatchHistoryRepository matchHistoryRepository) {
        this.matchHistoryRepository = matchHistoryRepository;
    }

    // Lấy lịch sử của user hiện tại
    public List<MatchHistoryDocument> getMyHistory(String userId) {
        return matchHistoryRepository.findByParticipantUserIdsContainsOrderByEndedAtDesc(userId);
    }

    // Hàm này để bước sau gọi khi trận kết thúc
    public MatchHistoryDocument saveHistory(MatchHistoryDocument history) {
        return matchHistoryRepository.save(history);
    }
}