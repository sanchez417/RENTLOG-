package com.rentlog;

/**
 * Base class for everyone who can log in (Admin, Tenant).
 * Login data (username + password) lives here so that
 * authenticate() can be called polymorphically on any User reference.
 */
public abstract class User {

    private static final int MIN_PASSWORD_LENGTH = 6;

    protected String userID;
    protected String fullName;
    protected String contactNumber;

    protected String username;
    protected String password;

    // No-argument constructor for Gson
    public User() {
        this.userID = "";
        this.fullName = "";
        this.contactNumber = "";
        this.username = "";
        this.password = "";
    }

    // Normal constructor
    public User(
            String userID,
            String fullName,
            String contactNumber,
            String username,
            String password) {

        setUserID(userID);
        setFullName(fullName);
        setContactNumber(contactNumber);
        setUsername(username);
        setPassword(password);
    }

    // ==========================================
    // STATIC VALIDATION (so Main can check BEFORE constructing objects)
    // ==========================================

    public static boolean isValidContactNumber(String contactNumber) {
        return contactNumber != null
                && contactNumber.trim().matches("[0-9+()\\- ]{7,20}");
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }

    // Getters
    public String getUserID() {
        return userID;
    }

    public String getFullName() {
        return fullName;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public String getUsername() {
        return username;
    }

    // Setters
    public void setUserID(String userID) {
        if (userID == null || userID.trim().isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be empty.");
        }

        this.userID = userID.trim();
    }

    public void setFullName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be empty.");
        }

        this.fullName = fullName.trim();
    }

    public void setContactNumber(String contactNumber) {
        if (contactNumber == null || contactNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Contact number cannot be empty.");
        }

        if (!isValidContactNumber(contactNumber)) {
            throw new IllegalArgumentException("Invalid contact number format.");
        }

        this.contactNumber = contactNumber.trim();
    }

    public void setUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be empty.");
        }

        this.username = username.trim();
    }

    public void setPassword(String password) {
        if (!isValidPassword(password)) {
            throw new IllegalArgumentException(
                    "Password must contain at least "
                            + MIN_PASSWORD_LENGTH + " characters."
            );
        }

        this.password = password;
    }

    public boolean verifyPassword(String enteredPassword) {
        return enteredPassword != null
                && password != null
                && password.equals(enteredPassword);
    }

    // Polymorphic login check. Subclasses can add extra rules (e.g. Tenant must be active).
    public boolean authenticate(String enteredUsername, String enteredPassword) {
        if (enteredUsername == null || enteredPassword == null) {
            return false;
        }

        return username != null
                && username.equalsIgnoreCase(enteredUsername.trim())
                && verifyPassword(enteredPassword);
    }

    // Abstract method (polymorphism)
    public abstract String getRoleDetails();
}