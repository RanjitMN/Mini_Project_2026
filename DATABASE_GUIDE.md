# Database Guide & Architecture: Village Energy Coordinator

## 1. What is a Database? (In Simple Terms)
A **database** is like an organized digital filing cabinet or digital Excel spreadsheet managed by a software server (like **MySQL** inside your XAMPP).
- Without a database, when your Java program terminates, all the simulation numbers, generation records, and allocations are lost from RAM memory.
- With database connectivity (**JDBC** - Java Database Connectivity), every piece of data is permanently saved into **tables** composed of **rows** and **columns**.

---

## 2. Your Database Setup with XAMPP

Your XAMPP includes **MySQL / MariaDB** running locally on your computer:
- **Host / Port**: `localhost:3306`
- **Database Name**: `energy_coordinator` (automatically created by our Java app!)
- **Default Username**: `root`
- **Default Password**: *(blank / empty)*

### How to View the Tables in phpMyAdmin
1. Open your web browser and go to: **[http://localhost/phpmyadmin](http://localhost/phpmyadmin)**
2. In the left sidebar, click on **`energy_coordinator`**.
3. You will see the 3 tables:
   - **`hourly_generation`**: Stores solar & wind energy available for each hour (0 to 23).
   - **`consumer_loads`**: Stores every consumer load request (Primary Clinic, Water Pump, School, Households), their priority level (Critical, Essential, Deferrable), demanded energy, and actual allocated energy.
   - **`simulation_results`**: Stores the coordinator outcome for each hour (Total Allocated, Unmet Deficit, Curtailed Surplus, plus **Battery Charged, Discharged, and State of Charge (SoC)**).

---

## 3. Database Schema

### Table 1: `hourly_generation`
| Column | Type | Description |
| :--- | :--- | :--- |
| `hour` | INT (Primary Key) | Hour of the day (0 to 23) |
| `generation_kwh` | DOUBLE | Solar + Wind energy generation in kWh |
| `updated_at` | VARCHAR(64) | Timestamp of entry |

### Table 2: `consumer_loads`
| Column | Type | Description |
| :--- | :--- | :--- |
| `id` | INT AUTO_INCREMENT (Primary Key) | Unique load identifier |
| `hour` | INT | Hour of the day (0 to 23) |
| `name` | VARCHAR(128) | Name (e.g. "Primary Health Clinic", "Community Water Pump") |
| `load_type` | VARCHAR(64) | `DIVISIBLE` or `MINIMUM_SERVICE` |
| `priority` | VARCHAR(32) | `CRITICAL`, `ESSENTIAL`, or `DEFERRABLE` |
| `priority_level` | INT | Numerical priority level (3 = Critical, 2 = Essential, 1 = Deferrable) |
| `demand` | DOUBLE | Requested energy in kWh |
| `min_required` | DOUBLE | Minimum energy required (for MinimumServiceLoad) |
| `allocated` | DOUBLE | Energy actually allocated by priority dispatch algorithm |

### Table 3: `simulation_results`
| Column | Type | Description |
| :--- | :--- | :--- |
| `hour` | INT (Primary Key) | Hour of the day (0 to 23) |
| `generation` | DOUBLE | Available generation in kWh |
| `total_allocated` | DOUBLE | Total energy served across all loads in kWh |
| `unmet_demand` | DOUBLE | Total energy unmet (shortage / deficit) in kWh |
| `curtailed_energy` | DOUBLE | Surplus energy (curtailed after battery charging) |
| `battery_charged` | DOUBLE | Energy absorbed into battery bank this hour (kWh) |
| `battery_discharged` | DOUBLE | Energy released from battery to power loads this hour (kWh) |
| `battery_soc` | DOUBLE | Battery State of Charge (stored kWh) at the end of this hour |
| `simulated_at` | VARCHAR(64) | Timestamp when hour was simulated |

---

## 4. Battery Energy Storage System (BESS)
- **Problem solved**: Bridges the day/night mismatch. Solar peaks between 11 AM and 3 PM when demand is moderate, but village lighting demand peaks between 7 PM and 11 PM when solar is 0.
- **Physics modeled**:
  - Community LiFePO4 battery bank with customizable capacity (default 200 kWh).
  - 92% charging efficiency and 95% discharging efficiency (heat and inverter conversion losses).
  - Max C-rate power limit (60 kW inverter cap).
- **Interactive UI**: Users can change battery capacity via the dashboard **Battery Config** modal (e.g. test 0 kWh vs 200 kWh vs 350 kWh) and immediately see the unmet deficit drop from ~120 kWh down to 0 kWh.

---

## 5. How to Run the Application
1. Start **Apache** and **MySQL** in **XAMPP Control Panel**.
2. Double-click **`run_web_app.bat`** (or run `java -cp ".;lib/*" WebServer` in terminal).
3. The dashboard opens automatically in your browser at **[http://localhost:8080](http://localhost:8080)**.
4. Click **"View DB Records"** on the dashboard or visit **phpMyAdmin** to inspect the live SQL rows!
