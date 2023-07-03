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
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;

public class StopWatch extends ShowTime implements SensorEventListener {
    private MyChrono chrono;
    private double START_ANGLE;
    private double STOP_ANGLE;
    private long RESTART_TIME = 2000;
    private long lastStopped = -100000000;
//    private double[] gravityAdjust = {0,0,0};
    private double[] vertical = {0,0,1};
    private static final double g = 9.81;
    private double[] lastGravity = {0,0,g};

    protected static final int[] TEXT_BUTTONS = {
    };
    protected static final int[][] IMAGE_BUTTONS = {
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
        gravity = sensorManager.getDefaultSensor(Build.VERSION.SDK_INT >= 9 ? Sensor.TYPE_GRAVITY : Sensor.TYPE_ACCELEROMETER);
        vertical[0] = options.getFloat("VERTICAL_X", 0);
        vertical[1] = options.getFloat("VERTICAL_Y", 0);
        vertical[2] = options.getFloat("VERTICAL_Z", 1);
    }

    @Override
    protected void onPause() {
        super.onPause();
        chrono.stop();
        sensorManager.unregisterListener(this, gravity);
    }

    @Override
    protected void onResume() {
        super.onResume();

        START_ANGLE = Options.getStartAngle(options);
        STOP_ANGLE = Options.getStopAngle(options);
        RESTART_TIME = Long.parseLong(options.getString(Options.PREF_RESTART_TIME, "2000"));

        sensorManager.registerListener(this, gravity, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    protected void setTheme() {
        super.setTheme();
        int fore = Options.getForeColor(this, options);
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
                double norm = Math.sqrt(lastGravity[0]*lastGravity[0]+lastGravity[1]*lastGravity[1]+lastGravity[2]*lastGravity[2]);
                vertical[0] = lastGravity[0] / norm;
                vertical[1] = lastGravity[1] / norm;
                vertical[2] = lastGravity[2] / norm;
                SharedPreferences.Editor ed = options.edit();
                ed.putFloat("VERTICAL_X", (float)vertical[0]);
                ed.putFloat("VERTICAL_Y", (float)vertical[1]);
                ed.putFloat("VERTICAL_Z", (float)vertical[2]);
                ed.commit();
            }
        });
        b.setNeutralButton("Set defaults", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                vertical[0] = 0;
                vertical[1] = 0;
                vertical[2] = 1;
                SharedPreferences.Editor ed = options.edit();
                ed.putFloat("VERTICAL_X", (float)vertical[0]);
                ed.putFloat("VERTICAL_Y", (float)vertical[1]);
                ed.putFloat("VERTICAL_Z", (float)vertical[2]);
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
        double x = event.values[0];
        double y = event.values[1];
        double z = event.values[2];
        double cosAngle = (x * vertical[0] + y * vertical[1] + z * vertical[2])/Math.sqrt(x*x+y*y+z*z);
        double angle = safeacos(cosAngle) * 180 / Math.PI;

        if (angle < START_ANGLE && (! chrono.active || chrono.paused) && java.lang.System.currentTimeMillis() >= lastStopped + RESTART_TIME) {
            chrono.resetAndStart();
        }
        if (angle >= STOP_ANGLE && (chrono.active && ! chrono.paused)) {
            chrono.stop();
            chrono.readTime();
            lastStopped = java.lang.System.currentTimeMillis();
        }
        chrono.setAngle(angle);
        lastGravity[0] = x;
        lastGravity[1] = y;
        lastGravity[2] = z;
    }

    public static double safeacos(double x) {
        if (x<-1)
            return -Math.PI;
        else if (x>1)
            return 0;
        else
            return Math.acos(x);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}
