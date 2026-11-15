package model;

import java.io.Serializable;

/**
 * Credentials travel only in authentication requests; never in User.
 */
public record AuthRequest(String username, String email, String displayName,
                          String password, int peerPort) implements Serializable {
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return "AuthRequest[credentials omitted]";
    }
}
