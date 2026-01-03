package com.example.sensetrack;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    // --- SENSORS & STATE ---
    private SensorManager sensorManager;
    private Sensor accelerometer, gyroscope;
    private boolean isRecording = false;
    private List<String> dataBuffer = new ArrayList<>();

    // --- FILE STORAGE ---
    private SharedPreferences prefs;
    private Uri saveDirectoryUri;
    private ActivityResultLauncher<Uri> directoryPicker;

    // --- UI COMPONENTS ---
    private Button btnAction, btnSelectDir;
    private TextView tvCurrentStep, tvNextStep, tvCurrentDir;
    private RadioGroup rgRakats;

    // --- SALAT SEQUENCE LOGIC ---
    private ArrayList<String> salatSequence = new ArrayList<>();
    private int currentStepIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Initialize UI
        btnAction = findViewById(R.id.btnAction);
        btnSelectDir = findViewById(R.id.btnSelectDir);
        tvCurrentStep = findViewById(R.id.tvCurrentStep);
        tvNextStep = findViewById(R.id.tvNextStep);
//        tvCurrentDir = findViewById(R.id.tvCurrentDir); // Ensure you have a TextView for this, or remove this line
        rgRakats = findViewById(R.id.rgRakats);

        // 2. Initialize Sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }

        // 3. Setup File Storage Logic
        setupStorageLogic();

        // 4. Button Listeners
        btnSelectDir.setOnClickListener(v -> directoryPicker.launch(null));

        btnAction.setOnClickListener(v -> {
            if (!isRecording) {
                startRecording();
            } else {
                stopRecording();
            }
        });
    }

    // =========================================================
    // VOLUME BUTTON CONTROL (NAVIGATION)
    // =========================================================
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (isRecording) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                // MOVE FORWARD
                if (currentStepIndex < salatSequence.size() - 1) {
                    currentStepIndex++;
                    updateStepDisplay();
                } else {
                    Toast.makeText(this, "Sequence Finished", Toast.LENGTH_SHORT).show();
                }
                return true; // Returns true to block system volume change
            }
            else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                // MOVE BACKWARD (Undo)
                if (currentStepIndex > 0) {
                    currentStepIndex--;
                    updateStepDisplay();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // =========================================================
    // SALAT SEQUENCE LOGIC
    // =========================================================
    private void generateSalatSequence() {
        salatSequence.clear();

        // Determine number of Rakats
        int rakats = 2;
        int selectedId = rgRakats.getCheckedRadioButtonId();
        if (selectedId == R.id.rb3) rakats = 3;
        else if (selectedId == R.id.rb4) rakats = 4;

        // Build the List
        // Start with Takbeer
        salatSequence.add("Takbeer/Standing");

        for (int i = 1; i <= rakats; i++) {
            // Standard Rakat Cycle
            salatSequence.add("Standing (Rakat " + i + ")");
            salatSequence.add("Ruku (Rakat " + i + ")");
            salatSequence.add("Standing (Post-Ruku)");
            salatSequence.add("Sujud 1 (Rakat " + i + ")");
            salatSequence.add("Sitting Between Sujud");
            salatSequence.add("Sujud 2 (Rakat " + i + ")");

            // Logic for Tashahhud (Sitting)
            // Sit after 2nd Rakat AND at the very end
            if (i == 2 || i == rakats) {
                salatSequence.add("Tashahhud (Sitting)");
            } else {
                // If not sitting, we stand up for next rakat
                salatSequence.add("Standing Up");
            }
        }

        salatSequence.add("Salam (Right)");
        salatSequence.add("Salam (Left)");
        salatSequence.add("Complete");
    }

    private void updateStepDisplay() {
        if (salatSequence.isEmpty()) return;

        String current = salatSequence.get(currentStepIndex);
        tvCurrentStep.setText(current);

        if (currentStepIndex < salatSequence.size() - 1) {
            tvNextStep.setText("Next: " + salatSequence.get(currentStepIndex + 1));
        } else {
            tvNextStep.setText("End of Prayer");
        }
    }

    // =========================================================
    // RECORDING LOGIC
    // =========================================================
    private void startRecording() {
        if (saveDirectoryUri == null) {
            Toast.makeText(this, "Please Select a Folder First!", Toast.LENGTH_LONG).show();
            return;
        }

        if (accelerometer == null || gyroscope == null) {
            Toast.makeText(this, "Sensors not found!", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Prepare Sequence
        generateSalatSequence();
        currentStepIndex = 0;
        updateStepDisplay();

        // 2. Prepare Data Buffer
        isRecording = true;
        dataBuffer.clear();
        dataBuffer.add("Timestamp,Label,SensorType,X,Y,Z");

        // 3. Register Sensors
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME);

        // 4. Update UI
        btnAction.setText("Stop & Save");
        // Disable Rakat selection while recording
        for (int i = 0; i < rgRakats.getChildCount(); i++) rgRakats.getChildAt(i).setEnabled(false);

        Toast.makeText(this, "Recording... Press Vol Down for Next Step", Toast.LENGTH_LONG).show();
    }

    private void stopRecording() {
        isRecording = false;
        sensorManager.unregisterListener(this);

        saveToCsv();

        // Reset UI
        btnAction.setText("Start Recording");
        tvCurrentStep.setText("Ready");
        tvNextStep.setText("");
        for (int i = 0; i < rgRakats.getChildCount(); i++) rgRakats.getChildAt(i).setEnabled(true);
    }

    // =========================================================
    // SENSOR EVENT LISTENER
    // =========================================================
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isRecording && !salatSequence.isEmpty()) {
            String type = (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) ? "ACCEL" : "GYRO";

            // Get Label from our dynamic list
            String currentLabel = salatSequence.get(currentStepIndex);

            String record = event.timestamp + "," + currentLabel + "," + type + "," +
                    event.values[0] + "," + event.values[1] + "," + event.values[2];
            dataBuffer.add(record);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    // =========================================================
    // FILE STORAGE LOGIC
    // =========================================================
    private void setupStorageLogic() {
        prefs = getSharedPreferences("SenseTrackPrefs", MODE_PRIVATE);

        // Handle the user selecting a folder
        directoryPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

                        saveDirectoryUri = uri;
                        prefs.edit().putString("save_directory_uri", uri.toString()).apply();

                        // Optional: Update text view to show folder name
                        DocumentFile df = DocumentFile.fromTreeUri(this, uri);
                        if(df != null && tvCurrentDir != null) tvCurrentDir.setText("Folder: " + df.getName());
                    }
                }
        );

        // Load saved folder on startup
        String savedUri = prefs.getString("save_directory_uri", null);
        if (savedUri != null) {
            saveDirectoryUri = Uri.parse(savedUri);
            DocumentFile df = DocumentFile.fromTreeUri(this, saveDirectoryUri);
            if(df != null && tvCurrentDir != null) tvCurrentDir.setText("Folder: " + df.getName());
        }
    }

    private void saveToCsv() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = "salat_data_" + timestamp + ".csv";

            DocumentFile directory = DocumentFile.fromTreeUri(this, saveDirectoryUri);
            if (directory != null && directory.exists()) {
                DocumentFile newFile = directory.createFile("text/csv", filename);

                if (newFile != null) {
                    try (OutputStream outputStream = getContentResolver().openOutputStream(newFile.getUri())) {
                        if (outputStream != null) {
                            for (String line : dataBuffer) {
                                outputStream.write((line + "\n").getBytes());
                            }
                            outputStream.flush();
                            Toast.makeText(this, "Saved: " + filename, Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error Saving: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}