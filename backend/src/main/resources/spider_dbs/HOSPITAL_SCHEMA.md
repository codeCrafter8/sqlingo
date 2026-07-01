# Spider Hospital Database Schema

This document describes the schema of the `hospital.sqlite` database from the Spider dataset (Yale University).

## Database Overview

The hospital database contains information about physicians, departments, hospital staff, patients, appointments,
medications, prescriptions, and medical procedures.

## Tables

### 1. physician

Stores information about physicians working in the hospital.

**Columns:**

- `EmployeeID` (INTEGER) - Primary Key - Unique identifier for the physician
- `Name` (TEXT) - Name of the physician
- `Position` (TEXT) - Job position/title
- `SSN` (TEXT) - Social Security Number

### 2. department

Contains information about hospital departments.

**Columns:**

- `DepartmentID` (INTEGER) - Primary Key - Unique identifier for the department
- `Name` (TEXT) - Department name
- `Head` (INTEGER) - Foreign Key to physician(EmployeeID) - Department head physician

### 3. affiliated_with

Junction table linking physicians to departments.

**Columns:**

- `Physician` (INTEGER) - Foreign Key to physician(EmployeeID)
- `Department` (INTEGER) - Foreign Key to department(DepartmentID)
- `PrimaryAffiliation` (BOOLEAN) - Whether this is the physician's primary affiliation

**Primary Key:** (Physician, Department)

### 4. procedures

Medical procedures that can be performed.

**Columns:**

- `Code` (INTEGER) - Primary Key - Procedure code
- `Name` (TEXT) - Name of the procedure
- `Cost` (REAL) - Cost of the procedure

### 5. trained_in

Tracks which physicians are trained in which procedures.

**Columns:**

- `Physician` (INTEGER) - Foreign Key to physician(EmployeeID)
- `Treatment` (INTEGER) - Foreign Key to procedures(Code)
- `CertificationDate` (DATE) - Date when certified
- `CertificationExpires` (DATE) - Expiration date of certification

**Primary Key:** (Physician, Treatment)

### 6. patient

Information about patients.

**Columns:**

- `SSN` (INTEGER) - Primary Key - Social Security Number
- `Name` (TEXT) - Patient name
- `Address` (TEXT) - Patient address
- `Phone` (TEXT) - Phone number
- `InsuranceID` (INTEGER) - Insurance identification number
- `PCP` (INTEGER) - Foreign Key to physician(EmployeeID) - Primary Care Physician

### 7. nurse

Information about nurses.

**Columns:**

- `EmployeeID` (INTEGER) - Primary Key - Unique identifier
- `Name` (TEXT) - Nurse name
- `Position` (TEXT) - Job position
- `Registered` (BOOLEAN) - Whether the nurse is registered
- `SSN` (INTEGER) - Social Security Number

### 8. appointment

Scheduled appointments between patients and physicians.

**Columns:**

- `AppointmentID` (INTEGER) - Primary Key - Unique identifier
- `Patient` (INTEGER) - Foreign Key to patient(SSN)
- `PrepNurse` (INTEGER) - Foreign Key to nurse(EmployeeID) - Preparation nurse
- `Physician` (INTEGER) - Foreign Key to physician(EmployeeID)
- `Start` (DATETIME) - Appointment start time
- `End` (DATETIME) - Appointment end time
- `ExaminationRoom` (TEXT) - Room where appointment takes place

### 9. medication

Available medications.

**Columns:**

- `Code` (INTEGER) - Primary Key - Medication code
- `Name` (TEXT) - Medication name
- `Brand` (TEXT) - Brand name
- `Description` (TEXT) - Description of the medication

### 10. prescribes

Tracks medication prescriptions.

**Columns:**

- `Physician` (INTEGER) - Foreign Key to physician(EmployeeID)
- `Patient` (INTEGER) - Foreign Key to patient(SSN)
- `Medication` (INTEGER) - Foreign Key to medication(Code)
- `Date` (DATETIME) - Date prescribed
- `Appointment` (INTEGER) - Foreign Key to appointment(AppointmentID)
- `Dose` (TEXT) - Dosage information

