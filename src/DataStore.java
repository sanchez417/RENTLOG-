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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes the same JSON files as Main (admin.json, tenants.json, ...),
 * so the console app and the Swing UI share one set of data.
 *
 * Every rule here is a copy of what Main does (same duplicate checks, same
 * "active during a month" test), so both programs always agree.
 */
public class DataStore {

    // Same default account Main creates on its first run
    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public List<Admin> admins = new ArrayList<>();
    public List<Tenant> tenants = new ArrayList<>();
    public List<Apartment> apartments = new ArrayList<>();
    public List<Payment> payments = new ArrayList<>();
    public List<Deposit> deposits = new ArrayList<>();
    public List<Rental> rentals = new ArrayList<>();

    public DataStore() {
        reload();
    }

    // ==========================================
    // LOAD / SAVE
    // ==========================================

    public void reload() {
        admins = load("admin.json", new TypeToken<ArrayList<Admin>>() {}.getType());
        tenants = load("tenants.json", new TypeToken<ArrayList<Tenant>>() {}.getType());
        apartments = load("apartments.json", new TypeToken<ArrayList<Apartment>>() {}.getType());
        payments = load("payments.json", new TypeToken<ArrayList<Payment>>() {}.getType());
        deposits = load("deposits.json", new TypeToken<ArrayList<Deposit>>() {}.getType());
        rentals = load("rentals.json", new TypeToken<ArrayList<Rental>>() {}.getType());
    }

    private static <T> List<T> load(String file, Type type) {
        Path path = Paths.get(file);

        if (!Files.exists(path)) {
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            List<T> loaded = GSON.fromJson(reader, type);
            return loaded == null ? new ArrayList<T>() : loaded;
        } catch (IOException | JsonParseException e) {
            return new ArrayList<>();
        }
    }

