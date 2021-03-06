package omegacentauri.mobi.tiltstopwatch;
//
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.text.style.TabStopSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class MyChrono implements BigTextView.GetCenter, MyTimeKeeper {
    private final Activity context;
    private final View mainContainer;
    private final BigTextView mainView;
    TextView angleView;
    public long baseTime;
    public long pauseTime;
    public boolean paused = false;
    public boolean active = false;
    boolean quiet = false;
    Timer timer;
    int maxSize;
    Handler updateHandler;
    SharedPreferences options;
    public int precision = 100;
    private TextToSpeech tts = null;
    private boolean ttsMode;
    static final int STREAM = AudioManager.STREAM_MUSIC;;
    private static final int GAIN = 2000;
    HashMap<String,String> ttsParams = new HashMap<String,String>();
    private static final long SHORT_TONE_LENGTH = 75;
    private static final long LONG_TONE_LENGTH = 600;
    private static final float TONE_FREQUENCY = 2000;
    private long ANNOUNCEMENT_TIME = 10000;
    private static final long PREANNOUNCE = 50;
    private double lastAngle = 0;
    private AudioTrack shortTone;
    private AudioTrack longTone;
    private long lastAnnouncement = -10000000;

    @SuppressLint("NewApi")
    public MyChrono(Activity context, SharedPreferences options, BigTextView mainView, TextView fractionView, View mainContainer) {
        this.mainView = mainView;
        this.context = context;
        this.options = options;
        this.mainContainer = mainContainer;
        this.angleView = fractionView;
        this.mainView.getCenterY = this;
        tts = null;

        StopWatch.debug("maxSize " +this.maxSize);

        updateHandler = new Handler() {
            public void handleMessage(Message m) {
                updateViews();
            }
        };
    }

    public long getTime() {
        return active ? (( paused ? pauseTime : SystemClock.elapsedRealtime() ) - baseTime) : 0;
    }

    private void announce(long t) {
        if (!active || paused || quiet || t + PREANNOUNCE < (lastAnnouncement+ANNOUNCEMENT_TIME)/ANNOUNCEMENT_TIME*ANNOUNCEMENT_TIME)
            return;

        lastAnnouncement = t;

        t = t+PREANNOUNCE;

        if (tts != null && ttsMode && ANNOUNCEMENT_TIME <= t) {
            String msg = "" + t/1000;
            tts.speak(msg,TextToSpeech.QUEUE_FLUSH, ttsParams);
        }
        else if (!quiet) {
            shortTone.stop();
            shortTone.reloadStaticData();
            shortTone.play();
        }
    }

    public void updateViews() {
        long t = getTime();
        announce(t);
        mainView.setText(formatTime(t,mainView.getHeight() > mainView.getWidth(), precision));
    }

    private void setAngleView(String s) {
        angleView.setText(s);
        angleView.setTextScaleX(1f);
        float w = angleView.getPaint().measureText(s, 0, s.length());
        float wCur = angleView.getWidth() * 0.98f - 2;
        if (wCur <= 0) {
            angleView.setText("");
            return;
        }
        if (w > wCur)
            angleView.setTextScaleX(wCur/w);
        angleView.setText(s);
    }

    static final long floorDiv(long a, long b) {
        long q = a/b;
        if (q*b > a)
            return q-1;
        else
            return q;
    }

    String formatTime(long t, boolean multiline, int prec) {
        //t+=1000*60*60*60;
        if (t<0)
            return String.format("\u2212%s", formatTime(-t, multiline,prec));

        String fraction;
        if (prec == 10)
            fraction = String.format(".%02d", (int)((t / 10) % 100));
        else if (prec == 100)
            fraction = String.format(".%01d", (int)((t / 100) % 10));
        else if (prec == 1)
            fraction = String.format(".%03d", (int)(t % 1000));
        else
            fraction = "";

        t /= 1000;

        int seconds = (int)(t % 60);

        t /= 60;

        int minutes = (int)(t % 6);

        t /= 60;

        int hours = (int)t;

        if (multiline) {
            if (hours > 0)
                return String.format("%d\n%02d\n%02d%s", hours, minutes, seconds, fraction);
            else
                return String.format("%02d\n%02d%s", minutes, seconds, fraction);
        }
        else {
            if (hours > 0)
                return String.format("%d:%02d:%02d%s", hours, minutes, seconds, fraction);
            else
                return String.format("%d:%02d%s", minutes, seconds, fraction);
        }
    }

    private short[] sinewave(float frequency, long duration) {
        int numSamples = (int)(44.100 * duration);
        double alpha = frequency / 44100 * 2 * Math.PI;
        short[] samples = new short[numSamples];
        for (int i = 0 ; i < numSamples ; i++)
            samples[i] = (short) (32767. * Math.sin(alpha * i));
        return samples;
    }

    public void resetAndStart() {
        active = false;
        lastAnnouncement = -10000000;
        baseTime = SystemClock.elapsedRealtime();
        paused = false;
        active = true;
        startUpdating();
        save();
        updateViews();
    }

    public void stop() {
        if (!paused) {
            paused = true;
            pauseTime = SystemClock.elapsedRealtime();
        }
        stopUpdating();
        save();
        updateViews();
    }


    public void setAudio(String soundMode) {
        if (soundMode.equals("none")) {
            quiet = true;
            ttsMode = false;
            return;
        }

        quiet = false;

        short[] tone = sinewave(TONE_FREQUENCY, LONG_TONE_LENGTH);
        longTone = new AudioTrack(STREAM, 44100, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT, tone.length * 2, AudioTrack.MODE_STATIC);
        longTone.write(tone, 0, tone.length);
        int sessionId = 0;
        int shortLength = Math.min(tone.length, (int) (44.100 * SHORT_TONE_LENGTH));
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD) {
            sessionId = longTone.getAudioSessionId();
            shortTone = new AudioTrack(STREAM, 44100, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, shortLength * 2, AudioTrack.MODE_STATIC, sessionId);
        }
        else {
            shortTone = new AudioTrack(STREAM, 44100, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, shortLength * 2, AudioTrack.MODE_STATIC);
        }
        shortTone.write(tone, 0, shortLength);
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (options.getBoolean(Options.PREF_BOOST, false))
            am.setStreamVolume(STREAM, am.getStreamMaxVolume(STREAM), 0);

        if (soundMode.equals("voice")) {
            ttsMode = true;
            ttsParams.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(STREAM));
            tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if(status != TextToSpeech.SUCCESS){
                        tts = null;
                    }
                    else {
                        tts.speak(" ",TextToSpeech.QUEUE_FLUSH, ttsParams);
                    }
                }
            });
        }
        else if (soundMode.equals("beeps")) {
            ttsMode = false;
        }
    }

    public void restore() {
        baseTime = options.getLong(Options.PREF_START_TIME, 0);
        pauseTime = options.getLong(Options.PREF_PAUSED_TIME, 0);
        ANNOUNCEMENT_TIME = 1000*Long.parseLong(options.getString(Options.PREF_ANNOUNCEMENT_SPACING, "10"));
        active = options.getBoolean(Options.PREF_ACTIVE, false);
        paused = options.getBoolean(Options.PREF_PAUSED, false);
        lastAnnouncement = options.getLong(Options.PREF_LAST_ANNOUNCED, -10000000);
        setAudio(options.getString(Options.PREF_SOUND, "voice"));

        precision = Integer.parseInt(options.getString(Options.PREF_PRECISION, "100"));
        if (SystemClock.elapsedRealtime() < baseTime)
            active = false;

        if (active && !paused) {
            startUpdating();
        }
        else {
            stopUpdating();
        }
        updateViews();
    }

    public static void apply(SharedPreferences.Editor ed) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
            ed.apply();
        }
        else {
            ed.commit();
        }
    }

    public static long getBootTime() {
        return java.lang.System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
    }

    public void save() {
        StopWatch.debug("saving "+pauseTime);
        SharedPreferences.Editor ed = options.edit();
        ed.putLong(Options.PREF_START_TIME, baseTime);
        ed.putLong(Options.PREF_PAUSED_TIME, pauseTime);
        ed.putBoolean(Options.PREF_ACTIVE, active);
        ed.putBoolean(Options.PREF_PAUSED, paused);
        ed.putLong(Options.PREF_LAST_ANNOUNCED, lastAnnouncement);
        ed.putLong(Options.PREF_BOOT_TIME, getBootTime());

        apply(ed);
    }

    public void stopUpdating() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        ((Activity)context).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void startUpdating() {
//        if (delayTime < 0 && lastAnnounced < 0 && !quiet) {
//        }
        if (timer == null) {
            timer = new Timer();
            timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    updateHandler.obtainMessage(1).sendToTarget();
                }
            }, 0, precision<=10 ? precision : 50); // avoid off-by-1 errors at lower precisions, at cost of some battery life
        }
