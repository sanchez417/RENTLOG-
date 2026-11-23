package com.rentlog;

import java.math.BigDecimal;

public class Deposit implements Reportable {

    public static final String STATUS_HELD = "Held";
    public static final String STATUS_APPLIED = "Applied";
    public static final String STATUS_REFUNDED = "Refunded";

    private String tenantID;
    private BigDecimal securityDeposit;
    private BigDecimal advancePayment;   // original advance paid
    private BigDecimal advanceUsed;      // part of the advance already used for rent
    private String depositStatus;

    // No-argument constructor for Gson
    public Deposit() {
    }

    // Main constructor
    public Deposit(
            String tenantID,
            BigDecimal securityDeposit,
            BigDecimal advancePayment) {

        setTenantID(tenantID);
        setSecurityDeposit(securityDeposit);
        setAdvancePayment(advancePayment);

        this.advanceUsed = BigDecimal.ZERO.setScale(2);
        this.depositStatus = STATUS_HELD;
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

    // Security deposit
    public BigDecimal getSecurityDeposit() {
        return securityDeposit;
    }

    public void setSecurityDeposit(BigDecimal securityDeposit) {
        this.securityDeposit = Payment.normalizeMoney(
                securityDeposit,
                "Security deposit",
                true
        );
    }

    // Advance payment
    public BigDecimal getAdvancePayment() {
        return advancePayment;
    }

    public void setAdvancePayment(BigDecimal advancePayment) {
        this.advancePayment = Payment.normalizeMoney(
                advancePayment,
                "Advance payment",
                true
        );
    }

    // Advance that has been used to pay rent (old JSON files do not have it)
    public BigDecimal getAdvanceUsed() {
        return advanceUsed == null ? BigDecimal.ZERO : advanceUsed;
    }

    public BigDecimal getAdvanceRemaining() {
        return advancePayment.subtract(getAdvanceUsed());
    }

    // Uses part of the advance payment to pay rent
    public void useAdvance(BigDecimal amount) {

        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be greater than zero."
            );
        }

        if (!STATUS_HELD.equalsIgnoreCase(getDepositStatus())) {
            throw new IllegalStateException(
                    "The advance payment can only be used while the deposit is Held."
            );
        }

        if (amount.compareTo(getAdvanceRemaining()) > 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "Not enough advance payment left (PHP%,.2f available).",
                            getAdvanceRemaining()
                    )
            );
        }

        this.advanceUsed = Payment.normalizeMoney(
                getAdvanceUsed().add(amount),
                "Advance used",
                true
        );
    }

    // Deposit status
    public String getDepositStatus() {
        return depositStatus == null ? STATUS_HELD : depositStatus;
    }

    public void setDepositStatus(String depositStatus) {

        if (depositStatus == null) {
            throw new IllegalArgumentException(
                    "Deposit status cannot be empty."
            );
        }

        String status = depositStatus.trim();

        if (status.equalsIgnoreCase(STATUS_HELD)) {
            this.depositStatus = STATUS_HELD;
        } else if (status.equalsIgnoreCase(STATUS_APPLIED)) {
            this.depositStatus = STATUS_APPLIED;
        } else if (status.equalsIgnoreCase(STATUS_REFUNDED)) {
            this.depositStatus = STATUS_REFUNDED;
        } else {
            throw new IllegalArgumentException(
                    "Invalid deposit status. Use Held, Applied, or Refunded."
            );
        }
    }

    // Applies the deposit (e.g. to unpaid rent or damages at move-out)
    public void applyDeposit() {

        if (STATUS_REFUNDED.equalsIgnoreCase(getDepositStatus())) {
            throw new IllegalStateException(
                    "A refunded deposit cannot be applied."
            );
        }

        if (STATUS_APPLIED.equalsIgnoreCase(getDepositStatus())) {
            throw new IllegalStateException(
                    "This deposit has already been applied."
            );
        }

        this.depositStatus = STATUS_APPLIED;
    }

    // Marks the deposit as refunded
    public void refundDeposit() {

        if (STATUS_APPLIED.equalsIgnoreCase(getDepositStatus())) {
            throw new IllegalStateException(
                    "An applied deposit cannot be refunded."
            );
        }

        if (STATUS_REFUNDED.equalsIgnoreCase(getDepositStatus())) {
            throw new IllegalStateException(
                    "This deposit has already been refunded."
            );
        }

        this.depositStatus = STATUS_REFUNDED;
    }

    // Original total (security deposit + advance payment)
    public BigDecimal getTotalDepositAmount() {
        return securityDeposit.add(advancePayment);
    }

    // What the tenant would get back right now
    public BigDecimal getRefundableAmount() {

        if (!STATUS_HELD.equalsIgnoreCase(getDepositStatus())) {
            return BigDecimal.ZERO;
        }

        return securityDeposit.add(getAdvanceRemaining());
    }

    // Displays deposit information
    public String getDepositSummary() {

        return String.format(
                "Tenant ID          : %s%n"
                + "Security Deposit   : PHP%,.2f%n"
                + "Advance Payment    : PHP%,.2f%n"
                + "Advance Remaining  : PHP%,.2f%n"
                + "Total Amount       : PHP%,.2f%n"
                + "Refundable Now     : PHP%,.2f%n"
                + "Deposit Status     : %s",
                tenantID,
                securityDeposit,
                advancePayment,
                getAdvanceRemaining(),
                getTotalDepositAmount(),
                getRefundableAmount(),
                getDepositStatus()
        );
    }

    // Reportable
    @Override
    public String getReportSummary() {
        return getDepositSummary();
    }
}