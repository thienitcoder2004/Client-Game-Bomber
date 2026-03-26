package com.bomberserver.backend.dto;

/**
 * DTO dùng cho dữ liệu server gửi về frontend qua WebSocket.
 *
 * Cấu trúc chung:
 * - type : loại message
 * - data : dữ liệu đi kèm
 *
 * Dùng để đồng bộ nhiều loại response khác nhau từ server.
 */
public class ServerMessage {

    /**
     * Loại message server gửi về.
     *
     * Ví dụ:
     * - init
     * - state
     * - error
     * - room_created
     * - room_state
     * - room_started
     */
    public String type;

    /**
     * Dữ liệu đi kèm message.
     *
     * Dùng Object để có thể chứa nhiều kiểu dữ liệu khác nhau
     * tùy theo từng loại message.
     */
    public Object data;

    /**
     * Constructor rỗng.
     */
    public ServerMessage() {
    }

    /**
     * Constructor đầy đủ.
     *
     * @param type loại message
     * @param data dữ liệu đi kèm
     */
    public ServerMessage(String type, Object data) {
        this.type = type;
        this.data = data;
    }
}