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
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.materialswitch.MaterialSwitch;
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
    private MaterialSwitch swBlindMode; // The new Switch

    // --- LOGIC ---
    private ArrayList<String> salatSequence = new ArrayList<>();
    private int currentStepIndex = 0;
    private boolean isBlindMode = false; // State to track mode

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Enable edge-to-edge display
        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 1. Initialize UI
        btnAction = findViewById(R.id.btnAction);
        btnSelectDir = findViewById(R.id.btnSelectDir);
        tvCurrentStep = findViewById(R.id.tvCurrentStep);
        tvNextStep = findViewById(R.id.tvNextStep);
        tvCurrentDir = findViewById(R.id.tvCurrentDir);
        rgRakats = findViewById(R.id.rgRakats);
        swBlindMode = findViewById(R.id.swBlindMode); // Bind Switch

        // 2. Initialize Sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }

        setupStorageLogic();

        // 3. Listeners
        btnSelectDir.setOnClickListener(v -> directoryPicker.launch(null));

        btnAction.setOnClickListener(v -> {
            if (!isRecording)
                startRecording();
            else
                stopRecording();
        });

        // Toggle UI logic based on Switch
        swBlindMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isBlindMode = isChecked;
            if (isChecked) {
                // Hide/Disable Labeled Mode UI
                rgRakats.setAlpha(0.5f);
                for (int i = 0; i < rgRakats.getChildCount(); i++)
                    rgRakats.getChildAt(i).setEnabled(false);
                tvCurrentStep.setText("Blind Mode");
                tvNextStep.setText("No Sequence");
            } else {
                // Enable Labeled Mode UI
                rgRakats.setAlpha(1.0f);
                for (int i = 0; i < rgRakats.getChildCount(); i++)
                    rgRakats.getChildAt(i).setEnabled(true);
                tvCurrentStep.setText("Ready");
                tvNextStep.setText("Select Rakats");
            }
        });
    }

    // =========================================================
    // VOLUME BUTTON (IGNORED IN BLIND MODE)
    // =========================================================
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Only use Volume Buttons if Recording AND NOT in Blind Mode
        if (isRecording && !isBlindMode) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                if (currentStepIndex < salatSequence.size() - 1) {
                    currentStepIndex++;
                    updateStepDisplay();
                } else {
                    Toast.makeText(this, "Sequence Finished", Toast.LENGTH_SHORT).show();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                if (currentStepIndex > 0) {
                    currentStepIndex--;
                    updateStepDisplay();
                }
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    private void generateSalatSequence() {
        salatSequence.clear();
        int rakats = 2;
        int selectedId = rgRakats.getCheckedRadioButtonId();
        if (selectedId == R.id.rb3)
            rakats = 3;
        else if (selectedId == R.id.rb4)
            rakats = 4;

        salatSequence.add("Takbeer/Standing");
        for (int i = 1; i <= rakats; i++) {
            salatSequence.add("Standing (Rakat " + i + ")");
            salatSequence.add("Ruku (Rakat " + i + ")");
            salatSequence.add("Standing (Post-Ruku)");
            salatSequence.add("Sujud 1 (Rakat " + i + ")");
            salatSequence.add("Sitting Between Sujud");
            salatSequence.add("Sujud 2 (Rakat " + i + ")");
            if (i == 2 || i == rakats)
                salatSequence.add("Tashahhud (Sitting)");
            else
                salatSequence.add("Standing Up");
        }
        salatSequence.add("Salam");
    }

    private void updateStepDisplay() {
        if (salatSequence.isEmpty())
            return;
        tvCurrentStep.setText(salatSequence.get(currentStepIndex));
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

        isRecording = true;
        dataBuffer.clear();
        dataBuffer.add("Timestamp,Label,SensorType,X,Y,Z");

        // --- CHECK MODE ---
        int samplingRate;
        if (isBlindMode) {
            samplingRate = SensorManager.SENSOR_DELAY_NORMAL;
            tvCurrentStep.setText("Recording (Blind)...");
            tvNextStep.setText("");
        } else {
            // Training Mode: High Speed (Game = 20ms / 50Hz)
            generateSalatSequence();
            currentStepIndex = 0;
            updateStepDisplay();
            samplingRate = SensorManager.SENSOR_DELAY_GAME;

            // Lock UI
            swBlindMode.setEnabled(false);
            for (int i = 0; i < rgRakats.getChildCount(); i++)
                rgRakats.getChildAt(i).setEnabled(false);
        }

        if (accelerometer != null)
            sensorManager.registerListener(this, accelerometer, samplingRate);
        if (gyroscope != null)
            sensorManager.registerListener(this, gyroscope, samplingRate);

        btnAction.setText("Stop & Save");
    }

    private void stopRecording() {
        isRecording = false;
        sensorManager.unregisterListener(this);
        saveToCsv();

        // Reset UI
        btnAction.setText("Start Recording");
        swBlindMode.setEnabled(true); // Re-enable switch

        if (!isBlindMode) {
            tvCurrentStep.setText("Ready");
            for (int i = 0; i < rgRakats.getChildCount(); i++)
                rgRakats.getChildAt(i).setEnabled(true);
        } else {
            tvCurrentStep.setText("Blind Mode Ready");
        }
    }

    // =========================================================
    // SENSOR EVENT
    // =========================================================
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (isRecording) {
            String type = (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) ? "ACCEL" : "GYRO";
            String label;

            if (isBlindMode) {
                // In blind mode, we don't know the posture. Use a generic label.
                label = "Blind_Data";
            } else {
                // In training mode, get the specific step
                if (!salatSequence.isEmpty()) {
                    label = salatSequence.get(currentStepIndex);
                } else {
                    label = "Unknown";
                }
            }

            String record = event.timestamp + "," + label + "," + type + "," +
                    event.values[0] + "," + event.values[1] + "," + event.values[2];
            dataBuffer.add(record);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // =========================================================
    // FILE STORAGE
    // =========================================================
    private void setupStorageLogic() {
        prefs = getSharedPreferences("SenseTrackPrefs", MODE_PRIVATE);
        directoryPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> {
                    if (uri != null) {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        saveDirectoryUri = uri;
                        prefs.edit().putString("save_directory_uri", uri.toString()).apply();
                        DocumentFile df = DocumentFile.fromTreeUri(this, uri);
                        if (df != null && tvCurrentDir != null)
                            tvCurrentDir.setText("Folder: " + df.getName());
                    }
                });
        String savedUri = prefs.getString("save_directory_uri", null);
        if (savedUri != null) {
            saveDirectoryUri = Uri.parse(savedUri);
            DocumentFile df = DocumentFile.fromTreeUri(this, saveDirectoryUri);
            if (df != null && tvCurrentDir != null)
                tvCurrentDir.setText("Folder: " + df.getName());
        }
    }

    private void saveToCsv() {
        try {
            // Append mode to filename so you know which is which
            String modePrefix = isBlindMode ? "BLIND_" : "LABELED_";
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            String filename = modePrefix + "salat_" + timestamp + ".csv";

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