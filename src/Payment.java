package com.rentlog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * One payment toward one billing month. All billing math (balance and status)
 * lives in calculateBalance(); Main only supplies the numbers.
 */
public class Payment {

    public static final String STATUS_PAID = "Paid";
    public static final String STATUS_PARTIAL = "Partial";
    public static final String STATUS_UNPAID = "Unpaid";

    public static final String METHOD_CASH = "Cash";
    public static final String METHOD_BANK = "Bank Transfer";
    public static final String METHOD_GCASH = "GCash";
    public static final String METHOD_ADVANCE = "Advance Payment";

    private String paymentID;
    private String tenantID;
    private String billingMonth;          // YYYY-MM the payment is FOR
    private BigDecimal monthlyRent;       // rent for that month at the time of payment
    private BigDecimal amountPaid;
    private String paymentMethod;
    private String referenceNumber;       // GCash / bank reference
    private BigDecimal remainingBalance;
    private String paymentStatus;
    private String paymentDate;           // date the money was received

    // No-argument constructor for Gson JSON loading
    public Payment() {
    }

    // Main constructor
    public Payment(
            String paymentID,
            String tenantID,
            String billingMonth,
            BigDecimal monthlyRent,
            BigDecimal previouslyPaid,
            BigDecimal amountPaid,
            String paymentMethod,
            String referenceNumber,
            String paymentDate) {

        setPaymentID(paymentID);
        setTenantID(tenantID);
        setBillingMonth(billingMonth);
        setMonthlyRent(monthlyRent);
        setAmountPaid(amountPaid);
        setPaymentMethod(paymentMethod);
        setReferenceNumber(referenceNumber);
        setPaymentDate(paymentDate);

        calculateBalance(previouslyPaid);
    }

    // ==========================================
    // SHARED MONEY HELPERS
    // ==========================================

