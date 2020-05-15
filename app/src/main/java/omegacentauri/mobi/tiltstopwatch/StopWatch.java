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
    private static final double START_ANGLE = 8;
    private static final double STOP_ANGLE = 15;
    private static final long RESTART_TIME = 2000;
    private long lastStopped = -RESTART_TIME;
    private double[] gravityAdjust = {0,0,0};
    private boolean calibrateNow = false;

    protected static final int TEXT_BUTTONS[] = {
            R.id.calibrate
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

        String last = options.getString(Options.PREF_LAST_ACTIVITY, StopWatch.class.getName());
        if (! last.equals(StopWatch.class.getName())) {
            try {
                switchActivity(Class.forName(last), NONE);
            } catch (ClassNotFoundException e) {
                debug("class not found "+last);
            }
        }

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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (calibrateNow) {
            calibrateNow = false;
            gravityAdjust[0] = -event.values[0];
            gravityAdjust[1] = -event.values[1];
            gravityAdjust[2] = 9.81-event.values[2];
            SharedPreferences.Editor ed = options.edit();
            ed.putFloat("CALIBRATE_X", (float)gravityAdjust[0]);
            ed.putFloat("CALIBRATE_Y", (float)gravityAdjust[1]);
            ed.putFloat("CALIBRATE_Z", (float)gravityAdjust[2]);
            ed.commit();
        }
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
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void onButtonCalibrate(View view) {
        calibrateNow = true;
    }
}
