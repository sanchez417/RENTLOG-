package com.rentlog;

import java.math.BigDecimal;

public class Apartment {

    public static final String STATUS_OCCUPIED = "Occupied";
    public static final String STATUS_VACANT = "Vacant";

    private String roomNumber;
    private BigDecimal monthlyRentalPrice;
    private String roomStatus; // Occupied or Vacant

    // Required by Gson for JSON deserialization
    public Apartment() {
    }

    public Apartment(String roomNumber, BigDecimal monthlyRentalPrice) {
        setRoomNumber(roomNumber);
        setMonthlyRentalPrice(monthlyRentalPrice);
        this.roomStatus = STATUS_VACANT;
    }

    // Getters
    public String getRoomNumber() {
        return roomNumber;
    }

    public BigDecimal getMonthlyRentalPrice() {
        return monthlyRentalPrice;
    }

    public String getRoomStatus() {
        return roomStatus == null ? STATUS_VACANT : roomStatus;
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

    // Changing the price only affects NEW leases; existing Rental records keep their own rent.
    public void setMonthlyRentalPrice(BigDecimal monthlyRentalPrice) {
        if (monthlyRentalPrice == null || monthlyRentalPrice.signum() <= 0) {
            throw new IllegalArgumentException(
                "Monthly rent must be greater than zero."
            );
        }

        this.monthlyRentalPrice = Payment.normalizeMoney(
                monthlyRentalPrice,
                "Monthly rent",
                false
        );
    }

    public void setRoomStatus(String roomStatus) {
        if (roomStatus == null) {
            throw new IllegalArgumentException(
                "Room status cannot be empty."
            );
        }

        String status = roomStatus.trim();

        if (status.equalsIgnoreCase(STATUS_OCCUPIED)) {
            this.roomStatus = STATUS_OCCUPIED;
        } else if (status.equalsIgnoreCase(STATUS_VACANT)) {
            this.roomStatus = STATUS_VACANT;
        } else {
            throw new IllegalArgumentException(
                "Invalid room status. Use Occupied or Vacant."
            );
        }
    }

    // Check room availability
    public boolean isVacant() {
        return STATUS_VACANT.equalsIgnoreCase(getRoomStatus());
    }

    public boolean isOccupied() {
        return STATUS_OCCUPIED.equalsIgnoreCase(getRoomStatus());
    }

    // Change room status
    public void markOccupied() {
        if (isOccupied()) {
            throw new IllegalStateException(
                "This room is already occupied."
            );
        }

        this.roomStatus = STATUS_OCCUPIED;
    }

    public void markVacant() {
        if (isVacant()) {
            throw new IllegalStateException(
                "This room is already vacant."
            );
        }

        this.roomStatus = STATUS_VACANT;
    }

    // Display room information
    public String getRoomDetails() {
        return String.format(
                "Room Number   : %s%n"
                + "Monthly Rent  : PHP%,.2f%n"
                + "Room Status   : %s",
                roomNumber,
                monthlyRentalPrice,
                getRoomStatus()
        );
    }
}