    // One place that validates and rounds money (also used by Apartment, Rental, Deposit)
    static BigDecimal normalizeMoney(
            BigDecimal amount,
            String fieldName,
            boolean allowZero) {

        if (amount == null) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    fieldName + " cannot be negative."
            );
        }

        if (!allowZero && amount.signum() == 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be greater than zero."
            );
        }

        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean isValidMethod(String method) {
        return method != null && canonicalMethod(method.trim()) != null;
    }

    // GCash and bank transfers need a reference number so they can be matched to the real transfer
    public static boolean requiresReference(String method) {
        String canonical = method == null ? null : canonicalMethod(method.trim());

        return METHOD_GCASH.equals(canonical) || METHOD_BANK.equals(canonical);
    }

    private static String canonicalMethod(String method) {

        if (method.equalsIgnoreCase(METHOD_CASH)) {
            return METHOD_CASH;
        } else if (method.equalsIgnoreCase(METHOD_BANK)) {
            return METHOD_BANK;
        } else if (method.equalsIgnoreCase(METHOD_GCASH)) {
            return METHOD_GCASH;
        } else if (method.equalsIgnoreCase(METHOD_ADVANCE)
                || method.equalsIgnoreCase("Advance")) {
            return METHOD_ADVANCE;
        }

        return null;
    }

    // ==========================================
    // BILLING LOGIC
    // ==========================================

    // Remaining balance = rent - everything already paid for this month - this payment
    public void calculateBalance(BigDecimal previouslyPaid) {

        BigDecimal before = normalizeMoney(
                previouslyPaid,
                "Previously paid amount",
                true
        );

        BigDecimal owed = monthlyRent.subtract(before);

        if (amountPaid.compareTo(owed) > 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "Payment cannot be greater than the remaining balance of PHP%,.2f.",
                            owed.max(BigDecimal.ZERO)
                    )
            );
        }

        this.remainingBalance = owed.subtract(amountPaid)
                .setScale(2, RoundingMode.HALF_UP);

        updatePaymentStatus();
    }

    // Updates the payment status based on the current amounts
    public void updatePaymentStatus() {

        if (amountPaid == null || amountPaid.signum() <= 0) {
            this.paymentStatus = STATUS_UNPAID;
        } else if (remainingBalance == null || remainingBalance.signum() <= 0) {
            this.remainingBalance = BigDecimal.ZERO.setScale(2);
            this.paymentStatus = STATUS_PAID;
        } else {
            this.paymentStatus = STATUS_PARTIAL;
        }
    }

    // ==========================================
    // GETTERS AND SETTERS
    // ==========================================

    // Payment ID
    public String getPaymentID() {
        return paymentID;
    }

    public void setPaymentID(String paymentID) {

        if (paymentID == null || paymentID.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Payment ID cannot be empty."
            );
        }

        this.paymentID = paymentID.trim();
    }

    // Tenant ID
    public String getTenantID() {
        return tenantID;
    }

    public void setTenantID(String tenantID) {

        if (tenantID == null || tenantID.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Tenant ID cannot be empty."
            );
        }

        this.tenantID = tenantID.trim();
    }

    // Billing month
    public String getBillingMonth() {
        return billingMonth;
    }

    public void setBillingMonth(String billingMonth) {

        if (billingMonth == null || billingMonth.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Billing month cannot be empty."
            );
        }

        try {
            this.billingMonth = YearMonth.parse(billingMonth.trim()).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid billing month. Use YYYY-MM."
            );
        }
    }

    // The month this payment counts toward. Old records without a billing month
    // fall back to the month of the payment date. Returns null if the data is invalid.
    public YearMonth getBillingYearMonth() {

        try {
            if (billingMonth != null) {
                return YearMonth.parse(billingMonth);
            }

            return YearMonth.from(LocalDate.parse(paymentDate));

        } catch (DateTimeParseException | NullPointerException e) {
            return null;
        }
    }

    // Monthly rent at the time of payment
    public BigDecimal getMonthlyRent() {
        return monthlyRent;
    }

    public void setMonthlyRent(BigDecimal monthlyRent) {
        this.monthlyRent = normalizeMoney(monthlyRent, "Monthly rent", false);
    }

    // Amount paid
    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = normalizeMoney(amountPaid, "Amount paid", false);
    }

    // Payment method
    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {

        if (paymentMethod == null || paymentMethod.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Payment method cannot be empty."
            );
        }

        String canonical = canonicalMethod(paymentMethod.trim());

        if (canonical == null) {
            throw new IllegalArgumentException(
                    "Invalid payment method. Use Cash, Bank Transfer, GCash, or Advance Payment."
            );
        }

        this.paymentMethod = canonical;
    }

    // Reference number
    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {

        String reference = referenceNumber == null ? "" : referenceNumber.trim();

        if (reference.isEmpty() && requiresReference(paymentMethod)) {
            throw new IllegalArgumentException(
                    paymentMethod + " payments need a reference number."
            );
        }

        this.referenceNumber = reference.isEmpty() ? null : reference;
    }

    // Remaining balance (computed by calculateBalance)
    public BigDecimal getRemainingBalance() {
        return remainingBalance;
    }

    // Payment status (computed by calculateBalance)
    public String getPaymentStatus() {
        return paymentStatus;
    }

    // Payment date
    public String getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(String paymentDate) {

        if (paymentDate == null || paymentDate.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Payment date cannot be empty."
            );
        }

        try {
            LocalDate.parse(paymentDate.trim());
            this.paymentDate = paymentDate.trim();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "Invalid date. Use YYYY-MM-DD."
            );
        }
    }

    // Displays the payment receipt
    public String getPaymentReceipt() {

        YearMonth month = getBillingYearMonth();

        String receipt = String.format(
                Locale.US,
                "Payment ID        : %s%n"
                + "Tenant ID         : %s%n"
                + "Billing Month     : %s%n"
                + "Monthly Rent      : PHP%,.2f%n"
                + "Amount Paid       : PHP%,.2f%n"
                + "Payment Method    : %s%n",
                paymentID,
                tenantID,
                month == null ? "Unknown" : month.toString(),
                monthlyRent,
                amountPaid,
                paymentMethod
        );

        if (referenceNumber != null) {
            receipt += String.format(
                    "Reference No.     : %s%n",
                    referenceNumber
            );
        }

        receipt += String.format(
                Locale.US,
                "Remaining Balance : PHP%,.2f%n"
                + "Payment Status    : %s%n"
                + "Payment Date      : %s",
                remainingBalance,
                paymentStatus,
                paymentDate
        );

        return receipt;
    }
}