package com.rentlog;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/**
 * One lease per tenant. Stores the rent agreed on at move-in (a snapshot),
 * so later changes to the Apartment price do not rewrite history.
 */
public class Rental {

    private String tenantID;
    private String roomNumber;
    private BigDecimal monthlyRent;
    private int dueDay;                 // day of the month rent is due (1-31)
    private String leaseStartDate;
    private String leaseExpirationDate;

    // No-argument constructor for Gson
    public Rental() {
    }

    // Main constructor
    public Rental(
            String tenantID,
            String roomNumber,
            BigDecimal monthlyRent,
            int dueDay,
            String leaseStartDate,
            String leaseExpirationDate) {

        setTenantID(tenantID);
        setRoomNumber(roomNumber);
        setMonthlyRent(monthlyRent);
        setDueDay(dueDay);
        setLeaseStartDate(leaseStartDate);
        setLeaseExpirationDate(leaseExpirationDate);

        validateLeaseDates();
    }

    // Tenant ID
    public String getTenantID() {
        return tenantID;
    }

    public void setTenantID(String tenantID) {
        if (tenantID == null || tenantID.trim().isEmpty()) {
            throw new IllegalArgumentException("Tenant ID cannot be empty.");
        }

        this.tenantID = tenantID.trim();
    }

    // Room number
    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        if (roomNumber == null || roomNumber.trim().isEmpty()) {
            throw new IllegalArgumentException("Room number cannot be empty.");
        }

        this.roomNumber = roomNumber.trim();
    }

    // Monthly rent
    public BigDecimal getMonthlyRent() {
        return monthlyRent;
    }

    public void setMonthlyRent(BigDecimal monthlyRent) {
        if (monthlyRent == null || monthlyRent.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Monthly rent must be greater than zero."
            );
        }

        this.monthlyRent = Payment.normalizeMoney(
                monthlyRent,
                "Monthly rent",
                false
        );
    }

    // Due day of the month
    public int getDueDay() {
        return dueDay;
    }

    public void setDueDay(int dueDay) {
        if (dueDay < 1 || dueDay > 31) {
            throw new IllegalArgumentException(
                    "Due day must be between 1 and 31."
            );
        }

        this.dueDay = dueDay;
    }

    // Lease dates
    public String getLeaseStartDate() {
        return leaseStartDate;
    }

    public void setLeaseStartDate(String leaseStartDate) {
        this.leaseStartDate = validateDate(
                leaseStartDate,
                "Lease start date"
        );
    }

    public String getLeaseExpirationDate() {
        return leaseExpirationDate;
    }

    public void setLeaseExpirationDate(String leaseExpirationDate) {
        this.leaseExpirationDate = validateDate(
                leaseExpirationDate,
                "Lease expiration date"
        );
    }

    // Validates date format
    private String validateDate(String date, String fieldName) {

        if (date == null || date.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be empty."
            );
        }

        try {
            LocalDate.parse(date.trim());
            return date.trim();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    fieldName + " must use YYYY-MM-DD format."
            );
        }
    }

    // The lease must end after it starts
    private void validateLeaseDates() {

        LocalDate start = LocalDate.parse(leaseStartDate);
        LocalDate end = LocalDate.parse(leaseExpirationDate);

        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "Lease expiration must be after the lease start date."
            );
        }
    }

    // Returns the current lease status
    public String checkLeaseStatus() {

        LocalDate expirationDate = LocalDate.parse(leaseExpirationDate);

        if (expirationDate.isBefore(LocalDate.now())) {
            return "Expired";
        }

        return "Active";
    }

    // True when the lease is still active but ends within the given number of days
    public boolean isExpiringWithin(int days) {

        LocalDate today = LocalDate.now();
        LocalDate expirationDate = LocalDate.parse(leaseExpirationDate);

        return !expirationDate.isBefore(today)
                && !expirationDate.isAfter(today.plusDays(days));
    }

    // Due date of a given month. Day 31 becomes Feb 28/29, Apr 30, etc.
    public LocalDate dueDateFor(YearMonth month) {

        return month.atDay(Math.min(dueDay, month.lengthOfMonth()));
    }

    public String calculateNextDueDate() {
        return calculateNextDueDate(LocalDate.now());
    }

    // Takes "today" as a parameter so the date logic is easy to test.
    // If today IS the due date, today is still the next due date.
    public String calculateNextDueDate(LocalDate today) {

        YearMonth month = YearMonth.from(today);
        LocalDate nextDueDate = dueDateFor(month);

        if (nextDueDate.isBefore(today)) {
            nextDueDate = dueDateFor(month.plusMonths(1));
        }

        if (nextDueDate.isAfter(LocalDate.parse(leaseExpirationDate))) {
            return "None (lease ends " + leaseExpirationDate + ")";
        }

        return nextDueDate.toString();
    }

    // Displays rental information
    public String getRentalSummary() {

        return String.format(
                "Monthly Rent      : PHP%,.2f%n"
                + "Rent Due Day      : %d of every month%n"
                + "Next Due Date     : %s%n"
                + "Lease Start       : %s%n"
                + "Lease Expiration  : %s%n"
                + "Lease Status      : %s",
                monthlyRent,
                dueDay,
                calculateNextDueDate(),
                leaseStartDate,
                leaseExpirationDate,
                checkLeaseStatus()
        );
    }
}