    private void save(String file, Object data) {
        try (Writer writer = Files.newBufferedWriter(Paths.get(file), StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save " + file + ": " + e.getMessage());
        }
    }

    public void saveAdmins() {
        save("admin.json", admins);
    }

    public void saveTenants() {
        save("tenants.json", tenants);
    }

    public void saveApartments() {
        save("apartments.json", apartments);
    }

    public void savePayments() {
        save("payments.json", payments);
    }

    public void saveDeposits() {
        save("deposits.json", deposits);
    }

    public void saveRentals() {
        save("rentals.json", rentals);
    }

    // Same set of files as Main.saveData()
    public void saveAll() {
        saveTenants();
        saveApartments();
        savePayments();
        saveDeposits();
        saveRentals();
    }

    // Main creates this account on its first run. Doing the same here means the
    // UI can be opened first without anyone having to run the console app.
    public void ensureDefaultAdmin() {
        if (!admins.isEmpty()) {
            return;
        }

        admins.add(new Admin(
                "A001",
                "Property Manager",
                "09123456789",
                DEFAULT_ADMIN_USERNAME,
                DEFAULT_ADMIN_PASSWORD
        ));

        saveAdmins();
    }

    // ==========================================
    // LOOKUPS
    // ==========================================

    public Admin admin(String id) {
        for (Admin a : admins) {
            if (a.getUserID().equalsIgnoreCase(id)) {
                return a;
            }
        }
        return null;
    }

    public Tenant tenant(String id) {
        for (Tenant t : tenants) {
            if (t.getTenantID().equalsIgnoreCase(id)) {
                return t;
            }
        }
        return null;
    }

    public Apartment apartment(String roomNumber) {
        for (Apartment a : apartments) {
            if (a.getRoomNumber() != null && a.getRoomNumber().equalsIgnoreCase(roomNumber)) {
                return a;
            }
        }
        return null;
    }

    public Rental rental(String tenantId) {
        for (Rental r : rentals) {
            if (r.getTenantID().equalsIgnoreCase(tenantId)) {
                return r;
            }
        }
        return null;
    }

    // Newest record first (old files may contain duplicates), same as Main
    public Deposit deposit(String tenantId) {
        for (int i = deposits.size() - 1; i >= 0; i--) {
            Deposit d = deposits.get(i);

            if (d.getTenantID() != null && d.getTenantID().equalsIgnoreCase(tenantId)) {
                return d;
            }
        }
        return null;
    }

    public String tenantName(String tenantId) {
        Tenant t = tenant(tenantId);
        return t == null ? tenantId : t.getFullName();
    }

    // The active tenant currently living in a room (null if none)
    public Tenant occupant(String roomNumber) {
        for (Tenant t : tenants) {
            if (t.isActive() && t.getRoomNumber().equalsIgnoreCase(roomNumber)) {
                return t;
            }
        }
        return null;
    }

    // Rooms that ever had a tenant cannot be removed (same rule as Main)
    public boolean roomHasHistory(String roomNumber) {
        for (Tenant t : tenants) {
            if (t.getRoomNumber().equalsIgnoreCase(roomNumber)) {
                return true;
            }
        }
        return false;
    }

    // ==========================================
    // MONEY
    // ==========================================

    // Money paid by one tenant for one billing month
    public BigDecimal paidFor(String tenantId, YearMonth month) {
        BigDecimal sum = BigDecimal.ZERO;

        for (Payment p : payments) {
            if (tenantId.equalsIgnoreCase(p.getTenantID())
                    && month.equals(p.getBillingYearMonth())) {
                sum = sum.add(p.getAmountPaid());
            }
        }
        return sum;
    }

    // Money collected from everyone for one billing month
    public BigDecimal collectedIn(YearMonth month) {
        BigDecimal sum = BigDecimal.ZERO;

        for (Payment p : payments) {
            if (month.equals(p.getBillingYearMonth())) {
                sum = sum.add(p.getAmountPaid());
            }
        }
        return sum;
    }

    // ==========================================
    // DUPLICATE CHECKS (copied from Main)
    // ==========================================

    // An ID is never reused: tenants stay on file after move-out,
    // and old payments/deposits also block the ID.
    public boolean isTenantIdTaken(String id) {
        if (tenant(id) != null || deposit(id) != null) {
            return true;
        }

        for (Payment p : payments) {
            if (p.getTenantID() != null && p.getTenantID().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    public boolean isUsernameTaken(String username) {
        for (Tenant t : tenants) {
            if (t.getUsername() != null && t.getUsername().equalsIgnoreCase(username)) {
                return true;
            }
        }

        for (Admin a : admins) {
            if (a.getUsername() != null && a.getUsername().equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPaymentIdTaken(String id) {
        for (Payment p : payments) {
            if (p.getPaymentID() != null && p.getPaymentID().equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    public String nextPaymentId() {
        int n = payments.size() + 1;
        String id = String.format("P%03d", n);

        while (isPaymentIdTaken(id)) {
            n++;
            id = String.format("P%03d", n);
        }
        return id;
    }

    public String nextTenantId() {
        int n = tenants.size() + 1;
        String id = String.format("T%03d", n);

        while (isTenantIdTaken(id)) {
            n++;
            id = String.format("T%03d", n);
        }
        return id;
    }

    // ==========================================
    // MONTH LOGIC (copied from Main's report)
    // ==========================================

    private static LocalDate dateOrNull(String value) {
        if (value == null) {
            return null;
        }

        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // Was this tenant living in the building at any time during the month?
    public boolean activeDuring(Tenant tenant, YearMonth month) {
        LocalDate moveIn = dateOrNull(tenant.getMoveInDate());

        if (moveIn == null || YearMonth.from(moveIn).isAfter(month)) {
            return false;
        }

        LocalDate moveOut = dateOrNull(tenant.getMoveOutDate());

        return moveOut == null || !YearMonth.from(moveOut).isBefore(month);
    }

    public boolean roomOccupiedDuring(Apartment apartment, YearMonth month) {
        for (Tenant t : tenants) {
            if (t.getRoomNumber().equalsIgnoreCase(apartment.getRoomNumber())
                    && activeDuring(t, month)) {
                return true;
            }
        }
        return false;
    }
}