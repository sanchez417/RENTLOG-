<p align="center">
  <img src="logo.png" alt="RentLog logo" width="220">
</p>

# 1. Project Title

**RentLog – Apartment Rental Management System**

> A Java (OOP) application for managing tenants, apartments, leases, payments and deposits, available as a **console app** and a **Swing desktop GUI** that share the same JSON data files.

---

# 2. Project Description

RentLog is a property and rental management system written in Java (package `com.rentlog`). It helps a landlord or property manager keep track of apartments, tenants, lease agreements, monthly rent payments and security deposits in one place.

The system has two entry points that work on the **same set of JSON files**, so data entered in one is immediately available in the other:

| Entry point | Class | Description |
|---|---|---|
| Console application | `Main` | Menu-driven CLI with an Administrator Portal and a Tenant Portal (view mode). |
| Desktop application | `RentLogUI` | Swing GUI with a role chooser, sign-in screen, landlord dashboard (charts, tables, alerts, global search) and tenant portal. |

Data is stored locally as pretty-printed UTF-8 JSON (using Gson), so no database installation is required.

---

# 3. Problem Being Addressed

Small landlords and apartment managers often track rent in notebooks, chat messages or spreadsheets. This leads to:

- Missed or forgotten rent due dates and lease expirations.
- Unclear balances when tenants pay in installments or for a different month than the payment date.
- Lost records when a tenant moves out (names, payment history and deposits disappear).
- Confusion over deposits and advance payments (what was used, what is refundable).
- No quick way to see monthly income, occupancy and unpaid rent.

RentLog replaces manual tracking with a single, validated system that computes balances automatically, raises alerts, and keeps a permanent history.

---

# 4. Project Objectives

1. Provide a secure login for the landlord and for tenants, with a lockout after repeated failed attempts.
2. Manage apartments (add, view, change rent, remove) and their Occupied/Vacant status.
3. Register tenants and automatically create their lease, deposit and room assignment in one step.
4. Record payments per **billing month** with support for partial payments, multiple payment methods and reference numbers.
5. Track security deposits and advance payments (Held / Applied / Refunded).
6. Warn the landlord about overdue rent and expiring or expired leases.
7. Generate a monthly financial report (cash received, rent expected vs. collected, occupancy).
8. Preserve all history after a tenant moves out.
9. Demonstrate core **Object-Oriented Programming** principles in a real-world system.

---

# 5. Target Users

| User | How they use RentLog |
|---|---|
| **Landlord / Property Manager (Admin)** | Manages apartments, tenants, leases, payments, deposits, alerts and reports. |
| **Tenant** | Logs in (view mode) to see their profile, lease summary, payment history, current-month balance and next due date, and can change their own password. |
| **Students / Instructors** | Reference project that demonstrates OOP concepts in Java. |

---

# 6. Main Features

### Authentication & Security
- Separate Admin and Tenant login (case-insensitive username, exact password match, minimum 6 characters).
- **Lockout:** 3 consecutive failed attempts lock that account for **60 seconds** (tracked per portal and username).
- Moved-out (Inactive) tenants cannot log in.
- Change-password option for both roles.

### Administrator Portal
| # | Feature | Highlights |
|---|---|---|
| 1 | Register New Tenant | Unique tenant ID (never reused), contact format check, vacant-room check, lease end after move-in, due day 1–31, unique username, security deposit + advance payment. Creates `Tenant`, `Deposit` and `Rental` together and marks the room Occupied. |
| 2 | View All Tenants | Separates Active and Moved-Out tenants with totals. |
| 3 | Update Tenant Information | Update contact number. |
| 4 | Move Out Tenant | Date validation, marks tenant Inactive, frees the room, keeps all records, reminds if the deposit is still Held. |
| 5 | Manage Apartments | View (with occupancy counts), add, change rent (affects **new** leases only), remove (blocked if occupied or has tenant history). |
| 6 | Record Tenant Payment | Payment date vs. billing month, partial payments, overpayment protection, reference number required for GCash / Bank Transfer, Advance Payment deducts from the tenant's advance. |
| 7 | Manage Deposits | View, search, apply, refund. Refundable amount becomes PHP0.00 once Applied or Refunded. |
| 8 | View Payment History | Full list of recorded payments. |
| 9 | Monthly Financial Report | Cash received by method, rent expected vs. collected, collection efficiency %, historical occupancy for the chosen month. |
| 10 | Alerts | `[LEASE EXPIRED]`, `[LEASE ENDING SOON]` (within 30 days) and `[OVERDUE RENT]` (with days late). |
| 11 | Change My Password | |
| 12 | Logout | |

