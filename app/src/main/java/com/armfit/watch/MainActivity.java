package com.armfit.watch;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(9, 13, 12), PANEL = Color.rgb(25, 34, 29),
        GREEN = Color.rgb(197, 247, 107), WHITE = Color.rgb(243, 247, 240), MUTED = Color.rgb(163, 177, 164);
    private static final String HOME = "home", DETAIL = "detail", TRAIN = "train", REST = "rest",
        SUMMARY = "summary", EXIT = "exit", DAY_OFF = "day_off", PICK = "pick", CONFIG = "config",
        OMIT = "omit";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String page = HOME;
    private int selected;
    private Session session;
    private SessionStore store;
    private LinearLayout column;
    private RestRing ring;
    private TextView elapsedLabel, exerciseLabel;
    private ToneGenerator tone;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long now = SystemClock.elapsedRealtime();
            if (session != null && session.active) {
                if (session.tick(now)) { store.save(session); announceRest(); render(); }
                if (ring != null) ring.update(session.remaining(now), session.phase[session.exercise] == Session.PAUSED);
                if (elapsedLabel != null) elapsedLabel.setText(getString(R.string.elapsed_time, time(session.elapsed(now))));
                if (exerciseLabel != null && session.exercise >= 0)
                    exerciseLabel.setText(getString(R.string.exercise_time, time(session.exerciseTime(session.exercise, now))));
            }
            handler.postDelayed(this, 200);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store = new SessionStore(this);
        session = store.load(SystemClock.elapsedRealtime());
        if (session != null) page = session.active ? activePage() : SUMMARY;
        else if (state != null) {
            selected = state.getInt("selected", 0);
            page = state.getString("page", HOME);
            if (!page.equals(DETAIL) && !page.equals(DAY_OFF) && !page.equals(CONFIG)) page = HOME;
        }
        render();
    }

    @Override protected void onResume() { super.onResume(); handler.removeCallbacks(ticker); handler.post(ticker); }
    @Override protected void onPause() { store.save(session); handler.removeCallbacks(ticker); super.onPause(); }
    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (tone != null) { tone.release(); tone = null; }
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("page", page); state.putInt("selected", selected);
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if (page.equals(CONFIG)) { page = HOME; render(); }
        else if (page.equals(EXIT) || page.equals(OMIT)) resumeSession();
        else if (session != null && session.active) { page = EXIT; render(); }
        else if (page.equals(SUMMARY)) clearSession();
        else if (!page.equals(HOME)) { page = HOME; render(); }
        else super.onBackPressed();
    }

    private String activePage() {
        if (session.exercise < 0) return PICK;
        return session.phase[session.exercise] == Session.TRAINING ? TRAIN : REST;
    }

    private void render() {
        ring = null; elapsedLabel = null; exerciseLabel = null;
        if (session != null && session.active && session.exercise >= 0
            && session.phase[session.exercise] == Session.RESTING)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(true);
        column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        int side = getResources().getConfiguration().isScreenRound() ? 28 : 18;
        column.setPadding(dp(side), dp(page.equals(HOME) ? 18 : 34), dp(side), dp(38));
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        scroll.setFocusableInTouchMode(true);
        scroll.setOnGenericMotionListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
                scroll.smoothScrollBy(0, (int) (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(42))); return true;
            }
            return false;
        });
        setContentView(scroll); scroll.requestFocus();
        if (session != null && session.active && session.exercise >= 0
            && session.phase[session.exercise] == Session.WAITING && !page.equals(REST))
            button("Descanso terminado · Ver", true, this::resumeSession);
        switch (page) {
            case DETAIL: showDetail(); break;
            case TRAIN: showTraining(); break;
            case REST: showRest(); break;
            case PICK: showPicker(); break;
            case CONFIG: showConfig(); break;
            case SUMMARY: showSummary(); break;
            case EXIT: showExit(); break;
            case OMIT:
                title("¿Omitir por hoy?"); paragraph("Se conservarán tus series y tiempo, pero quedará marcado como omitido.");
                button("Volver", true, this::resumeSession);
                button("Sí, omitir", false, () -> { session.omitExercise(now()); page = PICK; persistAndRender(); });
                break;
            case DAY_OFF:
                eyebrow("DÍA 4"); title("Descanso"); paragraph("Hoy toca recuperar. Tu siguiente rutina es Upper.");
                button("Ver Upper", true, () -> { selected = 3; page = DETAIL; render(); });
                button("Volver", false, () -> { page = HOME; render(); }); break;
            default: showHome();
        }
    }

    private long now() { return SystemClock.elapsedRealtime(); }

    private void showHome() {
        FrameLayout header = new FrameLayout(this);
        TextView brand = new TextView(this);
        brand.setText(R.string.app_name); brand.setTextColor(WHITE); brand.setTextSize(26);
        brand.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        FrameLayout.LayoutParams brandParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        brandParams.setMarginStart(dp(8));
        brandParams.bottomMargin = dp(8);
        header.addView(brand, brandParams);
        ImageButton settings = new ImageButton(this);
        settings.setImageResource(R.drawable.ic_settings);
        settings.setContentDescription(getString(R.string.settings));
        // Icono discreto de 18 dp dentro de una zona táctil de 48 dp.
        settings.setPadding(dp(15), dp(15), dp(15), dp(15));
        settings.setBackground(new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x33777777), null, shape(Color.WHITE, 24)));
        header.addView(settings, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.END));
        settings.setOnClickListener(v -> { page = CONFIG; render(); });
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, dp(64));
        headerParams.setMarginStart(-dp(8));
        headerParams.setMarginEnd(-dp(8));
        column.addView(header, headerParams);
        eyebrow("TU RITMO. TU RUTINA."); paragraph("Elige tu entrenamiento");
        for (int i = 0; i < Routines.NAMES.length; i++) {
            if (i == 3) button("Día 4 · Descanso", false, () -> { page = DAY_OFF; render(); });
            final int index = i;
            button("Día " + Routines.DAYS[i] + " · " + Routines.NAMES[i] + "\n"
                + Routines.EXERCISES[i].length + " ejercicios", i == 0,
                () -> { selected = index; page = DETAIL; render(); });
        }
    }

    private void showDetail() {
        eyebrow("DÍA " + Routines.DAYS[selected]); title(Routines.NAMES[selected]);
        paragraph(goal(store.target()) + " · Descanso " + time(store.rest()));
        caption("Al comenzar podrás elegir cualquier ejercicio.");
        button("Comenzar", true, () -> { session = store.create(selected, now()); page = PICK; persistAndRender(); });
        for (int i = 0; i < Routines.EXERCISES[selected].length; i++) {
            paragraph((i + 1) + ". " + Routines.EXERCISES[selected][i]);
        }
        button("Volver", false, () -> { page = HOME; render(); });
    }

    private String goal(int target) { return target == 0 ? "Series libres" : target + " series previstas"; }

    private void showConfig() {
        title("Configuración");
        int target = store.target();
        long rest = store.rest();
        label("Series · " + target, 20, GREEN, true);
        button("+ 1 serie", false, () -> configure(target + 1, rest));
        button("− 1 serie", false, () -> configure(target - 1, rest));
        caption("Es tu meta por ejercicio. Puedes hacer más o terminar antes.");
        label("Descanso · " + time(rest), 20, GREEN, true);
        button("+ 30 segundos", false, () -> configure(target, rest + 30000));
        button("− 30 segundos", false, () -> configure(target, rest - 30000));
        caption("De 0:30 a 15:00. Los valores se aplican a todos los ejercicios de la próxima sesión.");
        button("Vibración: " + (store.vibration() ? "activada" : "desactivada"), false,
            () -> { store.vibration(!store.vibration()); render(); });
        button("Sonido: " + (store.sound() ? "activado" : "desactivado"), false,
            () -> { store.sound(!store.sound()); render(); });
        caption("El sonido depende del volumen y del modo silencio.");
        button("Listo", true, () -> { page = HOME; render(); });
    }

    private void configure(int target, long rest) {
        store.configure(target, rest);
        render();
    }

    private void showTraining() {
        int i = session.exercise;
        eyebrow(Routines.NAMES[session.routine].toUpperCase(Locale.ROOT));
        title(Routines.EXERCISES[session.routine][i]);
        label("Serie " + (session.sets[i] + 1), 25, GREEN, true);
        caption(session.sets[i] + " registradas · " + goal(session.targets[i]));
        if (session.targets[i] > 0 && session.sets[i] >= session.targets[i]) caption("Meta alcanzada. Puedes terminar o añadir otra serie.");
        button("Terminé la serie", true, () -> { session.finishSet(now()); page = REST; persistAndRender(); });
        if (session.sets[i] > 0) button("Terminar ejercicio", false, this::completeExercise);
        exerciseLabel = caption(getString(R.string.exercise_time, time(session.exerciseTime(i, now()))));
        elapsedLabel = caption(getString(R.string.elapsed_time, time(session.elapsed(now()))));
        exerciseActions();
    }

    private void exerciseActions() {
        button("Hacer después / Cambiar", false, this::chooseExercise);
        button("Omitir por hoy", false, () -> { page = OMIT; render(); });
        button("Finalizar sesión", false, () -> { page = EXIT; render(); });
    }

    private void showRest() {
        int i = session.exercise;
        eyebrow("SERIE " + session.sets[i] + " REGISTRADA");
        if (session.phase[i] == Session.WAITING) {
            title("Descanso terminado"); paragraph("Continúa cuando estés listo.");
            if (session.targets[i] > 0 && session.sets[i] >= session.targets[i]) {
                caption("Completaste las series previstas.");
                button("Terminar ejercicio", true, this::completeExercise);
                button("Hacer otra serie", false, this::confirmNext);
            } else {
                button("Iniciar siguiente serie", true, this::confirmNext);
                button("Terminar ejercicio", false, this::completeExercise);
            }
            button("Descansar 30 s más", false, () -> adjustRest(30000));
        } else {
            title("Descanso");
            ring = new RestRing();
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(112), dp(112));
            params.bottomMargin = dp(8); column.addView(ring, params);
            ring.update(session.remaining(now()), session.phase[i] == Session.PAUSED);
            button(session.phase[i] == Session.PAUSED ? "Continuar descanso" : "Pausar descanso", true, () -> {
                int before = session.phase[i]; session.toggleRest(now());
                if (before != Session.WAITING && session.phase[i] == Session.WAITING) announceRest();
                persistAndRender();
            });
            button("Saltar descanso", false, () -> { session.skipRest(); persistAndRender(); });
        }
        caption(Routines.EXERCISES[session.routine][i]);
        if (session.phase[i] == Session.WAITING) exerciseActions();
    }

    private void adjustRest(long delta) {
        int before = session.phase[session.exercise];
        session.adjustRest(delta, now());
        if (before != Session.WAITING && session.phase[session.exercise] == Session.WAITING) announceRest();
        page = REST; persistAndRender();
    }
    private void confirmNext() { session.confirmNextSet(); page = TRAIN; persistAndRender(); }
    private void completeExercise() { session.completeExercise(now()); page = PICK; persistAndRender(); }
    private void chooseExercise() { session.postpone(now()); page = PICK; persistAndRender(); }

    private String statusText(int i) {
        switch (session.status[i]) {
            case Session.DONE: return "Completado";
            case Session.OMITTED: return "Omitido por hoy";
            case Session.STARTED: return session.exercise == i ? "En curso" : "Pendiente de retomar";
            default: return "Pendiente";
        }
    }

    private void showPicker() {
        title(session.count(Session.PENDING) == session.sets.length ? "¿Con cuál empiezas?" : "Elige un ejercicio");
        paragraph(session.pendingCount() + " pendientes · " + session.count(Session.DONE) + " completados");
        caption("Al cambiar, se pausan el tiempo y el descanso del ejercicio anterior.");
        if (session.pendingCount() == 0) button("Ver resumen", true, () -> { page = EXIT; render(); });
        // Primero pendientes; los completados y omitidos también se pueden retomar.
        for (int group = 0; group < 2; group++) {
            for (int i = 0; i < session.sets.length; i++) {
                boolean pending = session.status[i] == Session.PENDING || session.status[i] == Session.STARTED;
                if (pending != (group == 0)) continue;
                final int index = i;
                button((pending ? "" : "Retomar · ") + Routines.EXERCISES[session.routine][i] + "\n"
                    + statusText(i) + " · " + session.sets[i] + " series · " + time(session.exerciseTime(i, now())), false,
                    () -> { session.select(index, now()); page = activePage(); persistAndRender(); });
            }
        }
        button("Finalizar sesión", false, () -> { page = EXIT; render(); });
    }

    private void showExit() {
        title("¿Finalizar sesión?");
        paragraph(session.pendingCount() + " ejercicios pendientes y " + session.count(Session.OMITTED) + " omitidos.");
        for (int i = 0; i < session.sets.length; i++)
            if (session.status[i] == Session.PENDING || session.status[i] == Session.STARTED)
                caption(Routines.EXERCISES[session.routine][i] + " · " + statusText(i));
        button("Seguir entrenando", true, this::resumeSession);
        button("Elegir pendientes", false, this::chooseExercise);
        button("Finalizar y ver resumen", false, () -> { session.finish(now()); page = SUMMARY; persistAndRender(); });
    }

    private void showSummary() {
        eyebrow(session.complete ? "¡BIEN HECHO!" : "TU PROGRESO");
        title(session.complete ? "Rutina completada" : "Sesión finalizada"); paragraph(Routines.NAMES[session.routine]);
        label(time(session.elapsed(now())), 32, GREEN, true); caption("Tiempo total, incluidos descansos");
        label(session.totalSets() + " series", 22, WHITE, true);
        caption(session.count(Session.DONE) + " completados · " + session.count(Session.OMITTED) + " omitidos");
        for (int i = 0; i < session.sets.length; i++) {
            label(Routines.EXERCISES[session.routine][i], 16, WHITE, true);
            caption(statusText(i));
            paragraph(session.sets[i] + " series · " + time(session.exerciseTime(i, now())));
        }
        caption("Tiempo entre ejercicios: " + time(session.unassignedTime(now())));
        caption("Cada ejercicio incluye sus descansos y la espera de confirmación. Posponerlo detiene su tiempo.");
        if (session.migrated) caption("La sesión comenzó en la versión anterior. El tiempo previo a la actualización no puede desglosarse y se incluye entre ejercicios.");
        button("Volver al inicio", true, this::clearSession);
        caption("Este resumen no se guarda en un historial.");
    }

    private void announceRest() {
        if (store.vibration()) {
            Vibrator vibrator = getSystemService(Vibrator.class);
            if (vibrator != null && vibrator.hasVibrator())
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 150, 120, 150}, -1));
        }
        if (store.sound()) {
            try {
                if (tone != null) tone.release();
                final ToneGenerator playing = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
                tone = playing;
                playing.startTone(ToneGenerator.TONE_PROP_BEEP2, 350);
                handler.postDelayed(() -> { if (tone == playing) { tone.release(); tone = null; } }, 500);
            } catch (RuntimeException ignored) { /* El aviso visual permanece si no hay salida de audio. */ }
        }
    }
    private void clearSession() { session = null; page = HOME; persistAndRender(); }
    private void resumeSession() { page = activePage(); render(); }
    private void persistAndRender() { store.save(session); render(); }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        column.addView(view, params);
        return view;
    }

    private void eyebrow(String text) { label(text, 10, GREEN, true).setLetterSpacing(0.08f); }
    private void title(String text) { label(text, 22, WHITE, true); }
    private void paragraph(String text) { label(text, 14, MUTED, false); }
    private TextView caption(String text) { return label(text, 12, MUTED, false); }

    private void button(String text, boolean primary, Runnable action) {
        Button view = new Button(this);
        view.setText(text);
        view.setTextSize(14);
        view.setAllCaps(false);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        view.setTextColor(primary ? BG : WHITE);
        view.setBackgroundTintList(null);
        GradientDrawable background = shape(primary ? GREEN : PANEL, 24);
        view.setBackground(new android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(0x33777777), background, null));
        view.setPadding(dp(10), dp(12), dp(10), dp(12));
        view.setMinHeight(dp(48));
        view.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        column.addView(view, params);
        view.setOnClickListener(v -> action.run());
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static String time(long millis) {
        long seconds = millis / 1000;
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private final class RestRing extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private long remaining;
        private boolean paused;
        private String spokenTime;

        RestRing() { super(MainActivity.this); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES); }

        void update(long value, boolean pause) {
            remaining = value;
            paused = pause;
            String description = "Descanso " + (paused ? "pausado, " : "") + time(value + 999);
            if (!description.equals(spokenTime)) { spokenTime = description; setContentDescription(description); }
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = dp(5), cx = getWidth() / 2f, cy = getHeight() / 2f;
            bounds.set(inset, inset, getWidth() - inset, getHeight() - inset);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(5)); paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(PANEL); canvas.drawOval(bounds, paint);
            paint.setColor(paused ? MUTED : GREEN);
            canvas.drawArc(bounds, -90, 360f * Math.min(1f, (float) remaining / Math.max(1L, session.restTotalMs[session.exercise])), false, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(WHITE); paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextSize(28 * getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText(time(remaining + 999), cx, cy + dp(5), paint);
            paint.setColor(MUTED); paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(10 * getResources().getDisplayMetrics().scaledDensity);
            canvas.drawText(paused ? "EN PAUSA" : "RESPIRA", cx, cy + dp(24), paint);
        }
    }
}
