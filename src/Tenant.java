package com.rentlog;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class Tenant extends User implements Reportable {

    public static final String STATUS_ACTIVE = "Active";
    public static final String STATUS_INACTIVE = "Inactive";

    private String roomNumber;
    private String moveInDate;
    private String moveOutDate;   // null while the tenant is still living there
    private String status;        // Active / Inactive (old JSON files without it count as Active)

    // No-argument constructor for Gson
    public Tenant() {
        super();
        this.roomNumber = "";
        this.moveInDate = "";
        this.status = STATUS_ACTIVE;
    }

    // Normal constructor
    public Tenant(
            String tenantID,
            String fullName,
            String contactNumber,
            String roomNumber,
            String moveInDate,
            String username,
            String password) {

        super(tenantID, fullName, contactNumber, username, password);

        setRoomNumber(roomNumber);
        setMoveInDate(moveInDate);
        this.status = STATUS_ACTIVE;
    }

    // Getters
    public String getTenantID() {
        return userID;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public String getMoveInDate() {
        return moveInDate;
    }

    public String getMoveOutDate() {
        return moveOutDate;
    }

    public String getStatus() {
        return isActive() ? STATUS_ACTIVE : STATUS_INACTIVE;
    }

    public boolean isActive() {
        return status == null || STATUS_ACTIVE.equalsIgnoreCase(status);
    }

    // Setters with validation
    public void setRoomNumber(String roomNumber) {
        if (roomNumber == null || roomNumber.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Room number cannot be empty."
            );
        }

        this.roomNumber = roomNumber.trim();
    }

    public void setMoveInDate(String moveInDate) {
        if (moveInDate == null || moveInDate.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Move-in date cannot be empty."
            );
        }

        try {
            LocalDate.parse(moveInDate.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                "Invalid move-in date. Use YYYY-MM-DD."
            );
        }

        this.moveInDate = moveInDate.trim();
    }

    public void updateContact(String newContact) {
        setContactNumber(newContact);
    }

    // Marks the tenant as moved out but keeps the record (name, history)
    public void moveOut(String moveOutDate) {
        if (!isActive()) {
            throw new IllegalStateException(
                "This tenant has already moved out."
            );
        }

        if (moveOutDate == null || moveOutDate.trim().isEmpty()) {
            throw new IllegalArgumentException(
                "Move-out date cannot be empty."
            );
        }

        LocalDate out;

        try {
            out = LocalDate.parse(moveOutDate.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                "Invalid move-out date. Use YYYY-MM-DD."
            );
        }

        if (out.isBefore(LocalDate.parse(moveInDate))) {
            throw new IllegalArgumentException(
                "Move-out date cannot be before the move-in date."
            );
        }

        this.moveOutDate = out.toString();
        this.status = STATUS_INACTIVE;
    }

    // Moved-out tenants can no longer log in
    @Override
    public boolean authenticate(String enteredUsername, String enteredPassword) {
        return isActive() && super.authenticate(enteredUsername, enteredPassword);
    }

    // OOP: Polymorphism
    @Override
    public String getRoleDetails() {
        return String.format(
                "Role          : Tenant%n"
                + "Tenant ID     : %s%n"
                + "Full Name     : %s",
                userID,
                fullName
        );
    }

    // Interface implementation
    @Override
    public String getReportSummary() {
        return String.format(
                "Tenant ID     : %s%n"
                + "Full Name     : %s%n"
                + "Room Number   : %s%n"
                + "Move-In Date  : %s%n"
                + "Status        : %s",
                userID,
                fullName,
                roomNumber,
                moveInDate,
                getStatus()
        );
    }

    // Tenant information
    public String getTenantInfo() {
        String info = String.format(
                "Tenant ID       : %s%n"
                + "Full Name       : %s%n"
                + "Contact Number  : %s%n"
                + "Room Number     : %s%n"
                + "Move-In Date    : %s%n"
                + "Username        : %s%n"
                + "Status          : %s",
                userID,
                fullName,
                contactNumber,
                roomNumber,
                moveInDate,
                username,
                getStatus()
        );

        if (moveOutDate != null) {
            info += String.format("%nMove-Out Date   : %s", moveOutDate);
        }

        return info;
    }
}