### Tenant Portal (View Mode)
- Personal profile and room details
- Lease summary (rent, due day, next due date, lease status)
- Payment history and balances, including current-month paid / balance

### Desktop GUI (`RentLogUI`)
- Role chooser and styled sign-in screen
- Landlord dashboard with month picker, charts (donut / line), stat cards and alerts bell
- Filterable tables for apartments, tenants, rentals, payments and deposits
- Modal forms for registering tenants, recording payments, move-out, rent change, etc.
- Global search and monthly reports view
- Tenant portal (profile, payments, change password)

### Data Persistence
All changes are saved as pretty-printed JSON: `admin.json`, `tenants.json`, `apartments.json`, `payments.json`, `deposits.json`, `rentals.json`. Both the console app and the GUI read and write these same files.

---

# 7. Technologies Used

| Technology | Purpose |
|---|---|
| **Java** (JDK 8 or higher; JDK 11+ recommended) | Core language |
| **Java Swing / AWT** | Desktop GUI (`RentLogUI`) |
| **Gson** (`com.google.code.gson`) | JSON reading and writing |
| **`java.time` API** (`LocalDate`, `YearMonth`) | Dates, billing months, due-date logic |
| **`BigDecimal`** | Accurate money calculations |
| **JSON files** | Local data storage (no database needed) |

---

# 8. Team Members and Roles

> Replace the placeholders below with your group's details.

| Member | Role | Responsibilities |
|---|---|---|
| _Full Name_ | Project Leader / Lead Developer | _e.g., system design, `Main` controller_ |
| _Full Name_ | Developer | _e.g., domain classes (`Tenant`, `Apartment`, `Rental`)_ |
| _Full Name_ | Developer | _e.g., billing logic (`Payment`, `Deposit`)_ |
| _Full Name_ | UI Developer | _e.g., Swing GUI (`RentLogUI`)_ |
| _Full Name_ | Documentation / Tester | _e.g., UML diagram, testing, README_ |

---

# 9. OOP Concepts Demonstrated

| Concept | Where it appears in RentLog |
|---|---|
| **Abstraction** | `User` is an abstract class with the abstract method `getRoleDetails()`. `Reportable` is an interface that declares `getReportSummary()`. |
| **Inheritance** | `Admin` extends `User`; `Tenant` extends `User`. Shared identity, credentials and validation live in `User`. |
| **Polymorphism** | `authenticate()` is called on any `User` reference. `Tenant` overrides it to also require `isActive()`. `getRoleDetails()` and `getReportSummary()` behave differently in each subclass. |
| **Encapsulation** | Private/protected fields with validated setters (e.g., `setContactNumber()`, `setPassword()`, `setMonthlyRent()`), so invalid data can't enter an object. |
| **Interfaces** | `Reportable` is implemented by both `Tenant` and `Deposit`. |
| **Composition / Aggregation** | `RentLogUI` owns a `DataStore`, and `DataStore` holds lists of every entity class. |
| **Single Responsibility** | `Payment` owns billing math, `Rental` owns lease/due-date logic, `DataStore` owns persistence and lookups. |
| **Static utility & validation methods** | `User.isValidPassword()`, `Payment.normalizeMoney()`, `Payment.requiresReference()`. |
| **Exception handling** | `IllegalArgumentException` / `IllegalStateException` for invalid input and illegal state changes (e.g., applying an already-refunded deposit). |