//        if (options.getBoolean(Options.PREF_SCREEN_ON, true))
        ((Activity)context).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        else
//            ((Activity)context).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public static void clearSaved(SharedPreferences pref) {
        SharedPreferences.Editor ed = pref.edit();
        ed.putBoolean(Options.PREF_ACTIVE, false);
        apply(ed);
        StopWatch.debug("cleared "+Options.PREF_ACTIVE);
    }

    public static void detectBoot(SharedPreferences options) {
        return;
    }

    public void suspend() {
        destroyAudio();
    }

    public void destroy() {
        destroyAudio();
    }

    public void destroyAudio() {
        if (shortTone != null) {
            shortTone.release();
            shortTone = null;
        }
        if (longTone != null) {
            longTone.release();
            longTone = null;
        }
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }

    }

    public static void fixOnBoot(SharedPreferences options) {
        if (! options.getBoolean(Options.PREF_ACTIVE, false)) {
            StopWatch.debug("not active");
            return;
        }
        long oldBootTime = options.getLong(Options.PREF_BOOT_TIME, 0);
        SharedPreferences.Editor ed = options.edit();
        if (oldBootTime == 0) {
            ed.putBoolean(Options.PREF_ACTIVE, false);
        }
        else {
            long delta = getBootTime() - oldBootTime;
            if (delta == 0)
                return;
            adjust(options, ed, Options.PREF_START_TIME, -delta);
            adjust(options, ed, Options.PREF_PAUSED_TIME, -delta);
            ed.putBoolean(Options.PREF_BOOT_ADJUSTED, true);
        }
        apply(ed);
    }

    private static void adjust(SharedPreferences options, SharedPreferences.Editor ed, String opt, long delta) {
        StopWatch.debug("opt "+opt+" "+delta);
        ed.putLong(opt, options.getLong(opt, 0) + delta);
    }

    @Override
    public float getCenter() {
        return mainContainer.getHeight() / 2f;
    }

    public void setAngle(double angle) {
        this.lastAngle = angle;
        String f = String.format("%.1f\u00B0",lastAngle);
        if (!f.equals(angleView.getText()))
            setAngleView(f);
    }

    public void readTime() {
        if (tts != null && ttsMode) {
            long t = getTime() / 1000;
            int seconds = (int)(t % 60);
            int minutes = (int)(t / 60);
            String msg;
            if (minutes != 0)
                msg = String.format("%d minutes %d seconds", minutes, seconds);
            else
                msg = String.format("%d seconds", seconds);

            tts.speak(msg, TextToSpeech.QUEUE_FLUSH, ttsParams);
        }
        else if (!quiet) {
            shortTone.stop();
            shortTone.reloadStaticData();
            shortTone.play();
        }
    }
}
