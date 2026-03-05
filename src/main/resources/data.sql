-- Sample test data for H2 database
-- This file will be automatically executed by Spring Boot on startup

-- Create sample tables
CREATE TABLE IF NOT EXISTS employees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    department VARCHAR(100),
    salary DECIMAL(10, 2),
    hire_date DATE
);

CREATE TABLE IF NOT EXISTS departments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL,
    location VARCHAR(100),
    budget DECIMAL(12, 2)
);

CREATE TABLE IF NOT EXISTS projects (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    start_date DATE,
    end_date DATE,
    budget DECIMAL(12, 2),
    status VARCHAR(50)
);

-- Insert sample data
INSERT INTO departments (name, location, budget) VALUES
('Engineering', 'Warsaw', 500000.00),
('Sales', 'Krakow', 300000.00),
('Marketing', 'Gdansk', 200000.00),
('HR', 'Warsaw', 150000.00);

INSERT INTO employees (first_name, last_name, email, department, salary, hire_date) VALUES
('Jan', 'Kowalski', 'jan.kowalski@company.com', 'Engineering', 8500.00, '2020-01-15'),
('Anna', 'Nowak', 'anna.nowak@company.com', 'Engineering', 9200.00, '2019-03-20'),
('Piotr', 'Wisniewski', 'piotr.wisniewski@company.com', 'Sales', 6800.00, '2021-06-10'),
('Maria', 'Wojcik', 'maria.wojcik@company.com', 'Marketing', 7200.00, '2020-09-05'),
('Tomasz', 'Kaminski', 'tomasz.kaminski@company.com', 'Engineering', 8000.00, '2021-02-28'),
('Katarzyna', 'Lewandowska', 'katarzyna.lewandowska@company.com', 'HR', 6500.00, '2022-01-10'),
('Michal', 'Zielinski', 'michal.zielinski@company.com', 'Sales', 7500.00, '2020-11-15'),
('Agnieszka', 'Szymanska', 'agnieszka.szymanska@company.com', 'Marketing', 7800.00, '2019-08-22');

INSERT INTO projects (name, description, start_date, end_date, budget, status) VALUES
('Website Redesign', 'Complete overhaul of company website', '2023-01-01', '2023-06-30', 75000.00, 'Completed'),
('Mobile App', 'Development of mobile application', '2023-03-15', '2023-12-31', 150000.00, 'In Progress'),
('Marketing Campaign Q1', 'Q1 2024 marketing initiatives', '2024-01-01', '2024-03-31', 50000.00, 'Planning'),
('ERP System Upgrade', 'Upgrade to latest ERP version', '2023-09-01', '2024-03-31', 200000.00, 'In Progress');