### Class Overview

| Class / Interface | Type | Role |
|---|---|---|
| `Reportable` | Interface | Contract for report summaries |
| `User` | Abstract class | Shared identity, credentials, validation |
| `Admin` | Class (extends `User`) | Landlord / administrator account |
| `Tenant` | Class (extends `User`, implements `Reportable`) | Tenant, room, move-in/out, status |
| `Apartment` | Class | Room, monthly price, Occupied/Vacant |
| `Rental` | Class | Lease snapshot, due day, expiry |
| `Payment` | Class | Payment record and balance computation |
| `Deposit` | Class (implements `Reportable`) | Security deposit and advance payment |
| `DataStore` | Helper class | JSON persistence, lookups, money totals, ID generation |
| `Main` | Controller | Console application |
| `RentLogUI` | Controller (extends `JFrame`) | Swing desktop application |

A full UML class diagram is available in `RentLog_UML_Class_Diagram.png` / `.svg`.

---

# 10. Instructions for Running the Program

### Requirements
- JDK 8 or higher installed (`java -version` to check)
- Gson JAR, e.g., `gson-2.10.1.jar`, downloaded from Maven Central

### Project structure
```
RentLog/
├── lib/
│   └── gson-2.10.1.jar
├── src/com/rentlog/
│   ├── Admin.java
│   ├── Apartment.java
│   ├── DataStore.java
│   ├── Deposit.java
│   ├── Main.java
│   ├── Payment.java
│   ├── Rental.java
│   ├── Reportable.java
│   ├── RentLogUI.java
│   ├── Tenant.java
│   └── User.java
├── logo.png
└── README.md
```

### Compile
**Windows**
```bash
javac -cp "lib/gson-2.10.1.jar" -d out src/com/rentlog/*.java
```
**macOS / Linux**
```bash
javac -cp "lib/gson-2.10.1.jar" -d out src/com/rentlog/*.java
```

### Run the console application
**Windows**
```bash
java -cp "out;lib/gson-2.10.1.jar" com.rentlog.Main
```
**macOS / Linux**
```bash
java -cp "out:lib/gson-2.10.1.jar" com.rentlog.Main
```

### Run the desktop (Swing) application
**Windows**
```bash
java -cp "out;lib/gson-2.10.1.jar" com.rentlog.RentLogUI
```
**macOS / Linux**
```bash
java -cp "out:lib/gson-2.10.1.jar" com.rentlog.RentLogUI
```

> Run these commands from the project root so the `.json` data files are created and read in the same folder. If you use an IDE (IntelliJ, Eclipse, NetBeans), add the Gson JAR to the project libraries and run `Main` or `RentLogUI`.

### Default accounts (created on first run)

| Role | Username | Password |
|---|---|---|
| Administrator (`A001`, Property Manager) | `admin` | `admin123` |
| Tenant (`T001`, Juan Dela Cruz, Room 101) | `tenant1` | `password123` |

First-run demo data also includes apartments **101** (PHP5,000.00, Occupied), **102** (PHP5,500.00, Vacant) and **201** (PHP6,000.00, Vacant), plus a 1-year lease for T001 and a PHP5,000.00 security deposit with a PHP5,000.00 advance payment.

> The default admin password should be changed after the first login (Administrator Menu → *Change My Password*).

### Sample console flow
```
==================================================
          WELCOME TO RENTLOG
==================================================
1. Landlord / Admin Portal
2. Tenant Portal (View Mode)
3. Exit System
Enter choice (1-3):
```

---

# 11. Sample Screenshots

> Add your own screenshots to a `screenshots/` folder and update the file names below.

