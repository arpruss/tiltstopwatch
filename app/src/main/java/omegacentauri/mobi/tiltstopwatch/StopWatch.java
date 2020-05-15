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
    private Button secondButton;
    private Button firstButton;
    private TextView laps;
    private MyChrono chrono;
    private static final double START_ANGLE = 8*Math.PI/180;
    private static final double STOP_ANGLE = 15*Math.PI/180;
    private static final long RESTART_TIME = 1000;
    private long lastStopped = -RESTART_TIME;
    private double[] gravityAdjust = {0,0,0};
    private boolean calibrateNow = false;

    protected static final int TEXT_BUTTONS[] = {
            R.id.start, R.id.reset
    };
    protected static final int IMAGE_BUTTONS[][] = {
            {R.id.settings, R.drawable.settings},
            {R.id.menu, R.drawable.menu}
    };
    private boolean volumeControl;
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
        secondButton = (Button)findViewById(R.id.reset);
        firstButton = (Button)findViewById(R.id.start);
        controlBar = (LinearLayout)findViewById(R.id.controlBar);
        mainContainer = findViewById(R.id.chrono_and_laps);
        laps = (TextView)findViewById(R.id.laps);
        textButtons = TEXT_BUTTONS;
        imageButtons = IMAGE_BUTTONS;

        chrono = new MyChrono(this, options, bigDigits, (TextView)findViewById(R.id.fraction),
                laps, mainContainer);
        timeKeeper = chrono;

        laps.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                chrono.copyLapsToClipboard();
                return true;
            }
        });

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

        volumeControl = options.getBoolean(Options.PREF_VOLUME, true);
        updateButtons();
        if (options.getBoolean(Options.PREF_SWIPE, true) && !options.getBoolean(Options.PREF_STOPWATCH_SWIPE_INFO, false)) {
            SharedPreferences.Editor ed = options.edit();
            ed.putBoolean(Options.PREF_STOPWATCH_SWIPE_INFO, true);
            MyChrono.apply(ed);
            if (hasTouch())
                timedMessage("StopWatch Mode", "Swipe on time or press menu button to switch to clock mode", 5200);
            else
                timedMessage("StopWatch Mode", "Press up/down or use menu button to switch to clock mode", 5200);
//            Toast.makeText(this, "StopWatch mode: Swipe time to switch to clock", Toast.LENGTH_LONG).show();
        }
    }

    void updateButtons() {
        if (!chrono.active) {
            firstButton.setText("Start");
            secondButton.setText("Delay");
        }
        else {
            if (chrono.paused) {
                firstButton.setText("Continue");
                secondButton.setText("Reset");
                //secondButton.setVisibility(View.VISIBLE);
            } else {
                firstButton.setText("Stop");
                secondButton.setText("Lap");
            }
        }
    }

    @Override
    protected void setTheme() {
        super.setTheme();
        int fore = Options.getForeColor(this, options);

        laps.setTextColor(fore);
    }

    public void pace() {
        if (chrono.getTime() < 0 || !chrono.active) {
            Toast.makeText(StopWatch.this, "Stopwatch time not available", Toast.LENGTH_LONG).show();
            return;
        }

        lockOrientation();

        AlertDialog.Builder builder = AlertDialog_Builder();
        builder.setTitle("Pace/Speed Calculator");
        final long currentTime1000 = chrono.getTime();
        final String currentTimeString = chrono.formatTimeFull(currentTime1000);
        final double currentTime = currentTime1000/1000.;
        builder.setTitle("Pace Calculator");
        View content = getLayoutInflater().inflate(R.layout.pace, null);
        builder.setView(content);
        final EditText input = (EditText)content.findViewById(R.id.distance);
        final TextView message = (TextView)content.findViewById(R.id.message);
        final String defaultMessage = "Time: " + currentTimeString;
        final Button copyPace = (Button)content.findViewById(R.id.copy_pace);
        final Button copySpeed = (Button)content.findViewById(R.id.copy_speed);
        message.setText(defaultMessage);
        copyPace.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                try {
                    double distance = Double.parseDouble(input.getEditableText().toString());
                    clip(StopWatch.this, chrono.formatTimeFull((long)(currentTime1000 / distance)));
                }
                catch(Exception e) {
                    Toast.makeText(StopWatch.this, "Units not validly set", Toast.LENGTH_LONG).show();
                }
            }
        });
        copySpeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    double distance = Double.parseDouble(input.getEditableText().toString());
                    clip(StopWatch.this, String.format("%g", distance/(currentTime/(60*60))));
                }
                catch(Exception e) {
                    Toast.makeText(StopWatch.this, "Units not validly set", Toast.LENGTH_LONG).show();
                }
            }
        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                String msg;
                try {
                    double distance = Double.parseDouble(editable.toString());
                    msg = String.format("Time:\t%s\n" +
                                    "Pace:\t%s /unit\n" +
                                    "Speed:\t%g units/hour", currentTimeString,
                            chrono.formatTimeFull((long)(currentTime1000/distance)),
                            distance/(currentTime/(60*60)));
                }
                catch(Exception e) {
                    msg = defaultMessage;
                }
                SpannableStringBuilder span = new SpannableStringBuilder(msg);
                int w1 = (int) message.getPaint().measureText("Speed: ");
                //int w1 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 80, StopWatch.this.getResources().getDisplayMetrics());
                span.setSpan(new TabStopSpan.Standard(w1), 0, span.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE );
                message.setText(span);
            }
        });

        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialogInterface) {
                setOrientation();
            }
        });

        input.requestFocus();
        AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dialog.show();
    }

    void pressSecondButton() {
        if (Build.VERSION.SDK_INT >= 5)
            secondButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        chrono.secondButton();
        updateButtons();
    }

    void pressFirstButton() {
        if (Build.VERSION.SDK_INT >= 5)
            firstButton.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        chrono.firstButton();
        updateButtons();
    }

    public void onButtonStart(View v) {
        pressFirstButton();
    }

    public void onButtonReset(View v) {
        pressSecondButton();
    }

    public boolean isFirstButton(int keyCode) {
        return false;
    }

    public boolean isSecondButton(int keyCode) {
        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.copy_laps).setVisible(chrono.lapData.length()>0);
        menu.findItem(R.id.clear_laps).setVisible(chrono.lapData.length()>0);
        menu.findItem(R.id.pace).setVisible(chrono.getTime()>0);
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        debug("options menu "+item.getItemId());
        switch (item.getItemId()) {
            case R.id.copy_time:
                chrono.copyToClipboard();
                return true;
            case R.id.copy_laps:
                chrono.copyLapsToClipboard();
                return true;
            case R.id.clear_laps:
                chrono.clearLapData();
                return true;
            case R.id.pace:
                pace();
                return true;
        }
        return super.onOptionsItemSelected(item);
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
        double angle = Math.atan2(xy, z);
        Log.v("StopWatch", "gravity " + x+","+y + "," + "," + z + " " + angle * 180 / Math.PI);
        Log.v("StopWatch", "gravity " + x+","+y + "," + "," + z + " " + angle * 180 / Math.PI);
        if (angle < START_ANGLE && (! chrono.active || chrono.paused) && java.lang.System.currentTimeMillis() >= lastStopped + RESTART_TIME) {
            chrono.reset();
            pressFirstButton();
        }
        if (angle >= STOP_ANGLE && (chrono.active && ! chrono.paused)) {
            pressFirstButton();
            lastStopped = java.lang.System.currentTimeMillis();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void onButtonCalibrate(View view) {
        calibrateNow = true;
    }
}
