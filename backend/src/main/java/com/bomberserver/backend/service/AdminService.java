package com.bomberserver.backend.service;

import com.bomberserver.backend.document.CharacterProfileDocument;
import com.bomberserver.backend.document.MatchHistoryDocument;
import com.bomberserver.backend.document.UserAccountDocument;
import com.bomberserver.backend.dto.admin.AdminMatchResponse;
import com.bomberserver.backend.dto.admin.AdminUserResponse;
import com.bomberserver.backend.repository.CharacterProfileRepository;
import com.bomberserver.backend.repository.MatchHistoryRepository;
import com.bomberserver.backend.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service xử lý toàn bộ nghiệp vụ cho trang Admin.
 *
 * Chức năng chính:
 * - Lấy danh sách user
 * - Khóa / mở khóa tài khoản user
 * - Xóa user
 * - Lấy danh sách lịch sử trận đấu
 *
 * Lưu ý:
 * - Không cho phép thao tác với tài khoản admin mặc định
 * - Không cho phép admin tự khóa / tự xóa chính mình
 */
@Service
public class AdminService {

    /**
     * Email admin mặc định được bảo vệ.
     * Tài khoản này sẽ không hiển thị trong danh sách user
     * và không cho phép lock / unlock / delete.
     */
    private static final String DEFAULT_ADMIN_EMAIL = "admin@gmail.com";

    /**
     * Repository thao tác với collection tài khoản người dùng.
     */
    private final UserAccountRepository userAccountRepository;

    /**
     * Repository thao tác với hồ sơ nhân vật của user.
     */
    private final CharacterProfileRepository characterProfileRepository;

    /**
     * Repository thao tác với lịch sử trận đấu.
     */
    private final MatchHistoryRepository matchHistoryRepository;

    /**
     * Constructor inject các repository cần thiết.
     *
     * @param userAccountRepository repository tài khoản
     * @param characterProfileRepository repository hồ sơ nhân vật
     * @param matchHistoryRepository repository lịch sử trận
     */
    public AdminService(
            UserAccountRepository userAccountRepository,
            CharacterProfileRepository characterProfileRepository,
            MatchHistoryRepository matchHistoryRepository
    ) {
        this.userAccountRepository = userAccountRepository;
        this.characterProfileRepository = characterProfileRepository;
        this.matchHistoryRepository = matchHistoryRepository;
    }

    /**
     * Lấy toàn bộ danh sách user để hiển thị cho admin.
     *
     * Quy trình:
     * 1. Lấy tất cả user từ database
     * 2. Loại bỏ tài khoản admin mặc định
     * 3. Sắp xếp theo ngày tạo mới nhất trước
     * 4. Chuyển từ document sang DTO trả về cho frontend
     *
     * @return danh sách user dành cho admin
     */
    public List<AdminUserResponse> getUsers() {
        return userAccountRepository.findAll()
                .stream()

                // Loại bỏ tài khoản admin mặc định khỏi danh sách hiển thị
                .filter(user -> !isDefaultAdmin(user))

                // Sắp xếp theo createdAt giảm dần (mới nhất lên đầu)
                // Nếu createdAt null thì đẩy xuống cuối
                .sorted(
                        Comparator.comparing(
                                UserAccountDocument::getCreatedAt,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ).reversed()
                )

                // Chuyển từng user thành DTO trả về cho admin UI
                .map(this::toAdminUserResponse)
                .toList();
    }

    /**
     * Khóa tài khoản user.
     *
     * Quy trình:
     * 1. Tìm user theo id
     * 2. Kiểm tra user đó có phải tài khoản được bảo vệ không
     * 3. Đặt active = false
     * 4. Lưu lại database
     * 5. Trả về dữ liệu user đã cập nhật
     *
     * @param userId id user cần khóa
     * @param currentAdminId id admin đang thực hiện thao tác
     * @return thông tin user sau khi khóa
     */
    public AdminUserResponse lockUser(String userId, String currentAdminId) {
        UserAccountDocument user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user"));

        // Kiểm tra có được phép thao tác user này không
        validateProtectedUser(user, currentAdminId);

        // Khóa tài khoản
        user.setActive(false);

        // Lưu lại dữ liệu sau khi cập nhật
        user = userAccountRepository.save(user);

        // Trả về DTO cho frontend
        return toAdminUserResponse(user);
    }

    /**
     * Mở khóa tài khoản user.
     *
     * Quy trình:
     * 1. Tìm user theo id
     * 2. Kiểm tra user có thuộc diện bảo vệ không
     * 3. Đặt active = true
     * 4. Lưu lại database
     * 5. Trả về dữ liệu mới
     *
     * @param userId id user cần mở khóa
     * @param currentAdminId id admin đang thực hiện
     * @return thông tin user sau khi mở khóa
     */
    public AdminUserResponse unlockUser(String userId, String currentAdminId) {
        UserAccountDocument user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user"));

        // Kiểm tra quyền thao tác
        validateProtectedUser(user, currentAdminId);

        // Mở khóa tài khoản
        user.setActive(true);

        // Lưu lại vào database
        user = userAccountRepository.save(user);

        // Trả về DTO
        return toAdminUserResponse(user);
    }

