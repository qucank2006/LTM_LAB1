package model;

import java.io.Serializable;

public record AuthResult(boolean success, String message, User user) implements Serializable {
    private static final long serialVersionUID = 1L;
}
