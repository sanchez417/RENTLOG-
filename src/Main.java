package com.rentlog;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.TreeMap;

public class Main {

    // ==========================================
    // CONSTANTS
    // ==========================================

    private static final int MAX_LOGIN_ATTEMPTS = 3;
    private static final long LOCKOUT_MILLIS = 60_000L;
    private static final int LEASE_WARNING_DAYS = 30;
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final String LINE =
            "==================================================";
    private static final String THIN_LINE =
            "--------------------------------------------------";

    // ==========================================
    // DATA COLLECTIONS
    // ==========================================

    private static List<Tenant> tenants = new ArrayList<>();
    private static List<Apartment> apartments = new ArrayList<>();
    private static List<Payment> payments = new ArrayList<>();
    private static List<Deposit> deposits = new ArrayList<>();
    private static List<Admin> admins = new ArrayList<>();
    private static List<Rental> rentals = new ArrayList<>();

    private static final Gson gson =
            new GsonBuilder().setPrettyPrinting().create();

    private static final Scanner scanner = new Scanner(System.in);

    // Login protection. Kept for the whole session, so leaving and
    // re-entering a portal does NOT reset the attempt counter.
    private static final Map<String, Integer> loginFailures = new HashMap<>();
    private static final Map<String, Long> lockUntil = new HashMap<>();

    // ==========================================
    // MAIN METHOD
    // ==========================================

    public static void main(String[] args) {

        loadData();
        initializeDefaultData();
        migrateData();

        int portalChoice = 0;

        do {
            System.out.println("\n" + LINE);
            System.out.println("          WELCOME TO RENTLOG");
            System.out.println(LINE);
            System.out.println("1. Landlord / Admin Portal");
            System.out.println("2. Tenant Portal (View Mode)");
            System.out.println("3. Exit System");

            try {
                portalChoice = readInt("Enter choice (1-3): ", 1, 3);

                switch (portalChoice) {

                    case 1:
                        runAdminPortal();
                        break;

                    case 2:
                        runTenantPortal();
                        break;

                    case 3:
                        saveAllData();
                        System.out.println("\nThank you for using RENTLOG!");
                        System.out.println("Exiting system. Goodbye!");
                        break;
                }

            } catch (NoSuchElementException e) {

                // Input stream closed: save and stop instead of looping forever.
                saveAllData();
                System.out.println("\nInput closed. Data saved. Goodbye!");
                portalChoice = 3;

            } catch (RuntimeException e) {

                System.out.println("Error: " + e.getMessage());
            }

        } while (portalChoice != 3);

        scanner.close();
    }

    // ==========================================
    // INITIAL DATA (only when files do not exist yet)
    // ==========================================

    private static boolean canSeed(String fileName) {
        return !Files.exists(Paths.get(fileName));
    }

    private static void initializeDefaultData() {

        boolean changed = false;

        // Admin: also re-created if the list is empty, otherwise nobody could log in.
        if (admins.isEmpty()) {

            Admin admin = new Admin(
                    "A001",
                    "Property Manager",
                    "09123456789",
                    "admin",
                    DEFAULT_ADMIN_PASSWORD
            );

            admins.add(admin);
            changed = true;
        }

        // Demo apartments and tenant: ONLY on the very first run (files missing).
        boolean seedApartments = canSeed("apartments.json");
        boolean seedTenant = canSeed("tenants.json");

        if (seedApartments) {

            apartments.add(new Apartment("101", money("5000.00")));
            apartments.add(new Apartment("102", money("5500.00")));
            apartments.add(new Apartment("201", money("6000.00")));
            changed = true;
        }

        if (seedTenant && !isTenantIdTaken("T001")) {

            Apartment room = findApartmentByRoom("101");

            if (room != null && room.isVacant()) {

                tenants.add(new Tenant(
                        "T001",
                        "Juan Dela Cruz",
                        "09123456789",
                        "101",
                        "2026-11-08",
                        "tenant1",
                        "password123"
                ));

                room.markOccupied();

                deposits.add(new Deposit(
                        "T001",
                        money("5000.00"),
                        money("5000.00")
                ));

                changed = true;
            }
        }

        if (changed) {
            saveAllData();
        }
    }

    // ==========================================
    // MIGRATION OF OLD JSON FILES
    // ==========================================

    private static void migrateData() {

        boolean changed = false;

        // 1. Active tenants that have no lease record yet
        for (Tenant tenant : tenants) {

            if (!tenant.isActive()
                    || findRentalByTenant(tenant.getTenantID()) != null) {
                continue;
            }

            Apartment room = findApartmentByRoom(tenant.getRoomNumber());

            if (room == null) {
                continue;
            }

            try {

                LocalDate moveIn = LocalDate.parse(tenant.getMoveInDate());

                rentals.add(new Rental(
                        tenant.getTenantID(),
                        tenant.getRoomNumber(),
                        room.getMonthlyRentalPrice(),
                        moveIn.getDayOfMonth(),
                        moveIn.toString(),
                        moveIn.plusYears(1).toString()
                ));

                changed = true;

            } catch (RuntimeException e) {

                System.out.println(
                        "Notice: could not create a lease for tenant "
                                + tenant.getTenantID() + ": " + e.getMessage()
                );
            }
        }

        // 2. Old payments: fill in billing month and the rent snapshot
        for (Payment payment : payments) {

            try {

                if (payment.getBillingMonth() == null
                        && payment.getBillingYearMonth() != null) {

                    payment.setBillingMonth(
                            payment.getBillingYearMonth().toString()
                    );

                    changed = true;
                }

                if (payment.getMonthlyRent() == null) {

                    Rental rental = findRentalByTenant(payment.getTenantID());

                    BigDecimal rent = rental != null
                            ? rental.getMonthlyRent()
                            : payment.getAmountPaid()
                                .add(payment.getRemainingBalance());

                    payment.setMonthlyRent(rent);
                    changed = true;
                }

            } catch (RuntimeException e) {

                System.out.println(
                        "Notice: payment " + payment.getPaymentID()
                                + " could not be upgraded: " + e.getMessage()
                );
            }
        }

        if (changed) {
            saveAllData();
        }
    }

    // ==========================================
    // HELPERS: MONEY
    // ==========================================

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String peso(BigDecimal amount) {
        return String.format(Locale.US, "PHP%,.2f", amount);
    }

    // ==========================================
    // INPUT VALIDATION
    // ==========================================

    private static String readRequired(String prompt) {

        while (true) {

            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            if (!input.isEmpty()) {
                return input;
            }

            System.out.println("Error: This field cannot be empty.");
        }
    }

    private static boolean confirm(String prompt) {

        System.out.print(prompt);

        return scanner.nextLine().trim().equalsIgnoreCase("Y");
    }