| Screen | Preview |
|---|---|
| Main menu (console) | `![Main Menu](screenshots/console-main-menu.png)` |
| Administrator menu | `![Admin Menu](screenshots/console-admin-menu.png)` |
| Register tenant | `![Register Tenant](screenshots/console-register-tenant.png)` |
| Record payment & receipt | `![Record Payment](screenshots/console-record-payment.png)` |
| Monthly financial report | `![Monthly Report](screenshots/console-monthly-report.png)` |
| Alerts | `![Alerts](screenshots/console-alerts.png)` |
| Tenant portal | `![Tenant Portal](screenshots/console-tenant-portal.png)` |
| GUI role chooser / login | `![GUI Login](screenshots/gui-login.png)` |
| GUI landlord dashboard | `![GUI Dashboard](screenshots/gui-dashboard.png)` |
| GUI payments / deposits | `![GUI Payments](screenshots/gui-payments.png)` |

### Sample output: recording a payment
```
--- RECORD TENANT PAYMENT ---
Enter Tenant ID: T001
Enter Payment Date (YYYY-MM-DD): 2026-11-10
Billing Month this payment is for (YYYY-MM) [ENTER = 2026-11]:

--- RENT DETAILS (2026-11) ---
Monthly Rent: PHP5,000.00
Previously Paid: PHP0.00
Remaining Balance: PHP5,000.00
Enter Amount Paid: PHP3000
Payment Method (Cash/Bank Transfer/GCash/Advance Payment): GCash
Enter Reference Number: GC-987654321

Payment successfully recorded!
Payment ID        : P001
Billing Month     : 2026-11
Amount Paid       : PHP3,000.00
Payment Method    : GCash
Reference No.     : GC-987654321
Remaining Balance : PHP2,000.00
Payment Status    : Partial
```

### Sample output: alerts
```
==================================================
                      ALERTS
==================================================
[OVERDUE RENT] Juan Dela Cruz (T001, Room 101) - PHP2,000.00 unpaid for 2026-11
(due 2026-11-08, 13 day(s) late)
```

---

# 12. Known Limitations

- **Passwords are stored in plain text** in `admin.json` and `tenants.json` (no hashing). This is acceptable for a class project but not for real deployment.
- Data is stored in local JSON files, so there is no multi-user or network access, and no file locking if the console app and GUI are used at the same time.
- Tenants have **view-only** access; they cannot pay or submit requests through the system.
- Payments are recorded manually; there is no integration with GCash, banks or other payment gateways.
- The console app (`Main`) and `DataStore` duplicate some lookup and validation logic.
- One lease per tenant; there is no support for lease renewal history or multiple tenants sharing a room.
- No late fees, penalties or utility bills are computed.
- No automated unit tests.

---

# 13. Future Improvements

- Hash passwords (e.g., BCrypt/SHA-256 with salt) and add password-reset support.
- Replace JSON files with a database (SQLite / MySQL) for reliability and concurrent access.
- Refactor `Main` to use `DataStore` so the CLI and GUI share a single logic layer.
- Add lease renewal, late-fee computation and utility billing.
- Export reports and receipts to PDF/Excel and send email or SMS reminders.
- Let tenants submit payment proofs and maintenance requests.
- Add automated unit tests (JUnit) for billing, deposit and lease logic.
- Package the app as an executable JAR/installer with a build tool (Maven or Gradle).

---

# 14. Project Contributors

> Replace with your group's names, student numbers and contact details.

| Name | Student No. | Email / GitHub |
|---|---|---|
| _Full Name_ | _00-0000-000_ | _name@email.com / @username_ |
| _Full Name_ | _00-0000-000_ | _name@email.com / @username_ |
| _Full Name_ | _00-0000-000_ | _name@email.com / @username_ |

**Course / Section:** _CCS/OOP – Section ___
**Instructor:** _Instructor's Name_
**Academic Year:** _2026–2027_

---

<p align="center"><sub>RentLog · Built with Java · © 2026</sub></p>
