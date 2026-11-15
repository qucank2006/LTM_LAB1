package model;

public enum MessageType {
    PING, PONG, REGISTER, AUTH_RESPONSE, LOGOUT, ERROR,
    LOGIN,              // Báo danh khi Client mới bật lên
    TEXT_MESSAGE,       // Gửi tin nhắn chat bình thường
    GET_USER_LIST,      // Client xin danh sách user đang online
    USER_LIST_RESPONSE, // Server trả về danh sách user
    GET_IP_REQUEST,     // Client A xin IP của Client B để chuẩn bị gửi file
    GET_IP_RESPONSE,    // Server trả về IP và Port của Client B
    FILE_TRANSFER,      // Gói tin chứa dữ liệu file truyền P2P
    AUDIO_MESSAGE,      // Tin nhắn thoại / Voice note ghi âm (.wav)
    VIDEO_MESSAGE,      // Tin nhắn video (.mp4, .avi)
    MULTICAST_MESSAGE,  // Tin nhắn nhóm UDP Multicast (230.0.0.1:8888)
    BROADCAST_MESSAGE   // Tin nhắn toàn mạng UDP Broadcast (Port 9999)
}
