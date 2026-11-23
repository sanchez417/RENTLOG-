package com.rentlog;

public class Admin extends User {

    // No-argument constructor for Gson
    public Admin() {
        super();
    }

    public Admin(
            String userID,
            String fullName,
            String contactNumber,
            String username,
            String password) {

        super(userID, fullName, contactNumber, username, password);
    }

    // Login verification (delegates to the shared User logic)
    public boolean verifyLogin(String enteredUsername, String enteredPassword) {
        return authenticate(enteredUsername, enteredPassword);
    }

    @Override
    public String getRoleDetails() {
        return String.format(
                "Role       : Landlord / Administrator%n"
                + "Username   : %s",
                username
        );
    }
}