package com.armfit.watch;

import java.util.Arrays;

/** Estado de entrenamiento independiente de Android. Todos los tiempos son monotónicos. */
public final class Session {
    public static final long REST_MS = 180_000;
    public static final int PENDING = 0, STARTED = 1, DONE = 2, OMITTED = 3;
    public static final int TRAINING = 0, RESTING = 1, PAUSED = 2, WAITING = 3;
    public int routine, exercise = -1;
    public int[] sets, targets, status, phase;
    public long[] restMs, spentMs, savedRestMs, restTotalMs;
    public boolean active = true, complete, migrated;
    public long startedAt, endedAt, segmentStartedAt, restDeadline;

    public Session(int routine, long now) {
        this(routine, now, 3, REST_MS);
    }

    /** La sesión empieza sin ejercicio seleccionado; el tiempo de elección queda separado. */
    public Session(int routine, long now, int target, long rest) {
        this.routine = routine;
        int count = Routines.EXERCISES[routine].length;
        sets = new int[count]; targets = new int[count]; status = new int[count]; phase = new int[count];
        restMs = new long[count]; spentMs = new long[count]; savedRestMs = new long[count];
        restTotalMs = new long[count];
        Arrays.fill(restMs, Math.max(30000, Math.min(900000, rest)));
        Arrays.fill(targets, Math.max(1, Math.min(50, target)));
        startedAt = now;
    }

    public void select(int index, long now) {
        if (!active || index < 0 || index >= sets.length || index == exercise) return;
        postpone(now);
        exercise = index;
        status[index] = STARTED;
        segmentStartedAt = now;
        if (phase[index] == RESTING) restDeadline = now + savedRestMs[index];
    }

    /** Cambiar de ejercicio congela tanto su tiempo como el descanso pendiente. */
    public void postpone(long now) {
        if (!active || exercise < 0) return;
        spentMs[exercise] += Math.max(0, now - segmentStartedAt);
        if (phase[exercise] == RESTING) {
            savedRestMs[exercise] = remaining(now);
            if (savedRestMs[exercise] == 0) phase[exercise] = WAITING;
        }
        exercise = -1;
    }

    public void finishSet(long now) {
        if (!active || exercise < 0 || phase[exercise] != TRAINING) return;
        sets[exercise]++;
        phase[exercise] = RESTING;
        savedRestMs[exercise] = restMs[exercise];
        restTotalMs[exercise] = restMs[exercise];
        restDeadline = now + restMs[exercise];
    }

    public long remaining(long now) {
        if (exercise < 0) return 0;
        if (phase[exercise] == PAUSED) return savedRestMs[exercise];
        return phase[exercise] == RESTING ? Math.max(0, restDeadline - now) : 0;
    }

    /** Vencer el descanso solo solicita confirmación. No inicia ni registra otra serie. */
    public boolean tick(long now) {
        if (active && exercise >= 0 && phase[exercise] == RESTING && remaining(now) == 0) {
            phase[exercise] = WAITING;
            savedRestMs[exercise] = 0;
            return true;
        }
        return false;
    }

    public void toggleRest(long now) {
        if (!active || exercise < 0) return;
        if (phase[exercise] == RESTING) {
            if (tick(now)) return;
            savedRestMs[exercise] = remaining(now);
            phase[exercise] = PAUSED;
        } else if (phase[exercise] == PAUSED) {
            restDeadline = now + savedRestMs[exercise];
            phase[exercise] = RESTING;
        }
    }

    public void skipRest() {
        if (active && exercise >= 0 && phase[exercise] != TRAINING) {
            phase[exercise] = WAITING;
            savedRestMs[exercise] = 0;
        }
    }

    public void confirmNextSet() {
        if (active && exercise >= 0 && phase[exercise] == WAITING) phase[exercise] = TRAINING;
    }

    /** Ajuste temporal: no altera la preferencia del siguiente descanso. */
    public void adjustRest(long delta, long now) {
        if (!active || exercise < 0 || phase[exercise] == TRAINING) return;
        long value = Math.max(0, Math.min(3_600_000, remaining(now) + delta));
        savedRestMs[exercise] = value;
        restTotalMs[exercise] = Math.max(restTotalMs[exercise], value);
        if (value == 0) phase[exercise] = WAITING;
        else if (phase[exercise] != PAUSED) {
            restDeadline = now + value;
            phase[exercise] = RESTING;
        }
    }

    public void configure(int index, int target, long rest) {
        targets[index] = Math.max(0, Math.min(50, target));
        restMs[index] = Math.max(30_000, Math.min(900_000, rest));
    }

    public void completeExercise(long now) {
        if (!active || exercise < 0 || sets[exercise] == 0) return;
        int index = exercise;
        postpone(now);
        status[index] = DONE;
        phase[index] = TRAINING;
        savedRestMs[index] = 0;
    }

    public void omitExercise(long now) {
        if (!active || exercise < 0) return;
        int index = exercise;
        postpone(now);
        status[index] = OMITTED;
        phase[index] = TRAINING;
        savedRestMs[index] = 0;
    }

    public void finish(long now) {
        if (!active) return;
        postpone(now);
        endedAt = now;
        active = false;
        complete = count(DONE) == sets.length;
    }

    public int count(int value) { int total = 0; for (int v : status) if (v == value) total++; return total; }
    public int pendingCount() { return count(PENDING) + count(STARTED); }
    public int totalSets() { int total = 0; for (int v : sets) total += v; return total; }
    public long exerciseTime(int index, long now) {
        return spentMs[index] + (active && exercise == index ? Math.max(0, now - segmentStartedAt) : 0);
    }
    public long elapsed(long now) { return Math.max(0, (active ? now : endedAt) - startedAt); }
    public long unassignedTime(long now) {
        long total = 0;
        for (int i = 0; i < sets.length; i++) total += exerciseTime(i, now);
        return Math.max(0, elapsed(now) - total);
    }
}
