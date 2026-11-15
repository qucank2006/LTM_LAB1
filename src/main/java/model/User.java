package model;

import java.io.Serializable;

// implements Serializable là bắt buộc để biến Object thành luồng byte truyền qua mạng
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private long id;
    private String email;
    private String displayName;
    private String username;
    private String ipAddress;
    private int peerPort; // Port mà Client này mở ra để chờ nhận file P2P

    public User(String username, String ipAddress, int peerPort) {
        this.username = username;
        this.ipAddress = ipAddress;
        this.peerPort = peerPort;
    }

    public void setAccount(long id, String email, String displayName) {
        this.id = id;
        this.email = email;
        this.displayName = displayName;
    }

    public long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName == null ? username : displayName;
    }

    // Các hàm Getter / Setter
    public String getUsername() {
        return username;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPeerPort() {
        return peerPort;
    }
}