    private static int readInt(String prompt, int min, int max) {

        while (true) {

            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            try {

                int value = Integer.parseInt(input);

                if (value >= min && value <= max) {
                    return value;
                }

                System.out.println(
                        "Error: Enter a number between "
                                + min + " and " + max + "."
                );

            } catch (NumberFormatException e) {

                System.out.println(
                        "Error: Please enter a valid whole number."
                );
            }
        }
    }

    private static BigDecimal readAmount(String prompt, boolean allowZero) {

        while (true) {

            System.out.print(prompt);
            String input = scanner.nextLine().trim().replace(",", "");

            try {

                BigDecimal amount = new BigDecimal(input)
                        .setScale(2, RoundingMode.HALF_UP);

                if (amount.signum() < 0) {
                    System.out.println("Error: Amount cannot be negative.");
                    continue;
                }

                if (!allowZero && amount.signum() == 0) {
                    System.out.println(
                            "Error: Amount must be greater than zero."
                    );
                    continue;
                }

                return amount;

            } catch (NumberFormatException e) {

                System.out.println("Error: Please enter a valid amount.");
            }
        }
    }

    private static LocalDate readDate(String prompt) {

        while (true) {

            String input = readRequired(prompt);

            try {

                return LocalDate.parse(input);

            } catch (DateTimeParseException e) {

                System.out.println("Invalid date! Use YYYY-MM-DD format.");
            }
        }
    }

    // Reads a contact number and re-asks until it passes the real validation
    private static String readValidContact(String prompt) {

        while (true) {

            String contact = readRequired(prompt);

            if (User.isValidContactNumber(contact)) {
                return contact;
            }

            System.out.println(
                    "Error: Invalid contact number. Use 7-20 digits, "
                            + "spaces, +, ( ), or -."
            );
        }
    }

    // Reads a password without echoing it when a real console is available
    private static String readPassword(String prompt) {

        java.io.Console console = System.console();

        while (true) {

            String input;

            if (console != null) {

                char[] chars = console.readPassword(prompt);
                input = chars == null ? "" : new String(chars);

                if (chars != null) {
                    java.util.Arrays.fill(chars, ' ');
                }

            } else {

                // IDE consoles have no hidden input, so the text will be visible.
                System.out.print(prompt);
                input = scanner.nextLine();
            }

            if (!input.isEmpty()) {
                return input;
            }

            System.out.println("Error: This field cannot be empty.");
        }
    }

    // Asks for a new password that passes validation, then asks again to confirm
    private static String readNewPassword(String prompt) {

        while (true) {

            String password = readPassword(prompt);

            if (!User.isValidPassword(password)) {

                System.out.println(
                        "Error: Password must contain at least 6 characters."
                );
                continue;
            }

            String confirmation = readPassword("Confirm Password: ");

            if (password.equals(confirmation)) {
                return password;
            }

            System.out.println("Error: Passwords do not match.");
        }
    }

    private static String readPaymentMethod() {

        while (true) {

            String method = readRequired(
                    "Payment Method (Cash/Bank Transfer/GCash/Advance Payment): "
            );

            if (Payment.isValidMethod(method)) {
                return method;
            }

            System.out.println(
                    "Invalid method. Choose Cash, Bank Transfer, GCash, "
                            + "or Advance Payment."
            );
        }
    }