    /**
     * Xóa user khỏi hệ thống.
     *
     * Quy trình:
     * 1. Tìm user theo id
     * 2. Kiểm tra user có thuộc diện được bảo vệ không
     * 3. Nếu user có id thì xóa luôn profile nhân vật liên quan
     * 4. Xóa tài khoản user
     *
     * Lưu ý:
     * - Hàm này chỉ xóa profile theo userId và xóa account
     * - Chưa xử lý xóa lịch sử trận đấu hoặc dữ liệu liên quan khác
     *
     * @param userId id user cần xóa
     * @param currentAdminId id admin đang thực hiện
     */
    public void deleteUser(String userId, String currentAdminId) {
        UserAccountDocument user = userAccountRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy user"));

        // Kiểm tra có được phép thao tác user này không
        validateProtectedUser(user, currentAdminId);

        // Nếu user có id hợp lệ thì xóa hồ sơ nhân vật trước
        if (user.getId() != null) {
            characterProfileRepository.deleteByUserId(user.getId());
        }

        // Xóa tài khoản user
        userAccountRepository.delete(user);
    }

    /**
     * Lấy danh sách toàn bộ trận đấu để admin quản lý / xem thống kê.
     *
     * Quy trình:
     * 1. Lấy tất cả match trong database
     * 2. Sắp xếp theo thời gian kết thúc mới nhất trước
     * 3. Chuyển thành DTO phù hợp cho giao diện admin
     *
     * @return danh sách trận đấu
     */
    public List<AdminMatchResponse> getMatches() {
        return matchHistoryRepository.findAll()
                .stream()

                // Sắp xếp trận theo endedAt giảm dần
                // null sẽ nằm cuối
                .sorted(Comparator.comparing(
                        MatchHistoryDocument::getEndedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))

                // Convert document -> DTO
                .map(this::toAdminMatchResponse)
                .toList();
    }

    /**
     * Kiểm tra user có thuộc nhóm được bảo vệ hay không.
     *
     * Các trường hợp không được thao tác:
     * 1. Là tài khoản admin mặc định
     * 2. Admin đang tự thao tác chính tài khoản của mình
     *
     * @param user user mục tiêu
     * @param currentAdminId id admin đang thao tác
     */
    private void validateProtectedUser(UserAccountDocument user, String currentAdminId) {
        // Không cho thao tác với admin mặc định
        if (isDefaultAdmin(user)) {
            throw new IllegalArgumentException("Không thể thao tác tài khoản admin mặc định");
        }

        // Không cho admin tự khóa / tự mở / tự xóa chính mình
        if (user.getId() != null && user.getId().equals(currentAdminId)) {
            throw new IllegalArgumentException("Bạn không thể tự thao tác chính mình");
        }
    }

    /**
     * Kiểm tra một user có phải admin mặc định hay không
     * dựa trên email.
     *
     * @param user user cần kiểm tra
     * @return true nếu là admin mặc định, ngược lại false
     */
    private boolean isDefaultAdmin(UserAccountDocument user) {
        return user.getEmail() != null
                && user.getEmail().equalsIgnoreCase(DEFAULT_ADMIN_EMAIL);
    }

    /**
     * Chuyển UserAccountDocument thành AdminUserResponse.
     *
     * Mục đích:
     * - Gộp thêm thông tin profile nhân vật
     * - Trả dữ liệu gọn hơn cho frontend
     *
     * Xử lý:
     * - Nếu user không có profile thì characterName / gender = ""
     * - Nếu role null thì mặc định là USER
     * - Nếu active null thì xem như đang hoạt động
     *
     * @param user document tài khoản
     * @return DTO user cho trang admin
     */
    private AdminUserResponse toAdminUserResponse(UserAccountDocument user) {
        // Nếu user chưa có id thì không thể tìm profile theo userId
        Optional<CharacterProfileDocument> profileOpt = user.getId() == null
                ? Optional.empty()
                : characterProfileRepository.findByUserId(user.getId());

        // Lấy tên nhân vật nếu có, không thì trả chuỗi rỗng
        String characterName = profileOpt.map(CharacterProfileDocument::getCharacterName).orElse("");

        // Lấy giới tính nếu có, không thì trả chuỗi rỗng
        String gender = profileOpt.map(CharacterProfileDocument::getGender).orElse("");

        return new AdminUserResponse(
                user.getId(),                                             // id user
                user.getEmail(),                                          // email
                user.getUsername(),                                       // username
                characterName,                                            // tên nhân vật
                gender,                                                   // giới tính
                user.getRole() == null ? "USER" : user.getRole(),         // quyền
                !Boolean.FALSE.equals(user.getActive()),                  // trạng thái active
                user.getCreatedAt()                                       // ngày tạo
        );
    }

    /**
     * Chuyển MatchHistoryDocument thành AdminMatchResponse.
     *
     * Mục đích:
     * - Lấy thông tin chính của trận đấu
     * - Tách danh sách tên người chơi ra để frontend hiển thị
     *
     * Xử lý:
     * - Nếu match không có players thì trả về danh sách rỗng
     * - Bỏ các tên null hoặc rỗng
     *
     * @param match document lịch sử trận đấu
     * @return DTO match cho admin
     */
    private AdminMatchResponse toAdminMatchResponse(MatchHistoryDocument match) {
        // Lấy danh sách tên người chơi từ match history
        List<String> playerNames = match.getPlayers() == null
                ? List.of()
                : match.getPlayers().stream()
                .map(MatchHistoryDocument.PlayerMatchResult::getCharacterName) // lấy tên nhân vật
                .filter(Objects::nonNull)                                      // bỏ null
                .filter(name -> !name.isBlank())                               // bỏ chuỗi rỗng
                .toList();

        return new AdminMatchResponse(
                match.getId(),                 // id trận đấu
                match.getRoomCode(),           // mã phòng
                match.getWinnerUserId(),       // id người thắng
                match.getWinnerCharacterName(),// tên nhân vật thắng
                match.getStartedAt(),          // thời gian bắt đầu
                match.getEndedAt(),            // thời gian kết thúc
                playerNames.size(),            // số lượng người chơi
                playerNames                    // danh sách tên người chơi
        );
    }
}