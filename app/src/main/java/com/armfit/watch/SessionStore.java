package com.armfit.watch;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Recuperación versionada; las preferencias sobreviven al cierre de una sesión. */
public final class SessionStore {
    private final SharedPreferences prefs;
    private final SharedPreferences options;
    private final int boot;

    public SessionStore(Context context) {
        prefs = context.getSharedPreferences("armfit_session", Context.MODE_PRIVATE);
        options = context.getSharedPreferences("armfit_options", Context.MODE_PRIVATE);
        boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
    }

    // Preferencias generales nuevas: los ajustes por ejercicio de 1.1 no afectan sesiones nuevas.
    public int target() { return Math.max(1, Math.min(50, options.getInt("default_sets", 3))); }
    public long rest() { return Math.max(30_000, Math.min(900_000, options.getLong("default_rest", Session.REST_MS))); }
    public boolean sound() { return options.getBoolean("sound", false); }
    public boolean vibration() { return options.getBoolean("vibration", true); }
    public void sound(boolean enabled) { options.edit().putBoolean("sound", enabled).apply(); }
    public void vibration(boolean enabled) { options.edit().putBoolean("vibration", enabled).apply(); }
    public void configure(int target, long rest) {
        options.edit().putInt("default_sets", Math.max(1, Math.min(50, target)))
            .putLong("default_rest", Math.max(30000, Math.min(900000, rest))).apply();
    }

    public Session create(int routine, long now) {
        return new Session(routine, now, target(), rest());
    }

    public void save(Session s) {
        if (s == null) { prefs.edit().remove("session").apply(); return; }
        try {
            JSONObject j = new JSONObject();
            j.put("version", 2).put("boot", boot).put("routine", s.routine).put("exercise", s.exercise)
                .put("sets", new JSONArray(s.sets)).put("targets", new JSONArray(s.targets))
                .put("status", new JSONArray(s.status)).put("phase", new JSONArray(s.phase))
                .put("restMs", new JSONArray(s.restMs)).put("spentMs", new JSONArray(s.spentMs))
                .put("savedRestMs", new JSONArray(s.savedRestMs)).put("restTotalMs", new JSONArray(s.restTotalMs))
                .put("active", s.active).put("complete", s.complete).put("migrated", s.migrated)
                .put("start", s.startedAt).put("end", s.endedAt).put("segment", s.segmentStartedAt)
                .put("deadline", s.restDeadline);
            prefs.edit().putString("session", j.toString()).apply();
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }

    public Session load(long now) {
        try {
            String raw = prefs.getString("session", null);
            if (raw == null) return null;
            JSONObject j = new JSONObject(raw);
            if (j.getInt("boot") != boot) { save(null); return null; }
            int routine = j.getInt("routine");
            if (routine < 0 || routine >= Routines.NAMES.length) throw new JSONException("routine");
            Session s = new Session(routine, j.getLong("start"));
            s.active = j.getBoolean("active");
            s.complete = j.getBoolean("complete");
            s.endedAt = j.getLong("end");
            s.restDeadline = j.getLong("deadline");
            readInts(j, "sets", s.sets);
            if (j.optInt("version", 1) == 1) {
                migrate(j, s, now);
            } else {
                s.exercise = j.getInt("exercise");
                s.segmentStartedAt = j.getLong("segment");
                s.migrated = j.optBoolean("migrated", false);
                readInts(j, "targets", s.targets); readInts(j, "status", s.status); readInts(j, "phase", s.phase);
                readLongs(j, "restMs", s.restMs); readLongs(j, "spentMs", s.spentMs);
                readLongs(j, "savedRestMs", s.savedRestMs); readLongs(j, "restTotalMs", s.restTotalMs);
            }
            if (s.startedAt < 0 || s.startedAt > now || s.exercise < -1 || s.exercise >= s.sets.length
                || s.segmentStartedAt > now || (!s.active && s.exercise != -1)) throw new JSONException("state");
            for (int i = 0; i < s.sets.length; i++) {
                if (s.sets[i] < 0 || s.targets[i] < 0 || s.targets[i] > 50 || s.status[i] < 0 || s.status[i] > 3
                    || s.phase[i] < 0 || s.phase[i] > 3 || s.spentMs[i] < 0
                    || s.restMs[i] < 30000 || s.restMs[i] > 900000 || s.savedRestMs[i] < 0)
                    throw new JSONException("exercise state");
            }
            return s;
        } catch (JSONException e) { save(null); return null; }
    }

    private void migrate(JSONObject j, Session s, long now) throws JSONException {
        int old = j.getInt("exercise");
        if (old < 0 || old > s.sets.length || (s.active && old == s.sets.length)) throw new JSONException("legacy");
        for (int i = 0; i < s.sets.length; i++) {
            s.configure(i, target(), rest());
            s.status[i] = i < old ? Session.DONE : s.sets[i] > 0 ? Session.STARTED : Session.PENDING;
        }
        s.exercise = s.active ? old : -1;
        s.segmentStartedAt = now;
        s.migrated = true;
        if (s.active) {
            s.status[old] = Session.STARTED;
            if (j.getBoolean("resting")) {
                s.phase[old] = j.getBoolean("paused") ? Session.PAUSED : Session.RESTING;
                s.savedRestMs[old] = j.getLong("remaining");
                s.restTotalMs[old] = Session.REST_MS;
            }
        }
    }

    private static void readInts(JSONObject j, String key, int[] values) throws JSONException {
        JSONArray a = j.getJSONArray(key);
        if (a.length() != values.length) throw new JSONException(key);
        for (int i = 0; i < values.length; i++) values[i] = a.getInt(i);
    }
    private static void readLongs(JSONObject j, String key, long[] values) throws JSONException {
        JSONArray a = j.getJSONArray(key);
        if (a.length() != values.length) throw new JSONException(key);
        for (int i = 0; i < values.length; i++) values[i] = a.getLong(i);
    }
}