    // ENTER = current month
    private static YearMonth readMonthOrDefault(
            String prompt,
            YearMonth defaultMonth
    ) {

        while (true) {

            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                return defaultMonth;
            }

            try {

                return YearMonth.parse(input);

            } catch (DateTimeParseException e) {

                System.out.println("Invalid month! Please use YYYY-MM format.");
            }
        }
    }

    private static LocalDate parseDateOrNull(String value) {

        try {

            return value == null ? null : LocalDate.parse(value);

        } catch (DateTimeParseException e) {

            return null;
        }
    }

    // ==========================================
    // SEARCH METHODS
    // ==========================================

    // Finds ANY tenant, including moved-out ones
    private static Tenant findTenantById(String id) {

        for (Tenant tenant : tenants) {

            if (tenant.getTenantID() != null
                    && tenant.getTenantID().equalsIgnoreCase(id)) {

                return tenant;
            }
        }

        return null;
    }

    private static Tenant findActiveTenantById(String id) {

        Tenant tenant = findTenantById(id);

        return (tenant != null && tenant.isActive()) ? tenant : null;
    }

    private static Apartment findApartmentByRoom(String roomNumber) {

        for (Apartment apartment : apartments) {

            if (apartment.getRoomNumber() != null
                    && apartment.getRoomNumber()
                    .equalsIgnoreCase(roomNumber)) {

                return apartment;
            }
        }

        return null;
    }

    // Newest record first (old files may contain duplicates)
    private static Deposit findDepositByTenantId(String tenantId) {

        for (int i = deposits.size() - 1; i >= 0; i--) {

            Deposit deposit = deposits.get(i);

            if (deposit.getTenantID() != null
                    && deposit.getTenantID().equalsIgnoreCase(tenantId)) {

                return deposit;
            }
        }

        return null;
    }

    private static Rental findRentalByTenant(String tenantId) {

        for (Rental rental : rentals) {

            if (rental.getTenantID() != null
                    && rental.getTenantID().equalsIgnoreCase(tenantId)) {

                return rental;
            }
        }

        return null;
    }

    private static boolean isMovedOut(String tenantId) {

        Tenant tenant = findTenantById(tenantId);

        return tenant == null || !tenant.isActive();
    }

    // ==========================================
    // DUPLICATE CHECKING
    // ==========================================

    // An ID is never reused: tenants stay on file after move-out,
    // and old payments/deposits also block the ID.
    private static boolean isTenantIdTaken(String id) {

        if (findTenantById(id) != null
                || findDepositByTenantId(id) != null) {
            return true;
        }

        for (Payment payment : payments) {

            if (payment.getTenantID() != null
                    && payment.getTenantID().equalsIgnoreCase(id)) {

                return true;
            }
        }

        return false;
    }

    private static boolean isUsernameTaken(String username) {

        for (Tenant tenant : tenants) {

            if (tenant.getUsername() != null
                    && tenant.getUsername().equalsIgnoreCase(username)) {

                return true;
            }
        }

        for (Admin admin : admins) {

            if (admin.getUsername() != null
                    && admin.getUsername().equalsIgnoreCase(username)) {

                return true;
            }
        }

        return false;
    }

    private static boolean isPaymentIdTaken(String id) {

        for (Payment payment : payments) {

            if (payment.getPaymentID() != null
                    && payment.getPaymentID().equalsIgnoreCase(id)) {

                return true;
            }
        }

        return false;
    }

    private static String readUniqueTenantId() {

        while (true) {

            String id = readRequired("Enter Tenant ID: ");

            if (!isTenantIdTaken(id)) {
                return id;
            }

            System.out.println(
                    "Error: Tenant ID already exists (IDs of moved-out "
                            + "tenants cannot be reused)."
            );
        }
    }

    private static String readUniqueUsername() {

        while (true) {

            String username = readRequired(
                    "Create Tenant Portal Username: "
            );

            if (!isUsernameTaken(username)) {
                return username;
            }

            System.out.println("Error: Username is already being used.");
        }
    }

    private static String readUniquePaymentId() {

        while (true) {

            String id = readRequired("Enter Payment ID: ");

            if (!isPaymentIdTaken(id)) {
                return id;
            }

            System.out.println("Error: Payment ID already exists.");
        }
    }

    // ==========================================
    // LOGIN (shared by both portals)
    // ==========================================

    private static long lockSecondsRemaining(String key) {

        Long until = lockUntil.get(key);

        if (until == null) {
            return 0;
        }

        long remainingMillis = until - System.currentTimeMillis();

        if (remainingMillis <= 0) {
            lockUntil.remove(key);
            return 0;
        }

        return (remainingMillis + 999) / 1000;
    }

    // Works with any list of Users: polymorphic authenticate() does the checking.
    private static User login(
            List<? extends User> users,
            String title,
            String portalKey
    ) {

        System.out.println("\n--- " + title + " ---");

        while (true) {

            String username = readRequired("Enter Username: ");
            String key = portalKey + ":" + username.toLowerCase();

            long locked = lockSecondsRemaining(key);

            if (locked > 0) {

                System.out.println(
                        "Too many failed attempts. Try again in "
                                + locked + " seconds."
                );

                return null;
            }

            String password = readPassword("Enter Password: ");

            for (User user : users) {

                if (user.authenticate(username, password)) {

                    loginFailures.remove(key);
                    lockUntil.remove(key);

                    System.out.println(
                            "\nLogin Successful! Welcome, "
                                    + user.getFullName() + "."
                    );

                    return user;
                }
            }

            int failures = loginFailures.merge(key, 1, Integer::sum);

            if (failures >= MAX_LOGIN_ATTEMPTS) {

                lockUntil.put(
                        key,
                        System.currentTimeMillis() + LOCKOUT_MILLIS
                );

                loginFailures.remove(key);

                System.out.println(
                        "Too many failed attempts. This account is locked for "
                                + (LOCKOUT_MILLIS / 1000) + " seconds."
                );

                return null;
            }

            System.out.println(
                    "Invalid credentials! Attempts remaining: "
                            + (MAX_LOGIN_ATTEMPTS - failures)
            );

            if (!confirm("Try again? (Y/N): ")) {
                return null;
            }
        }
    }

    private static boolean changeOwnPassword(User user) {

        System.out.println("\n--- CHANGE PASSWORD ---");

        String current = readPassword("Enter Current Password: ");

        if (!user.verifyPassword(current)) {

            System.out.println("Error: Current password is incorrect.");
            return false;
        }

        user.setPassword(readNewPassword("Enter New Password (min 6 characters): "));

        saveAllData();

        System.out.println("Password changed successfully!");

        return true;
    }

    // ==========================================
    // ADMIN PORTAL
    // ==========================================

    private static void runAdminPortal() {

        User user = login(admins, "ADMINISTRATOR LOGIN", "admin");

        if (!(user instanceof Admin)) {

            System.out.println("Returning to main menu.");
            return;
        }

        // Polymorphism: getRoleDetails() is called on a User reference
        System.out.println(user.getRoleDetails());

        showAlerts();

        int choice = 0;

        do {

            System.out.println("\n" + LINE);
            System.out.println("          ADMINISTRATOR MENU");
            System.out.println(LINE);
            System.out.println("1. Register New Tenant");
            System.out.println("2. View All Tenants");
            System.out.println("3. Update Tenant Information");
            System.out.println("4. Move Out Tenant");
            System.out.println("5. Manage Apartments");
            System.out.println("6. Record Tenant Payment");
            System.out.println("7. Manage Deposits");
            System.out.println("8. View Payment History");
            System.out.println("9. Generate Monthly Financial Report");
            System.out.println("10. Alerts (Overdue Rent & Lease Expiry)");
            System.out.println("11. Change My Password");
            System.out.println("12. Logout");

            choice = readInt("Enter your choice: ", 1, 12);

            // One bad input or rule violation must never crash the program.
            try {

                switch (choice) {

                    case 1:
                        addTenant();
                        break;

                    case 2:
                        viewTenants();
                        break;

                    case 3:
                        updateTenant();
                        break;

                    case 4:
                        moveOutTenant();
                        break;

                    case 5:
                        manageApartments();
                        break;

                    case 6:
                        recordPayment();
                        break;

                    case 7:
                        manageDeposits();
                        break;

                    case 8:
                        viewPaymentHistory();
                        break;

                    case 9:
                        generateMonthlyReport();
                        break;

                    case 10:
                        showAlerts();
                        break;

                    case 11:
                        changeOwnPassword(user);
                        break;

                    case 12:
                        saveAllData();
                        System.out.println("Logged out of Admin Portal.");
                        break;
                }

            } catch (NoSuchElementException e) {

                throw e;

            } catch (RuntimeException e) {

                System.out.println("Error: " + e.getMessage());
            }

        } while (choice != 12);
    }

    // ==========================================
    // TENANT PORTAL
    // ==========================================

    private static void runTenantPortal() {

        User user = login(tenants, "TENANT PORTAL LOGIN", "tenant");

        if (!(user instanceof Tenant)) {

            System.out.println("Returning to main menu.");
            return;
        }

        Tenant loggedInTenant = (Tenant) user;

        System.out.println(user.getRoleDetails());

        int choice = 0;

        do {

            System.out.println("\n" + LINE);
            System.out.println("          TENANT PORTAL");
            System.out.println(LINE);
            System.out.println("1. View Personal Profile & Room Details");
            System.out.println("2. View My Payment History & Balances");
            System.out.println("3. Change My Password");
            System.out.println("4. Logout");

            choice = readInt("Enter your choice (1-4): ", 1, 4);

            try {

                switch (choice) {

                    case 1:
                        viewTenantProfile(loggedInTenant);
                        break;

                    case 2:
                        viewTenantPayments(loggedInTenant);
                        break;

                    case 3:
                        changeOwnPassword(loggedInTenant);
                        break;

                    case 4:
                        System.out.println("Logged out of Tenant Portal.");
                        break;
                }

            } catch (NoSuchElementException e) {

                throw e;

            } catch (RuntimeException e) {

                System.out.println("Error: " + e.getMessage());
            }

        } while (choice != 4);
    }

    // ==========================================
    // REGISTER NEW TENANT
    // ==========================================

    private static void addTenant() {

        System.out.println("\n--- REGISTER NEW TENANT ---");

        String id = readUniqueTenantId();
        String name = readRequired("Enter Full Name: ");

        // Validated here with the real rules, so nothing crashes later.
        String contact = readValidContact("Enter Contact Number: ");

        String roomNumber = readRequired("Enter Room Number: ");

        Apartment targetRoom = findApartmentByRoom(roomNumber);

        if (targetRoom == null) {

            System.out.println("Error: Apartment does not exist.");
            return;
        }

        if (!targetRoom.isVacant()) {

            System.out.println("Error: Apartment is already occupied.");
            return;
        }

        LocalDate moveInDate = readDate("Enter Move-In Date (YYYY-MM-DD): ");

        LocalDate leaseEnd;

        while (true) {

            leaseEnd = readDate("Enter Lease Expiration Date (YYYY-MM-DD): ");

            if (leaseEnd.isAfter(moveInDate)) {
                break;
            }

            System.out.println(
                    "Error: Lease expiration must be after the move-in date."
            );
        }

        int dueDay = readInt(
                "Enter Rent Due Day of the Month (1-31): ",
                1,
                31
        );

        String username = readUniqueUsername();

        String password = readNewPassword(
                "Create Tenant Portal Password (min 6 characters): "
        );

        BigDecimal securityDeposit = readAmount(
                "Enter Security Deposit Amount: PHP",
                true
        );

        BigDecimal advancePayment = readAmount(
                "Enter Advance Payment Amount: PHP",
                true
        );

        // Build everything first; only then change any data.
        Tenant newTenant = new Tenant(
                id,
                name,
                contact,
                targetRoom.getRoomNumber(),
                moveInDate.toString(),
                username,
                password
        );

        Deposit newDeposit = new Deposit(id, securityDeposit, advancePayment);

        // The rent is copied into the lease, so later price changes do not affect this tenant.
        Rental newRental = new Rental(
                id,
                targetRoom.getRoomNumber(),
                targetRoom.getMonthlyRentalPrice(),
                dueDay,
                moveInDate.toString(),
                leaseEnd.toString()
        );

        tenants.add(newTenant);
        deposits.add(newDeposit);
        rentals.add(newRental);

        targetRoom.markOccupied();

        saveData();

        System.out.println("\nTenant successfully registered!");
        System.out.println("Tenant ID: " + id);
        System.out.println("Tenant Name: " + newTenant.getFullName());
        System.out.println("Assigned Room: " + targetRoom.getRoomNumber());
        System.out.println("Room Status: Occupied");
        System.out.println("\n--- LEASE ---");
        System.out.println(newRental.getRentalSummary());
    }

    // ==========================================
    // VIEW ALL TENANTS
    // ==========================================

    private static void viewTenants() {

        System.out.println("\n" + LINE);
        System.out.println("                  ALL TENANT RECORDS");
        System.out.println(LINE);

        if (tenants.isEmpty()) {

            System.out.println("No tenants registered.");
            return;
        }

        int active = 0;

        System.out.println("--- ACTIVE TENANTS ---");

        for (Tenant tenant : tenants) {

            if (tenant.isActive()) {

                System.out.println(tenant.getTenantInfo());
                System.out.println(THIN_LINE);
                System.out.println();
                active++;
            }
        }

        if (active == 0) {
            System.out.println("None.\n");
        }

        int movedOut = tenants.size() - active;

        if (movedOut > 0) {

            System.out.println("--- MOVED-OUT TENANTS (history) ---");

            for (Tenant tenant : tenants) {

                if (!tenant.isActive()) {

                    System.out.println(tenant.getTenantInfo());
                    System.out.println(THIN_LINE);
                    System.out.println();
                }
            }
        }

        System.out.println("Total Active Tenants: " + active);
        System.out.println("Total Moved-Out Tenants: " + movedOut);
    }

    // ==========================================
    // UPDATE TENANT INFORMATION
    // ==========================================

    private static void updateTenant() {

        System.out.println("\n--- UPDATE TENANT INFORMATION ---");

        String id = readRequired("Enter Tenant ID to update: ");

        Tenant tenant = findActiveTenantById(id);

        if (tenant == null) {

            System.out.println("Error: Active tenant with that ID not found.");
            return;
        }

        System.out.println("\nTenant Found!");
        System.out.println("Name: " + tenant.getFullName());
        System.out.println("Current Contact: " + tenant.getContactNumber());

        while (true) {

            System.out.print(
                    "Enter New Contact Number (press ENTER to keep current): "
            );

            String newContact = scanner.nextLine().trim();

            if (newContact.isEmpty()) {

                System.out.println("No changes made.");
                return;
            }

            if (User.isValidContactNumber(newContact)) {

                tenant.updateContact(newContact);
                saveData();

                System.out.println("Tenant contact updated successfully!");
                return;
            }

            System.out.println(
                    "Error: Invalid contact number. Use 7-20 digits, "
                            + "spaces, +, ( ), or -."
            );
        }
    }

    // ==========================================
    // MOVE OUT TENANT
    // ==========================================

    private static void moveOutTenant() {

        System.out.println("\n--- MOVE OUT TENANT ---");

        String id = readRequired("Enter Tenant ID: ");

        Tenant tenant = findTenantById(id);

        if (tenant == null) {

            System.out.println("Error: Tenant ID not found.");
            return;
        }

        if (!tenant.isActive()) {

            System.out.println("Error: This tenant has already moved out.");
            return;
        }

        System.out.println("\nTenant Information");
        System.out.println("Tenant Name: " + tenant.getFullName());
        System.out.println("Room Number: " + tenant.getRoomNumber());

        LocalDate moveOutDate;
        LocalDate moveInDate = LocalDate.parse(tenant.getMoveInDate());

        while (true) {

            moveOutDate = readDate("Enter Move-Out Date (YYYY-MM-DD): ");

            if (!moveOutDate.isBefore(moveInDate)) {
                break;
            }

            System.out.println(
                    "Error: Move-out date cannot be before the move-in date ("
                            + moveInDate + ")."
            );
        }

        if (!confirm("\nConfirm move-out? (Y/N): ")) {

            System.out.println("Move-out cancelled.");
            return;
        }

        String roomNumber = tenant.getRoomNumber();
        Apartment room = findApartmentByRoom(roomNumber);

        // The tenant stays on file as Inactive, so names and history are kept.
        tenant.moveOut(moveOutDate.toString());

        if (room != null && room.isOccupied()) {
            room.markVacant();
        }

        saveData();

        System.out.println("\nTenant successfully moved out.");
        System.out.println("Room " + roomNumber + " is now Vacant.");
        System.out.println(
                "The tenant record, payment history and deposit were preserved."
        );

        Deposit deposit = findDepositByTenantId(tenant.getTenantID());

        if (deposit != null
                && Deposit.STATUS_HELD.equalsIgnoreCase(
                        deposit.getDepositStatus())) {

            System.out.println(
                    "Reminder: the deposit is still Held ("
                            + peso(deposit.getRefundableAmount())
                            + "). Use Manage Deposits to apply or refund it."
            );
        }
    }

    // ==========================================
    // MANAGE APARTMENTS
    // ==========================================

    private static void manageApartments() {

        int choice;

        do {

            System.out.println("\n" + LINE);
            System.out.println("             MANAGE APARTMENTS");
            System.out.println(LINE);
            System.out.println("1. View Apartments");
            System.out.println("2. Add Apartment");
            System.out.println("3. Change Apartment Rent");
            System.out.println("4. Remove Apartment");
            System.out.println("5. Back to Administrator Menu");

            choice = readInt("Enter your choice (1-5): ", 1, 5);

            switch (choice) {

                case 1:
                    viewApartments();
                    break;

                case 2:
                    addApartment();
                    break;

                case 3:
                    changeApartmentRent();
                    break;

                case 4:
                    removeApartment();
                    break;

                case 5:
                    System.out.println("Returning to Administrator Menu.");
                    break;
            }

        } while (choice != 5);
    }

    private static void viewApartments() {

        System.out.println("\n" + LINE);
        System.out.println("                 APARTMENT RECORDS");
        System.out.println(LINE);

        if (apartments.isEmpty()) {

            System.out.println("No apartment records found.");
            return;
        }

        int occupied = 0;
        int vacant = 0;

        for (Apartment apartment : apartments) {

            System.out.println(apartment.getRoomDetails());
            System.out.println(THIN_LINE);
            System.out.println();

            if (apartment.isOccupied()) {
                occupied++;
            } else {
                vacant++;
            }
        }

        System.out.println("\n--- APARTMENT SUMMARY ---");
        System.out.println("Total Apartments: " + apartments.size());
        System.out.println("Occupied Apartments: " + occupied);
        System.out.println("Vacant Apartments: " + vacant);
    }

    private static void addApartment() {

        System.out.println("\n--- ADD APARTMENT ---");

        String roomNumber;

        while (true) {

            roomNumber = readRequired("Enter Room Number: ");

            if (findApartmentByRoom(roomNumber) == null) {
                break;
            }

            System.out.println("Error: Room number already exists.");
        }

        BigDecimal rent = readAmount("Enter Monthly Rent: PHP", false);

        apartments.add(new Apartment(roomNumber, rent));
        saveData();

        System.out.println("Apartment " + roomNumber + " added (Vacant).");
    }

    private static void changeApartmentRent() {

        System.out.println("\n--- CHANGE APARTMENT RENT ---");

        Apartment apartment = findApartmentByRoom(
                readRequired("Enter Room Number: ")
        );

        if (apartment == null) {

            System.out.println("Error: Apartment does not exist.");
            return;
        }

        System.out.println(
                "Current rent: " + peso(apartment.getMonthlyRentalPrice())
        );

        BigDecimal newRent = readAmount("Enter New Monthly Rent: PHP", false);

        apartment.setMonthlyRentalPrice(newRent);
        saveData();

        System.out.println("Rent updated to " + peso(newRent) + ".");
        System.out.println(
                "Note: this applies to NEW leases only. Current tenants keep "
                        + "the rent in their lease, and past payments are unchanged."
        );
    }

    private static void removeApartment() {

        System.out.println("\n--- REMOVE APARTMENT ---");

        String roomNumber = readRequired("Enter Room Number: ");
        Apartment apartment = findApartmentByRoom(roomNumber);

        if (apartment == null) {

            System.out.println("Error: Apartment does not exist.");
            return;
        }

        if (!apartment.isVacant()) {

            System.out.println("Error: Occupied apartments cannot be removed.");
            return;
        }

        // Rooms that ever had a tenant stay on file so history keeps making sense.
        for (Tenant tenant : tenants) {

            if (tenant.getRoomNumber().equalsIgnoreCase(roomNumber)) {

                System.out.println(
                        "Error: This room has tenant history and cannot be removed."
                );
                return;
            }
        }

        if (!confirm("Remove room " + roomNumber + "? (Y/N): ")) {

            System.out.println("Cancelled.");
            return;
        }

        apartments.remove(apartment);
        saveData();

        System.out.println("Apartment removed.");
    }

    // ==========================================
    // RECORD TENANT PAYMENT
    // ==========================================

    private static void recordPayment() {

        System.out.println("\n--- RECORD TENANT PAYMENT ---");

        String tenantId = readRequired("Enter Tenant ID: ");

        Tenant tenant = findTenantById(tenantId);

        if (tenant == null) {

            System.out.println("Error: Tenant not found.");
            return;
        }

        if (!tenant.isActive()) {

            System.out.println(
                    "Error: This tenant has moved out. Payments can no longer be recorded."
            );
            return;
        }

        Rental rental = findRentalByTenant(tenant.getTenantID());

        if (rental == null) {

            System.out.println("Error: No lease record found for this tenant.");
            return;
        }

        LocalDate paymentDate = readDate("Enter Payment Date (YYYY-MM-DD): ");

        // The month the rent is FOR can differ from the date it was paid.
        YearMonth billingMonth = readMonthOrDefault(
                "Billing Month this payment is for (YYYY-MM) "
                        + "[ENTER = " + YearMonth.from(paymentDate) + "]: ",
                YearMonth.from(paymentDate)
        );

        YearMonth firstMonth = YearMonth.from(
                LocalDate.parse(tenant.getMoveInDate())
        );

        if (billingMonth.isBefore(firstMonth)) {

            System.out.println(
                    "Error: Billing month is before the tenant moved in ("
                            + firstMonth + ")."
            );
            return;
        }

        BigDecimal monthlyRent = rental.getMonthlyRent();
        BigDecimal previouslyPaid = getBilledPayments(tenantId, billingMonth);
        BigDecimal remainingBefore = monthlyRent.subtract(previouslyPaid);

        if (remainingBefore.signum() <= 0) {

            System.out.println(
                    "Rent for " + billingMonth + " is already fully paid."
            );
            return;
        }

        System.out.println("\n--- RENT DETAILS (" + billingMonth + ") ---");
        System.out.println("Monthly Rent: " + peso(monthlyRent));
        System.out.println("Previously Paid: " + peso(previouslyPaid));
        System.out.println("Remaining Balance: " + peso(remainingBefore));

        BigDecimal amount = readAmount("Enter Amount Paid: PHP", false);

        if (amount.compareTo(remainingBefore) > 0) {

            System.out.println(
                    "Error: Payment exceeds remaining balance of "
                            + peso(remainingBefore)
            );
            return;
        }

        String method = readPaymentMethod();
        Deposit deposit = null;

        if (Payment.METHOD_ADVANCE.equalsIgnoreCase(method)
                || "Advance".equalsIgnoreCase(method)) {

            deposit = findDepositByTenantId(tenantId);

            if (deposit == null
                    || !Deposit.STATUS_HELD.equalsIgnoreCase(
                            deposit.getDepositStatus())
                    || deposit.getAdvanceRemaining().compareTo(amount) < 0) {

                System.out.println(
                        "Error: Not enough advance payment available "
                                + (deposit == null
                                    ? "(no deposit record)."
                                    : "(" + peso(deposit.getAdvanceRemaining())
                                        + " left).")
                );
                return;
            }
        }

        String reference = "";

        if (Payment.requiresReference(method)) {
            reference = readRequired("Enter Reference Number: ");
        }

        String paymentId = readUniquePaymentId();

        // Payment does ALL the billing math (balance + status).
        Payment payment = new Payment(
                paymentId,
                tenantId,
                billingMonth.toString(),
                monthlyRent,
                previouslyPaid,
                amount,
                method,
                reference,
                paymentDate.toString()
        );

        if (deposit != null) {
            deposit.useAdvance(amount);
        }

        payments.add(payment);

        saveData();

        System.out.println("\nPayment successfully recorded!");
        System.out.println(payment.getPaymentReceipt());
    }

    // ==========================================
    // CALCULATE PAYMENTS FOR A BILLING MONTH
    // ==========================================

    private static BigDecimal getBilledPayments(
            String tenantId,
            YearMonth month
    ) {

        BigDecimal total = BigDecimal.ZERO;

        for (Payment payment : payments) {

            if (payment.getTenantID() == null
                    || !payment.getTenantID().equalsIgnoreCase(tenantId)) {

                continue;
            }

            YearMonth billed = payment.getBillingYearMonth();

            if (billed == null) {

                System.out.println(
                        "Warning: Invalid date in payment "
                                + payment.getPaymentID()
                );
                continue;
            }

            if (billed.equals(month)) {
                total = total.add(payment.getAmountPaid());
            }
        }

        return total;
    }

    // ==========================================
    // MANAGE DEPOSITS
    // ==========================================

    private static void manageDeposits() {

        int choice;

        do {

            System.out.println("\n" + LINE);
            System.out.println("             MANAGE DEPOSITS");
            System.out.println(LINE);
            System.out.println("1. View All Deposit Records");
            System.out.println("2. Search Deposit by Tenant ID");
            System.out.println("3. Apply Deposit (to unpaid balance / damages)");
            System.out.println("4. Refund Deposit");
            System.out.println("5. Back to Administrator Menu");

            choice = readInt("Enter your choice (1-5): ", 1, 5);

            try {

                switch (choice) {

                    case 1:
                        viewAllDeposits();
                        break;

                    case 2:
                        searchDeposit();
                        break;

                    case 3:
                        applyDeposit();
                        break;

                    case 4:
                        refundDeposit();
                        break;

                    case 5:
                        System.out.println("Returning to Administrator Menu.");
                        break;
                }

            } catch (NoSuchElementException e) {

                throw e;

            } catch (RuntimeException e) {

                System.out.println("Error: " + e.getMessage());
            }

        } while (choice != 5);
    }

    private static void printTenantStatusLine(String tenantId) {

        Tenant tenant = findTenantById(tenantId);

        if (tenant == null) {

            System.out.println("Tenant Status: Unknown (no tenant record)");
            return;
        }

        System.out.println("Tenant Name: " + tenant.getFullName());
        System.out.println(
                "Tenant Status: "
                        + (tenant.isActive()
                            ? "Active"
                            : "Moved Out (" + tenant.getMoveOutDate() + ")")
        );
    }

    private static void viewAllDeposits() {

        System.out.println("\n" + LINE);
        System.out.println("                 ALL DEPOSIT RECORDS");
        System.out.println(LINE);

        if (deposits.isEmpty()) {

            System.out.println("No deposit records found.");
            return;
        }

        // Deposit implements Reportable, so it is printed through that interface
        for (Deposit deposit : deposits) {

            Reportable item = deposit;

            System.out.println(item.getReportSummary());
            printTenantStatusLine(deposit.getTenantID());
            System.out.println(THIN_LINE);
            System.out.println();
        }

        System.out.println("Total Deposit Records: " + deposits.size());
    }

    private static void searchDeposit() {

        System.out.println("\n" + LINE);
        System.out.println("                SEARCH DEPOSIT RECORD");
        System.out.println(LINE);

        String tenantId = readRequired("Enter Tenant ID: ");

        Deposit deposit = findDepositByTenantId(tenantId);

        if (deposit == null) {

            System.out.println("No deposit record found for this Tenant ID.");
            return;
        }

        System.out.println("\nDeposit Record Found!");
        System.out.println(deposit.getDepositSummary());
        printTenantStatusLine(tenantId);
    }

    private static void applyDeposit() {

        System.out.println("\n--- APPLY DEPOSIT ---");

        String tenantId = readRequired("Enter Tenant ID: ");
        Deposit deposit = findDepositByTenantId(tenantId);

        if (deposit == null) {

            System.out.println("No deposit record found for this Tenant ID.");
            return;
        }

        if (!isMovedOut(tenantId)) {

            System.out.println(
                    "Error: A deposit can only be applied after the tenant has moved out."
            );
            return;
        }

        System.out.println(deposit.getDepositSummary());

        if (confirm("\nMark this deposit as Applied? (Y/N): ")) {

            deposit.applyDeposit();
            saveData();

            System.out.println("Deposit marked as Applied.");

        } else {

            System.out.println("Cancelled.");
        }
    }

    private static void refundDeposit() {

        System.out.println("\n--- REFUND DEPOSIT ---");

        String tenantId = readRequired("Enter Tenant ID: ");
        Deposit deposit = findDepositByTenantId(tenantId);

        if (deposit == null) {

            System.out.println("No deposit record found for this Tenant ID.");
            return;
        }

        if (!isMovedOut(tenantId)) {

            System.out.println(
                    "Error: A deposit can only be refunded after the tenant has moved out."
            );
            return;
        }

        System.out.println(deposit.getDepositSummary());

        if (confirm(
                "\nRefund " + peso(deposit.getRefundableAmount())
                        + " to the tenant? (Y/N): ")) {

            deposit.refundDeposit();
            saveData();

            System.out.println("Deposit marked as Refunded.");

        } else {

            System.out.println("Cancelled.");
        }
    }

    // ==========================================
    // VIEW PAYMENT HISTORY
    // ==========================================

    private static void viewPaymentHistory() {

        System.out.println("\n" + LINE);
        System.out.println("                   PAYMENT HISTORY");
        System.out.println(LINE);

        if (payments.isEmpty()) {

            System.out.println("No payment transactions recorded.");
            return;
        }

        for (Payment payment : payments) {

            System.out.println(payment.getPaymentReceipt());
            System.out.println(THIN_LINE);
            System.out.println();
        }

        System.out.println("Total Payment Transactions: " + payments.size());
    }

    // ==========================================
    // ALERTS (uses the Rental lease and due-date methods)
    // ==========================================

    private static void showAlerts() {

        System.out.println("\n" + LINE);
        System.out.println("                      ALERTS");
        System.out.println(LINE);

        LocalDate today = LocalDate.now();
        YearMonth thisMonth = YearMonth.from(today);
        int alerts = 0;

        for (Tenant tenant : tenants) {

            if (!tenant.isActive()) {
                continue;
            }

            Rental rental = findRentalByTenant(tenant.getTenantID());

            if (rental == null) {
                continue;
            }

            String label = tenant.getFullName()
                    + " (" + tenant.getTenantID()
                    + ", Room " + tenant.getRoomNumber() + ")";

            // Lease status
            if ("Expired".equals(rental.checkLeaseStatus())) {

                System.out.println(
                        "[LEASE EXPIRED] " + label + " - ended "
                                + rental.getLeaseExpirationDate()
                );
                alerts++;

            } else if (rental.isExpiringWithin(LEASE_WARNING_DAYS)) {

                System.out.println(
                        "[LEASE ENDING SOON] " + label + " - ends "
                                + rental.getLeaseExpirationDate()
                );
                alerts++;
            }

            // Overdue rent for the current month
            LocalDate moveIn = LocalDate.parse(tenant.getMoveInDate());
            LocalDate dueDate = rental.dueDateFor(thisMonth);

            if (YearMonth.from(moveIn).isAfter(thisMonth)
                    || dueDate.isBefore(moveIn)) {
                continue;
            }

            BigDecimal balance = rental.getMonthlyRent()
                    .subtract(getBilledPayments(tenant.getTenantID(), thisMonth));

            if (balance.signum() > 0 && today.isAfter(dueDate)) {

                long daysLate = java.time.temporal.ChronoUnit.DAYS
                        .between(dueDate, today);

                System.out.println(
                        "[OVERDUE RENT] " + label + " - " + peso(balance)
                                + " unpaid for " + thisMonth + " (due "
                                + dueDate + ", " + daysLate + " day(s) late)"
                );
                alerts++;
            }
        }

        if (alerts == 0) {
            System.out.println("No alerts. Everything looks good!");
        }
    }

    // ==========================================
    // MONTHLY FINANCIAL REPORT
    // ==========================================

    // Was this tenant living in the building at any time during the month?
    private static boolean isTenantActiveDuring(Tenant tenant, YearMonth month) {

        LocalDate moveIn = parseDateOrNull(tenant.getMoveInDate());

        if (moveIn == null || YearMonth.from(moveIn).isAfter(month)) {
            return false;
        }

        LocalDate moveOut = parseDateOrNull(tenant.getMoveOutDate());

        return moveOut == null || !YearMonth.from(moveOut).isBefore(month);
    }

    private static boolean isRoomOccupiedDuring(
            Apartment apartment,
            YearMonth month
    ) {

        for (Tenant tenant : tenants) {

            if (tenant.getRoomNumber().equalsIgnoreCase(apartment.getRoomNumber())
                    && isTenantActiveDuring(tenant, month)) {

                return true;
            }
        }

        return false;
    }

    private static void generateMonthlyReport() {

        System.out.println("\n" + LINE);
        System.out.println("       RENTLOG MONTHLY FINANCIAL REPORT");
        System.out.println(LINE);

        YearMonth selectedMonth = readMonthOrDefault(
                "Enter Report Month (YYYY-MM) "
                        + "or press ENTER for current month: ",
                YearMonth.now()
        );

        // Cash received during the month (by payment date), split by method
        BigDecimal totalReceived = BigDecimal.ZERO;
        int transactionCount = 0;
        Map<String, BigDecimal> byMethod = new TreeMap<>();

        for (Payment payment : payments) {

            LocalDate paymentDate = parseDateOrNull(payment.getPaymentDate());

            if (paymentDate == null) {

                System.out.println(
                        "Warning: Invalid payment date for "
                                + payment.getPaymentID()
                );
                continue;
            }

            if (YearMonth.from(paymentDate).equals(selectedMonth)) {

                totalReceived = totalReceived.add(payment.getAmountPaid());
                transactionCount++;

                byMethod.merge(
                        payment.getPaymentMethod(),
                        payment.getAmountPaid(),
                        BigDecimal::add
                );
            }
        }

        // Rent collected FOR this month (by billing month) vs. rent expected
        BigDecimal collectedForMonth = BigDecimal.ZERO;

        for (Payment payment : payments) {

            if (selectedMonth.equals(payment.getBillingYearMonth())) {
                collectedForMonth = collectedForMonth.add(payment.getAmountPaid());
            }
        }

        BigDecimal expectedRent = BigDecimal.ZERO;
        List<Reportable> tenantsInMonth = new ArrayList<>();

        for (Tenant tenant : tenants) {

            if (isTenantActiveDuring(tenant, selectedMonth)) {

                tenantsInMonth.add(tenant);

                Rental rental = findRentalByTenant(tenant.getTenantID());

                if (rental != null) {
                    expectedRent = expectedRent.add(rental.getMonthlyRent());
                }
            }
        }

        // Occupancy for the SELECTED month (not just today's status)
        int occupiedRooms = 0;

        System.out.println("\n--- APARTMENT OCCUPANCY (" + selectedMonth + ") ---");

        for (Apartment apartment : apartments) {

            boolean occupied = isRoomOccupiedDuring(apartment, selectedMonth);

            System.out.printf(
                    "Room %-6s | %-14s | %s%n",
                    apartment.getRoomNumber(),
                    peso(apartment.getMonthlyRentalPrice()),
                    occupied ? "Occupied" : "Vacant"
            );

            if (occupied) {
                occupiedRooms++;
            }
        }

        // Reportable is used here: tenants are printed through the interface
        System.out.println("\n--- TENANTS IN " + selectedMonth + " ---");

        if (tenantsInMonth.isEmpty()) {

            System.out.println("No tenants for this month.");

        } else {

            for (Reportable item : tenantsInMonth) {

                System.out.println(item.getReportSummary());
                System.out.println(THIN_LINE);
            }
        }

        System.out.println("\n--- FINANCIAL SUMMARY ---");

        System.out.println("Report Month: " + selectedMonth);
        System.out.println("Tenants in this month: " + tenantsInMonth.size());

        System.out.println(
                "Occupied Apartments: "
                        + occupiedRooms + " / " + apartments.size()
        );

        System.out.println(
                "Vacant Apartments: " + (apartments.size() - occupiedRooms)
        );

        System.out.println("Payment Transactions: " + transactionCount);
        System.out.println("Total Cash Received: " + peso(totalReceived));

        for (Map.Entry<String, BigDecimal> entry : byMethod.entrySet()) {

            System.out.println(
                    "   - " + entry.getKey() + ": " + peso(entry.getValue())
            );
        }

        System.out.println("Rent Expected for " + selectedMonth + ": "
                + peso(expectedRent));
        System.out.println("Rent Collected for " + selectedMonth + ": "
                + peso(collectedForMonth));

        if (expectedRent.signum() > 0) {

            BigDecimal efficiency = collectedForMonth
                    .multiply(BigDecimal.valueOf(100))
                    .divide(expectedRent, 1, RoundingMode.HALF_UP);

            System.out.println("Collection Efficiency: " + efficiency + "%");
        }

        System.out.println(LINE);
    }

    // ==========================================
    // TENANT PROFILE
    // ==========================================

    private static void viewTenantProfile(Tenant tenant) {

        System.out.println("\n" + LINE);
        System.out.println("                 MY RENTAL INFORMATION");
        System.out.println(LINE);

        System.out.println("Tenant ID: " + tenant.getTenantID());
        System.out.println("Full Name: " + tenant.getFullName());
        System.out.println("Contact Number: " + tenant.getContactNumber());
        System.out.println("Room Number: " + tenant.getRoomNumber());
        System.out.println("Move-In Date: " + tenant.getMoveInDate());

        Rental rental = findRentalByTenant(tenant.getTenantID());

        if (rental != null) {

            System.out.println("\n--- MY LEASE ---");
            System.out.println(rental.getRentalSummary());

        } else {

            System.out.println("Lease information is unavailable.");
        }
    }

    // ==========================================
    // TENANT PAYMENT HISTORY
    // ==========================================

    private static void viewTenantPayments(Tenant tenant) {

        System.out.println("\n" + LINE);
        System.out.println("             MY PAYMENT HISTORY & BALANCES");
        System.out.println(LINE);

        boolean found = false;

        for (Payment payment : payments) {

            if (payment.getTenantID() != null
                    && payment.getTenantID()
                    .equalsIgnoreCase(tenant.getTenantID())) {

                System.out.println(payment.getPaymentReceipt());
                System.out.println(THIN_LINE);
                System.out.println();

                found = true;
            }
        }

        if (!found) {
            System.out.println("No payment transactions recorded.");
        }

        Rental rental = findRentalByTenant(tenant.getTenantID());

        if (rental != null) {

            YearMonth thisMonth = YearMonth.now();

            BigDecimal paid = getBilledPayments(tenant.getTenantID(), thisMonth);
            BigDecimal balance = rental.getMonthlyRent().subtract(paid).max(BigDecimal.ZERO);

            System.out.println("\n--- CURRENT MONTH (" + thisMonth + ") ---");
            System.out.println("Monthly Rent: " + peso(rental.getMonthlyRent()));
            System.out.println("Paid So Far: " + peso(paid));
            System.out.println("Balance: " + peso(balance));
            System.out.println("Next Due Date: " + rental.calculateNextDueDate());
        }
    }

    // ==========================================
    // SAVE JSON DATA
    // ==========================================

    private static void saveAllData() {
        saveData();
        saveAdminData();
    }

    private static void saveData() {

        saveJsonFile("tenants.json", tenants);
        saveJsonFile("apartments.json", apartments);
        saveJsonFile("payments.json", payments);
        saveJsonFile("deposits.json", deposits);
        saveJsonFile("rentals.json", rentals);
    }

    private static void saveAdminData() {
        saveJsonFile("admin.json", admins);
    }

    private static void saveJsonFile(String fileName, Object data) {

        Path target = Paths.get(fileName);

        try (Writer writer = Files.newBufferedWriter(
                target, StandardCharsets.UTF_8)) {

            gson.toJson(data, writer);

        } catch (IOException e) {

            System.out.println(
                    "Error saving " + fileName + ": " + e.getMessage()
            );
        }
    }

    // ==========================================
    // LOAD JSON DATA
    // ==========================================

    private static void loadData() {

        admins = loadJsonFile(
                "admin.json",
                new TypeToken<ArrayList<Admin>>() {}.getType()
        );

        tenants = loadJsonFile(
                "tenants.json",
                new TypeToken<ArrayList<Tenant>>() {}.getType()
        );

        apartments = loadJsonFile(
                "apartments.json",
                new TypeToken<ArrayList<Apartment>>() {}.getType()
        );

        payments = loadJsonFile(
                "payments.json",
                new TypeToken<ArrayList<Payment>>() {}.getType()
        );

        deposits = loadJsonFile(
                "deposits.json",
                new TypeToken<ArrayList<Deposit>>() {}.getType()
        );

        rentals = loadJsonFile(
                "rentals.json",
                new TypeToken<ArrayList<Rental>>() {}.getType()
        );
    }

    private static <T> List<T> readJson(Path path, Type type) {

        try (Reader reader = Files.newBufferedReader(
                path, StandardCharsets.UTF_8)) {

            List<T> loaded = gson.fromJson(reader, type);

            return loaded == null ? new ArrayList<T>() : loaded;

        } catch (IOException | JsonParseException e) {

            return null;
        }
    }

    private static <T> List<T> loadJsonFile(String fileName, Type type) {

        Path path = Paths.get(fileName);

        if (!Files.exists(path)) {
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(
                path, StandardCharsets.UTF_8)) {

            List<T> loaded = gson.fromJson(reader, type);

            return loaded == null ? new ArrayList<T>() : loaded;

        } catch (IOException | JsonParseException e) {

            System.out.println(
                    "Warning: Could not load " + fileName + " (" + e.getMessage() + ")."
            );

            return new ArrayList<>();
        }
    }
}