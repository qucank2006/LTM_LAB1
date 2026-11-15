package model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private MessageType type;    // Loại tin nhắn
    private String sender;       // Người gửi
    private String receiver;     // Người nhận (hoặc "All", "Multicast", "Broadcast")
    private String content;      // Nội dung chat text

    // Dữ liệu tệp đính kèm / âm thanh / video
    private String fileName;
    private long fileSize;
    private byte[] fileData;

    // Biến phụ chứa danh sách User hoặc Object tùy chọn
    private Object payload;
    private String timestamp;

    public Message() {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public Message(MessageType type, String sender, String receiver) {
        this();
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
    }

    public Message(MessageType type, String sender, String receiver, String content) {
        this(type, sender, receiver);
        this.content = content;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public byte[] getFileData() { return fileData; }
    public void setFileData(byte[] fileData) { this.fileData = fileData; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