**Primary Key:** (Physician, Patient, Medication, Date)

### 11. block

Hospital room blocks.

**Columns:**

- `BlockFloor` (INTEGER) - Floor number
- `BlockCode` (INTEGER) - Block code on the floor

**Primary Key:** (BlockFloor, BlockCode)

### 12. room

Hospital rooms.

**Columns:**

- `RoomNumber` (INTEGER) - Primary Key - Room number
- `RoomType` (TEXT) - Type of room
- `BlockFloor` (INTEGER) - Foreign Key to block(BlockFloor)
- `BlockCode` (INTEGER) - Foreign Key to block(BlockCode)
- `Unavailable` (BOOLEAN) - Whether the room is unavailable

### 13. on_call

Tracks which nurses are on call for which blocks.

**Columns:**

- `Nurse` (INTEGER) - Foreign Key to nurse(EmployeeID)
- `BlockFloor` (INTEGER) - Foreign Key to block(BlockFloor)
- `BlockCode` (INTEGER) - Foreign Key to block(BlockCode)
- `OnCallStart` (DATETIME) - Start of on-call period
- `OnCallEnd` (DATETIME) - End of on-call period

**Primary Key:** (Nurse, BlockFloor, BlockCode, OnCallStart, OnCallEnd)

### 14. stay

Patient hospital stays.

**Columns:**

- `StayID` (INTEGER) - Primary Key - Unique identifier
- `Patient` (INTEGER) - Foreign Key to patient(SSN)
- `Room` (INTEGER) - Foreign Key to room(RoomNumber)
- `StayStart` (DATETIME) - Start of stay
- `StayEnd` (DATETIME) - End of stay

### 15. undergoes

Procedures that patients undergo.

**Columns:**

- `Patient` (INTEGER) - Foreign Key to patient(SSN)
- `Procedures` (INTEGER) - Foreign Key to procedures(Code)
- `Stay` (INTEGER) - Foreign Key to stay(StayID)
- `DateUndergoes` (DATETIME) - Date of procedure
- `Physician` (INTEGER) - Foreign Key to physician(EmployeeID)
- `AssistingNurse` (INTEGER) - Foreign Key to nurse(EmployeeID)

**Primary Key:** (Patient, Procedures, Stay, DateUndergoes)

## Example Queries

### Simple Queries

```sql
-- Get all physicians
SELECT * FROM physician;

-- Get all departments
SELECT * FROM department;

-- Count patients
SELECT COUNT(*) FROM patient;
```

### Complex Queries

```sql
-- Get all appointments with patient and physician names
SELECT 
    a.AppointmentID,
    p.Name AS PatientName,
    ph.Name AS PhysicianName,
    a.Start,
    a.ExaminationRoom
FROM appointment a
JOIN patient p ON a.Patient = p.SSN
JOIN physician ph ON a.Physician = ph.EmployeeID;

-- Get all prescriptions with details
SELECT 
    ph.Name AS PhysicianName,
    p.Name AS PatientName,
    m.Name AS MedicationName,
    pr.Dose,
    pr.Date
FROM prescribes pr
JOIN physician ph ON pr.Physician = ph.EmployeeID
JOIN patient p ON pr.Patient = p.SSN
JOIN medication m ON pr.Medication = m.Code;

-- Get physicians and their departments
SELECT 
    ph.Name AS PhysicianName,
    d.Name AS DepartmentName,
    CASE WHEN aw.PrimaryAffiliation = 1 THEN 'Primary' ELSE 'Secondary' END AS AffiliationType
FROM physician ph
JOIN affiliated_with aw ON ph.EmployeeID = aw.Physician
JOIN department d ON aw.Department = d.DepartmentID;
```

## Natural Language Query Examples

These are example questions that can be asked in natural language:

1. "Show me all physicians"
2. "How many patients are there?"
3. "List all appointments for today"
4. "Which medications has Dr. Smith prescribed?"
5. "Show me all patients in room 101"
6. "Which nurses are on call right now?"
7. "What procedures cost more than $1000?"
8. "Show me all physicians in the Cardiology department"
9. "List all patients with their primary care physicians"
10. "Which rooms are currently unavailable?"

