package com.bomberserver.backend.dto;

/**
 * DTO dùng cho message mà frontend gửi lên WebSocket game / room.
 *
 * Đây là message tổng quát cho nhiều hành động khác nhau,
 * ví dụ:
 * - di chuyển
 * - đặt bom
 * - dùng item
 * - tạo phòng
 * - vào phòng
 * - kick thành viên
 * - xóa bot
 */
public class ClientMessage {

    /**
     * Loại hành động mà client gửi lên server.
     *
     * Ví dụ:
     * - move
     * - bomb
     * - use_item
     * - create_room
     * - join_room
     * - leave_room
     * - start_room
     * - add_bot
     * - remove_bot
     */
    public String type;

    /**
     * Hướng di chuyển của người chơi.
     *
     * Ví dụ:
     * - up
     * - down
     * - left
     * - right
     *
     * Trường này thường dùng khi type là move.
     */
    public String direction;

    /**
     * Chỉ số slot vật phẩm trong inventory.
     *
     * Dùng khi người chơi chọn dùng item.
     */
    public Integer slotIndex; // slot vat pham

    /**
     * Mã phòng.
     *
     * Dùng khi:
     * - vào phòng
     * - thao tác trong phòng cụ thể
     */
    public String roomCode;

    /**
     * Tên phòng.
     *
     * Dùng khi tạo phòng.
     */
    public String roomName;

    /**
     * Số người chơi tối đa của phòng.
     */
    public Integer maxPlayers;

    /**
     * Cho biết phòng có phải phòng riêng tư hay không.
     *
     * true  = private
     * false = public
     */
    public Boolean isPrivate;

    /**
     * Chế độ trận đấu.
     *
     * Ví dụ:
     * - SOLO
     * - DUO
     */
    public String matchMode;

    /**
     * ID client mục tiêu.
     *
     * Dùng cho các hành động như:
     * - kick member
     * - remove bot
     */
    public String targetClientId;
}