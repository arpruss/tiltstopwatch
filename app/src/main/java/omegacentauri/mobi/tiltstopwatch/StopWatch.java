package omegacentauri.mobi.tiltstopwatch;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.TabStopSpan;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class StopWatch extends ShowTime implements SensorEventListener {
    private MyChrono chrono;
    private double START_ANGLE;
    private double STOP_ANGLE;
    private static final long RESTART_TIME = 2000;
    private long lastStopped = -RESTART_TIME;
    private double[] gravityAdjust = {0,0,0};
    private static final double g = 9.81;
    private double[] lastGravity = {0,0,g};
    private boolean calibrateNow = false;

    protected static final int TEXT_BUTTONS[] = {
    };
    protected static final int IMAGE_BUTTONS[][] = {
            {R.id.settings, R.drawable.settings},
            {R.id.menu, R.drawable.menu}
    };
    private SensorManager sensorManager;
    private Sensor gravity;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        colorThemeOptionName = Options.PREF_STOPWATCH_COLOR;

        setContentView(R.layout.activity_stop_watch);
        bigDigits = (BigTextView)findViewById(R.id.chrono);
        controlBar = (LinearLayout)findViewById(R.id.controlBar);
        mainContainer = findViewById(R.id.chrono_and_laps);
        textButtons = TEXT_BUTTONS;
        imageButtons = IMAGE_BUTTONS;

        chrono = new MyChrono(this, options, bigDigits, (TextView)findViewById(R.id.angle),
                mainContainer);
        timeKeeper = chrono;

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        gravityAdjust[0] = options.getFloat("CALIBRATE_X", 0);
        gravityAdjust[1] = options.getFloat("CALIBRATE_Y", 0);
        gravityAdjust[2] = options.getFloat("CALIBRATE_Z", 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this, gravity);
    }

    @Override
    protected void onResume() {
        super.onResume();

        START_ANGLE = Options.getStartAngle(options);
        STOP_ANGLE = Options.getStopAngle(options);

        sensorManager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void setTheme() {
        super.setTheme();
        int fore = Options.getForeColor(this, options);
    }

    void pressSecondButton() {
        chrono.secondButton();
    }

    void pressFirstButton() {
        chrono.firstButton();
    }

    public boolean isFirstButton(int keyCode) {
        return false;
    }

    public boolean isSecondButton(int keyCode) {
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.calibrate:
                calibrate();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void calibrate() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Calibrate");
        b.setMessage("Lay device flat on level surface with screen up and press Calibrate button.");
        b.setPositiveButton("Calibrate", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                gravityAdjust[0] = -lastGravity[0];
                gravityAdjust[1] = -lastGravity[1];
                gravityAdjust[2] = g-lastGravity[2];
                SharedPreferences.Editor ed = options.edit();
                ed.putFloat("CALIBRATE_X", (float)gravityAdjust[0]);
                ed.putFloat("CALIBRATE_Y", (float)gravityAdjust[1]);
                ed.putFloat("CALIBRATE_Z", (float)gravityAdjust[2]);
                ed.commit();
            }
        });
        b.create().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        double x = event.values[0] + gravityAdjust[0];
        double y = event.values[1] + gravityAdjust[1];
        double z = event.values[2] + gravityAdjust[2];
        double xy = Math.sqrt(x*x+y*y);
        double angle = Math.atan2(xy, z) * 180 / Math.PI;

        if (angle < START_ANGLE && (! chrono.active || chrono.paused) && java.lang.System.currentTimeMillis() >= lastStopped + RESTART_TIME) {
            chrono.reset();
            pressFirstButton();
        }
        if (angle >= STOP_ANGLE && (chrono.active && ! chrono.paused)) {
            pressFirstButton();
            lastStopped = java.lang.System.currentTimeMillis();
        }
        chrono.setAngle(angle);
        lastGravity[0] = event.values[0];
        lastGravity[1] = event.values[1];
        lastGravity[2] = event.values[2];
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void onButtonCalibrate(View view) {
        calibrateNow = true;
    }
}
