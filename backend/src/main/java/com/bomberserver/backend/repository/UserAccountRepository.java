package com.bomberserver.backend.repository;

import com.bomberserver.backend.document.UserAccountDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository thao tác với collection users.
 *
 * Đây là repository chính cho tài khoản người dùng.
 */
public interface UserAccountRepository extends MongoRepository<UserAccountDocument, String> {

    /**
     * Tìm user theo email, không phân biệt hoa thường.
     *
     * @param email email tài khoản
     * @return Optional chứa user nếu tồn tại
     */
    Optional<UserAccountDocument> findByEmailIgnoreCase(String email);

    /**
     * Tìm user theo username, không phân biệt hoa thường.
     *
     * @param username username tài khoản
     * @return Optional chứa user nếu tồn tại
     */
    Optional<UserAccountDocument> findByUsernameIgnoreCase(String username);

    /**
     * Kiểm tra email đã tồn tại hay chưa.
     *
     * @param email email cần kiểm tra
     * @return true nếu đã tồn tại
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Kiểm tra username đã tồn tại hay chưa.
     *
     * @param username username cần kiểm tra
     * @return true nếu đã tồn tại
     */
    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Tìm tối đa 20 user có username chứa từ khóa, không phân biệt hoa thường.
     *
     * Dùng cho tìm kiếm user kết bạn hoặc admin search.
     *
     * @param username từ khóa username
     * @return danh sách tối đa 20 user phù hợp
     */
    List<UserAccountDocument> findTop20ByUsernameContainingIgnoreCase(String username);
}