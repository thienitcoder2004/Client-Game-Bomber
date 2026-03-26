package com.bomberserver.backend.repository;

import com.bomberserver.backend.document.CharacterProfileDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository thao tác với collection character_profiles.
 *
 * Kế thừa MongoRepository để có sẵn các hàm cơ bản như:
 * - save(...)
 * - findById(...)
 * - findAll()
 * - deleteById(...)
 *
 * Ngoài ra khai báo thêm các hàm query theo tên method của Spring Data MongoDB.
 */
public interface CharacterProfileRepository extends MongoRepository<CharacterProfileDocument, String> {

    /**
     * Tìm profile theo userId.
     *
     * Vì mỗi user chỉ có 1 profile nên kết quả trả về là Optional.
     *
     * @param userId id người dùng
     * @return Optional chứa profile nếu tồn tại
     */
    Optional<CharacterProfileDocument> findByUserId(String userId);

    /**
     * Kiểm tra tên nhân vật đã tồn tại hay chưa, không phân biệt hoa thường.
     *
     * Dùng để tránh 2 người chơi có cùng characterName nếu bạn muốn unique theo logic service.
     *
     * @param characterName tên nhân vật cần kiểm tra
     * @return true nếu đã tồn tại, false nếu chưa
     */
    boolean existsByCharacterNameIgnoreCase(String characterName);

    /**
     * Tìm tối đa 20 profile có characterName chứa từ khóa, không phân biệt hoa thường.
     *
     * Dùng cho chức năng tìm kiếm người chơi theo tên nhân vật.
     *
     * @param characterName từ khóa tìm kiếm
     * @return danh sách tối đa 20 profile phù hợp
     */
    List<CharacterProfileDocument> findTop20ByCharacterNameContainingIgnoreCase(String characterName);

    /**
     * Xóa profile theo userId.
     *
     * Thường dùng khi xóa tài khoản user thì xóa luôn profile đi kèm.
     *
     * @param userId id người dùng
     */
    void deleteByUserId(String userId);
}