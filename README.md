# RentLog: Java-Based Apartment Rental & Tenant Management System

> **OOP 101 – Object-Oriented Programming** | College of Informatics and Computing Sciences, Computer Science Department
> 1st Semester, AY 2026-2027 | Section: IT 2104 | Team: Sir Jayson Pogi Team

---

## Table of Contents

1. [Project Title](#1-project-title)
2. [Project Description](#2-project-description)
3. [Problem Being Addressed](#3-problem-being-addressed)
4. [Project Objectives](#4-project-objectives)
5. [Target Users](#5-target-users)
6. [Main Features](#6-main-features)
7. [Technologies Used](#7-technologies-used)
8. [Team Members and Roles](#8-team-members-and-roles)
9. [OOP Concepts Demonstrated](#9-oop-concepts-demonstrated)
10. [Instructions for Running the Program](#10-instructions-for-running-the-program)
11. [Sample Screenshots](#11-sample-screenshots)
12. [Known Limitations](#12-known-limitations)
13. [Future Improvements](#13-future-improvements)
14. [Project Contributors](#14-project-contributors)

---

## 1. Project Title

**RentLog: Java-Based Apartment Rental & Tenant Management System**

---

## 2. Project Description

RentLog is a Java desktop and command-line application that helps apartment owners, landlords, and property managers run their rental operations in an organized way. It keeps tenant profiles, tracks vacant and occupied units, manages lease contracts and due dates, records partial and full rental payments, computes remaining monthly balances, and monitors security deposits and advance payments.

The system offers two interfaces that share the same data:

- **Swing GUI** (`RentLogUI`): an interactive desktop interface with a dashboard, charts, and notifications.
- **Command-Line Interface** (`Main`): a full-featured console version with numbered menus.

Both interfaces read from and write to the same JSON files, so records stay consistent no matter which one is used. RentLog also generates real-time overdue and lease alerts and monthly financial summary reports.

---

## 3. Problem Being Addressed

Managing apartment units and tenant records manually often leads to:

- Misplaced tenant and lease information
- Inaccurate balance calculations
- Unorganized payment and deposit records
- Missed due dates and expiring leases

These problems affect apartment owners, landlords, and property managers who handle tenants, lease terms, and monthly collections. RentLog replaces manual record-keeping with a structured, reliable system that reduces human error.

---

## 4. Project Objectives

### General Objective

To develop a Java-based apartment rental management system with dual-portal access across both a Swing graphical user interface (GUI) and a command-line interface (CLI) that streamlines property operations by tracking tenant profiles, apartment occupancy, lease contracts, payment histories, and security deposits using Object-Oriented Programming (OOP) principles and JSON data persistence.

### Specific Objectives

| Objective | Description |
| --- | --- |
| **OOP Architecture** | Implement the four core OOP pillars (Encapsulation, Inheritance, Polymorphism, Abstraction) across an 11-class architecture: `Main`, `RentLogUI`, `DataStore`, `User`, `Admin`, `Tenant`, `Apartment`, `Rental`, `Payment`, `Deposit`, and the `Reportable` interface. |
| **Tenant, Apartment & Lease Lifecycle** | Let administrators manage apartment units and pricing, register tenants with lease contracts, view active and historical records, update contact numbers, and process move-outs while preserving historical payment and deposit records. |
| **Automated Billing & Balance Tracking** | Compute remaining monthly balances using `BigDecimal` by aggregating payments within the same billing month (`YearMonth`). Support Cash, Bank Transfer, GCash, and Advance Payment methods, prevent overpayment, and update payment status (Paid, Partial, Unpaid). |
| **Deposit & Advance Payment Management** | Track security deposits and advance payments, advance usage, refundable balances, and holding statuses (Held, Applied, Refunded). Deposit application and refund are only allowed after tenant move-out. |
| **Alerts & Monthly Financial Reporting** | Provide alerts for expired leases, leases expiring within 30 days, and overdue rent, plus monthly financial and historical occupancy reports using Java collections and `java.time`. |
| **Dual-Portal Authentication & Security** | Provide separate Landlord/Admin and read-only Tenant portals in both GUI and CLI with credential validation, active-account verification, password changes, and a 3-attempt login lockout (60 seconds). |

---

## 5. Target Users

| User | How They Use RentLog |
| --- | --- |
| **Landlord / Property Manager** | Logs into the Administrator Portal (GUI or CLI) to manage apartment units and rental rates, register tenants with lease terms, view active and moved-out tenants, update contact details, process move-outs, record partial or full payments (including advance deductions), apply or refund deposits after move-out, monitor overdue and lease alerts, and generate monthly financial and occupancy reports. |
| **Tenant (View Mode)** | Logs into the Tenant Portal (GUI or CLI) to view their rental profile, assigned room, monthly lease rate, due day, next due date, lease expiration status, current-month balance summary, and payment receipts, and to change their account password. |

---

## 6. Main Features

| Feature | Description |
| --- | --- |
| **Dual-Portal Sign-In & Lockout Protection** | Role selection with separate Admin and Tenant logins, polymorphic credential verification, active-tenant login enforcement, password updates, and a 3-attempt / 60-second lockout. |
| **Interactive Swing Desktop UI & Dashboard** | Landlord dashboard with KPI cards, income trend chart (Monthly/Quarterly/Yearly), occupancy donut chart, upcoming due dates, live table filtering, global search, and a notification bell with badge alerts. |
| **Manage Apartments** | Add rooms, view occupancy summaries, update monthly rent (affects new leases only; existing lease snapshots are preserved), and remove vacant rooms with no tenant history. |
| **Register New Tenant & Lease** | Register tenants with unique ID and username validation, assign a vacant room, record move-in date, lease expiration, and rent due day (1-31), set initial security deposit and advance payment, create a `Rental` lease snapshot, and mark the room Occupied. |
| **View All Tenants** | Display active and moved-out tenants with contact details, rooms, move-in/move-out dates, account status, and summary counts. |
| **Update Tenant Information** | Look up an active tenant by ID and update their validated contact number. |
| **Move Out Tenant** | Mark a tenant Inactive with a validated move-out date, revoke portal access, set the apartment back to Vacant, and keep all historical records. |
| **Record Tenant Payment** | Record payments (Cash, Bank Transfer, GCash, Advance Payment) with unique Payment ID validation, reference numbers (required for Cash and Bank Transfer), separate payment date and billing month, advance deduction, overpayment prevention, balance computation, and formatted receipts. |
| **Manage Deposits** | View all deposit and advance records (original advance, advance used, remaining advance, refundable amount, tenant status), look up by Tenant ID, and apply or refund deposits after move-out. |
| **Automated Lease & Overdue Alerts** | Alerts for expired leases, leases expiring within 30 days, and overdue rent with exact days late. |
| **Tenant Portal (View Mode)** | Read-only access to profile, lease terms, next due date, current-month balance, payment receipt history, and password change. |
| **Monthly Financial & Occupancy Report** | For any selected `YYYY-MM` month: cash received by payment date (by method), expected vs. collected rent, collection efficiency percentage, active tenants, and historical room occupancy. |
| **Shared JSON Data Persistence (Gson)** | Saves and loads system state across six JSON files shared by the GUI and CLI, including default account setup and legacy record migration. |

---

## 7. Technologies Used

| Technology | Purpose |
| --- | --- |
| **Java** | Core programming language (OOP) |
| **Java Swing** | Graphical user interface (`RentLogUI`) |
| **Console / CLI** | Text-based interface (`Main`) |
| **Google Gson 2.10.1** | JSON serialization and deserialization |
| **JSON files** | Local data storage (`admin.json`, `tenants.json`, `apartments.json`, `payments.json`, `deposits.json`, `rentals.json`) |
| **`java.math.BigDecimal`** | Accurate currency calculations |
| **`java.time`** (`LocalDate`, `YearMonth`) | Date, due date, and billing month handling |
| **Java Collections** (`ArrayList`, `HashMap`, `TreeMap`) | In-memory data management |
| **Git & GitHub** | Version control and collaboration |

---

## 8. Team Members and Roles

**Team Name:** Sir Jayson Pogi Team | **Section:** IT 2104

| Member | Role | Responsibilities |
| --- | --- | --- |
| Sanchez, Althea P. | Lead Programmer | Coordinates the implementation of Java classes, methods, and OOP features. |
| Cepillo, Steven Nicholas A. | Documentation | Maintains the README, testing documentation, and screenshots; assists in checking application functionality. |
| Evangelista, Haira Joice A. | System/Design Lead | Develops system requirements, class designs, UML diagrams, and overall application structure. |
| Macaraig, Austine Jay U. | Project Leader | Coordinates tasks, monitors deadlines, communicates with the instructor, and facilitates team meetings. |
| Mendoza, Mark Airon O. | Documentation | Maintains the README, testing documentation, and screenshots; assists in checking application functionality. |

---

## 9. OOP Concepts Demonstrated

| OOP Concept | How It Is Used in RentLog |
| --- | --- |
| **Classes and Objects** | Core domain objects (`User`, `Admin`, `Tenant`, `Apartment`, `Rental`, `Payment`, `Deposit`) are coordinated by `Main` (CLI controller), `RentLogUI` (Swing GUI), and `DataStore` (shared persistence/query layer). |
| **Methods** | Business logic is encapsulated in methods such as `authenticate()`, `calculateBalance()`, `updatePaymentStatus()`, `markOccupied()`, `markVacant()`, `updateContact()`, `moveOut()`, `useAdvance()`, `applyDeposit()`, `refundDeposit()`, `checkLeaseStatus()`, `dueDateFor()`, `calculateNextDueDate()`, `saveAll()`, and `reload()`. |
| **Encapsulation** | Entity fields are `private` or `protected` (e.g., `private BigDecimal remainingBalance`, `protected String fullName`). Setters validate input: `BigDecimal` normalized to 2 decimal places, non-empty checks, regex contact validation, `LocalDate`/`YearMonth` parsing, and reference number rules. |
| **Inheritance** | The abstract class `User` is extended by `Admin` and `Tenant`, reusing shared identity fields, password verification, and validation helpers. |
| **Polymorphism** | `authenticate()` is overridden (enforcing active status for `Tenant` logins), the abstract `getRoleDetails()` is implemented by `Admin` and `Tenant`, and `Reportable` references are handled uniformly when generating summaries. |
| **Abstraction** | The abstract class `User` and the `Reportable` interface (`getReportSummary()`) enforce consistent contracts across domain entities. |
| **Exception Handling** | `try-catch` blocks handle `NumberFormatException`, `DateTimeParseException`, `IOException`, `JsonParseException`, `IllegalArgumentException`, and `IllegalStateException`, with custom dialogs and prompts. |
| **Collections & File I/O** | `ArrayList<Tenant>`, `ArrayList<Apartment>`, `ArrayList<Payment>`, `ArrayList<Deposit>`, `ArrayList<Rental>`, `ArrayList<Admin>`, and `HashMap`/`TreeMap` structures, persisted via Gson (`TypeToken`, `BufferedReader`, `BufferedWriter`). |

### Class Overview

| Class | Purpose |
| --- | --- |
| `Main` | CLI entry point and console controller: menus, authentication, lockout tracking, input validation, legacy JSON migration, workflows, and Gson file I/O. |
| `RentLogUI` | Swing GUI with dashboard, charts, tables, and notifications. |
| `DataStore` | Shared persistence and query layer used by both interfaces. |
| `User` *(abstract)* | Base class for shared identity, credentials, and validation. |
| `Admin` | Administrator account (extends `User`). |
| `Tenant` | Registered tenant with room assignment and move-in/out lifecycle (extends `User`, implements `Reportable`). |
| `Apartment` | Rental unit with room number, monthly rent, and Vacant/Occupied status. |
| `Rental` | Lease contract snapshot, due day, due-date clamping, and lease expiration status. |
| `Payment` | Rent payment transaction, method/reference validation, balance computation, and receipts. |
| `Deposit` | Security deposit and advance payment tracking with Held/Applied/Refunded states (implements `Reportable`). |
| `Reportable` *(interface)* | Uniform contract for generating report summaries. |

*The classes may change as the project is developed.*

---

## 10. Instructions for Running the Program

### Prerequisites

- **Java Development Kit (JDK)** installed (`java -version` and `javac -version` should both work)
- **Google Gson 2.10.1** (`gson-2.10.1.jar`)
- A terminal or an IDE such as IntelliJ IDEA, Eclipse, or VS Code

### 1. Clone the repository

```bash
git clone https://github.com/sanchez417/RentLog.git
cd RentLog
```

### 2. Add the Gson library

Download `gson-2.10.1.jar` and place it in a `lib/` folder (or add it to your IDE's project libraries).

### 3. Compile

Adjust the paths below to match your project's folder structure.

**Windows**
```bash
javac -cp "lib/gson-2.10.1.jar" -d out *.java
```

**macOS / Linux**
```bash
javac -cp "lib/gson-2.10.1.jar" -d out *.java
```

### 4. Run

**Command-Line Interface (CLI)**

Windows:
```bash
java -cp "out;lib/gson-2.10.1.jar" Main
```

macOS / Linux:
```bash
java -cp "out:lib/gson-2.10.1.jar" Main
```

**Swing GUI**

Windows:
```bash
java -cp "out;lib/gson-2.10.1.jar" RentLogUI
```

macOS / Linux:
```bash
java -cp "out:lib/gson-2.10.1.jar" RentLogUI
```

### Running in an IDE

1. Open the project folder in your IDE.
2. Add `gson-2.10.1.jar` to the project's libraries/classpath.
3. Run `Main.java` for the CLI, or `RentLogUI.java` for the GUI.

### Using the program

1. Choose a portal: **Landlord / Admin** or **Tenant (View Mode)**.
2. Log in with your username and password. After 3 failed attempts, login is locked for 60 seconds.
3. Use the dashboard/sidebar (GUI) or numbered menu (CLI) to manage apartments, tenants, payments, deposits, alerts, and reports.
4. Data is saved automatically to the JSON files and shared by both interfaces.

> **Default admin account:** *(add the default username and password from the project's setup here)*

---

## 11. Sample Screenshots

> Add screenshots to a `screenshots/` folder and update the paths below.

| Screen | Screenshot |
| --- | --- |
| Portal Selection / Login | `![Login](screenshots/login.png)` |
| Landlord Dashboard (GUI) | `![Dashboard](screenshots/dashboard.png)` |
| Manage Apartments | `![Apartments](screenshots/apartments.png)` |
| Register New Tenant & Lease | `![Register Tenant](screenshots/register-tenant.png)` |
| Record Payment & Receipt | `![Payment](screenshots/payment.png)` |
| Manage Deposits | `![Deposits](screenshots/deposits.png)` |
| Lease & Overdue Alerts | `![Alerts](screenshots/alerts.png)` |
| Monthly Financial Report | `![Report](screenshots/monthly-report.png)` |
| Tenant Portal (View Mode) | `![Tenant Portal](screenshots/tenant-portal.png)` |
| CLI Main Menu | `![CLI Menu](screenshots/cli-menu.png)` |

---

## 12. Known Limitations

- **Local JSON storage:** Data is stored in local JSON files instead of a hosted relational database (e.g., MySQL), so it is not suited for many simultaneous users.
- **No online payment gateway:** Payments (including GCash and Bank Transfer) must be recorded manually by the administrator. They are not processed through live payment channels.
- **Read-only Tenant Portal:** Tenants can only view their information and change their password. They cannot make payments or edit their profile.
- **Single administrator workflow:** The system is designed for one landlord/property manager setup and does not include multi-user roles or permissions beyond Admin and Tenant.
- **No automated notifications:** Alerts appear inside the application only. No email or SMS reminders are sent.
- **Deposit restrictions:** Security deposits can only be applied or refunded after a tenant has moved out.

---

## 13. Future Improvements

- Migrate from JSON files to a relational database (e.g., MySQL or SQLite)
- Integrate online payment gateways (e.g., GCash, bank APIs) for automatic payment recording
- Send email or SMS reminders for due dates, overdue rent, and expiring leases
- Allow tenants to submit payments, maintenance requests, and profile updates
- Export reports and receipts to PDF or Excel
- Support multiple properties and multiple administrator accounts with role permissions
- Add password hashing and stronger security measures
- Build a web or mobile version of RentLog
- Add automated unit tests and data backup/restore features

---

## 14. Project Contributors

| Name | SR-Code | GitHub Username |
| --- | --- | --- |
| Cepillo, Steven Nicholas A. | 25-05021 | [Cepillo-25-05021](https://github.com/Cepillo-25-05021) |
| Evangelista, Haira Joice A. | 25-05052 | [25-05052-cmd](https://github.com/25-05052-cmd) |
| Macaraig, Austine Jay U. | 25-07926 | [ostin-dotcom](https://github.com/ostin-dotcom) |
| Mendoza, Mark Airon O. | 25-06457 | [mendoza-25-06457](https://github.com/mendoza-25-06457) |
| Sanchez, Althea P. | 25-04787 | [sanchez417](https://github.com/sanchez417) |

**Instructor:** Ms. Fatima Marie P. Agdon, MSCS

**Repository:** [https://github.com/sanchez417/RentLog](https://github.com/sanchez417/RentLog)

---

*Developed for OOP 101 – Object-Oriented Programming, 1st Semester, AY 2026-2027.*
