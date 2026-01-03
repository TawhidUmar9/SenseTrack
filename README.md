#  SenseTrack: Salat Data Collector

> **A specialized Android tool for collecting labeled Accelerometer & Gyroscope data to train Human Activity Recognition (HAR) models for Islamic Prayer (Salat).**

###  The Problem
Training a Machine Learning model to recognize Salat postures (Standing, Ruku, Sujud) requires high-quality, labeled data. Most sensor logger apps just dump raw numbers, leaving you to manually label thousands of rows later—which is painful and prone to error.

###  The Solution
**SenseTrack** solves this by labeling data **in real-time**. It uses the phone's **Volume Buttons** to act as a "clicker," allowing you to switch labels (e.g., from *Standing* to *Ruku*) while the phone is in your pocket. This ensures the data captures natural movement without you looking at the screen.

---

##  Key Features

### 1. Training Mode (Labeled Data)
* **Guided Sequence:** Pre-loaded sequences for 2, 3, or 4 Rakats.
* **Pocket Control:** Press **Volume Down** to advance to the next posture (e.g., `Standing` → `Ruku`). Press **Volume Up** to undo if you made a mistake.
* **High Precision:** Logs data at **50Hz (20ms delay)**, capturing the fine details of transitions between postures.

### 2. Blind Mode (Inference Simulation)
* **Real-world Simulation:** Logs data without specific labels (labeled as `Blind_Data`).
* **Battery Efficient:** Runs at a lower sampling rate (**~5Hz / 200ms**) to simulate a production environment where battery life matters.
* **No Interaction:** Just press start, put the phone in your pocket, and pray naturally.

### 3. Smart Storage
* **User-Defined Storage:** You select exactly where the CSV files are saved (e.g., a specific "Dataset" folder on your phone), making it easy to transfer to your PC later.
* **CSV Format:** Ready-to-use format for Python (Pandas/Scikit-Learn).

---

##  Data Format

The app generates CSV files named:
* `LABELED_salat_TIMESTAMP.csv`
* `BLIND_salat_TIMESTAMP.csv`

**Columns:**

| Timestamp (ns) | Label | SensorType | X | Y | Z |
| :--- | :--- | :--- | :--- | :--- | :--- |
| 5293849102 | Standing (Rakat 1) | ACCEL | 0.04 | 9.81 | 0.32 |
| 5293849122 | Standing (Rakat 1) | GYRO | 0.01 | -0.02 | 0.00 |
| ... | ... | ... | ... | ... | ... |

> **Note:** Accelerometer and Gyroscope readings are logged as separate rows to maximize speed. You will need to merge them by timestamp during preprocessing.

---

##  Installation

1.  **Clone the Repo**
    ```bash
    git clone https://github.com/TawhidUmar9/SenseTrack.git
    ```

2.  **Open in Android Studio**
    * `File` > `Open` > Select the project folder.
    * Let Gradle sync.

3.  **Build & Run**
    * Connect your Android device (USB Debugging must be **ON**).
    * Press the **Run** (Green Play) button.

**Requirements:**
* **Min SDK:** API 24 (Android 7.0)
* **Target SDK:** API 34 (Android 14)

---

##  How to Use

### Collecting Training Data
1.  Open the app and tap **"Select Folder"** to choose where to save files.
2.  Select the number of **Rakats** (2, 3, or 4).
3.  Tap **Start Recording**.
4.  **Put the phone in your pocket.**
5.  Perform the Salat.
    * *Before* you move to the next position, press the **Volume Down** button through your pocket fabric.
    * The app will vibrate (optional future feature) or log the change instantly.
6.  When finished, take the phone out and press **Stop & Save**.

### Collecting Test Data (Blind)
1.  Toggle the **"Blind Mode"** switch to **ON**.
2.  Tap **Start Recording**.
3.  Perform Salat naturally without pressing any volume buttons.
4.  **Stop & Save** when done.
