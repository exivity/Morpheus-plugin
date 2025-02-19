# Exivity FinOps Plugin for Morpheus

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)  
**Integrate Exivity's financial operations analytics directly into Morpheus for granular cost tracking and infrastructure spending insights.**

---

## 📖 Overview

This plugin bridges Morpheus infrastructure management with Exivity's FinOps capabilities. Monitor spending, generate breakdowns by service/category, and access Exivity reports seamlessly within Morpheus.

---

## 🛠️ Prerequisites

Before installation, ensure:
- **Morpheus Instance**: Configured and accessible.
- **Exivity Instance**: Configured with a pre-defined Morpheus report.
- **Exivity Report Requirements**:
  - One hierarchy level in the report must correspond to the **Instance** in Morpheus.
  - The report’s `Key` column must match the `InstanceUUID` in Morpheus.
  - The `Name` column should map to a user-friendly identifier.
- **Plugin JAR File**: Downloaded and accessible.

---

## 📥 Installation

### Step 1: Upload the Plugin
1. Log in to your Morpheus instance.
2. Navigate to **Administration > Integrations**.
3. Go to the **Plugins** tab.
4. Click **ADD**.
5. Select **Add File** and upload the Exivity FinOps plugin jar`.
6. Click **UPLOAD**.

**Success**: A green icon appears next to the plugin in the list.

---

## ⚙️ Configuration

1. In the **Plugins** tab, click the **Edit** icon for the Exivity FinOps plugin.
2. Configure these parameters:

| **Parameter**                | **Description**                                                                 |
|------------------------------|---------------------------------------------------------------------------------|
| `EXIVITY API ENDPOINT`        | URL of your Exivity instance (e.g., `https://exivity.yourcompany.com`).         |
| `EXIVITY USER NAME`           | Exivity username with access to the Morpheus report.                            |
| `EXIVITY PASSWORD`            | Password for the Exivity user.                                                  |
| `MORPHEUS REPORT ID`          | **How to find**: In Exivity, go to **DATA PIPELINES > REPORTS**. Open your Morpheus report and copy the ID from the URL. |
| `MORPHEUS REPORT DEPTH`       | **How to find**: In Exivity, navigate to **ACCOUNTS > OVERVIEW**. Locate the instance-level account and note its depth. |
| `DEFAULT REPORT DATE RANGE`   | Optional: Set default days for reporting (e.g., `30`).                |

---

## 🖥️ Usage

### Instance View
1. In Morpheus, go to **Provisioning > Instances**.
2. Select an instance and click the **FinOps Exivity** tab.  
   **Features**:
   - **Cost/Usage Breakdown**: Per-instance spending by service.
   - **Date Range Filters**: Choose presets (7/30/90 days) or custom dates.
   - **Exivity Access**: Click **Access Exivity** to dive deeper.

### FinOps Dashboard
1. In Morpheus, navigate to **Operations > Analytics**.
2. Under **FINOPS ANALYTICS**, select **FinOps**.  
   **Features**:
   - **Aggregate Spending**: View costs across all Morpheus instances.
   - **Visualizations**:
     - **Total Charges by Service** (Pie Chart)
     - **Total Charges by Category** (Bar Chart)
   - **Date Range Selection**: Filter data dynamically.

---

## 📜 License

Licensed under the **[Apache License 2.0](LICENSE)**.  

Copyright 2025 Exivity B.V.  

---

*For detailed Exivity report setup, refer to the [Exivity Documentation](https://docs.exivity.com/how%20to%20guides/how%20to%20install%20the%20morpheus%20plugin/).*
