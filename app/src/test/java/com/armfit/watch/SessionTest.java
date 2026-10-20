package com.armfit.watch;

import org.junit.Test;
import static org.junit.Assert.*;

public class SessionTest {
    private Session start() { Session s = new Session(0, 1000); s.select(0, 1000); return s; }

    @Test public void routineCountsMatchUser() {
        assertArrayEquals(new int[]{7, 8, 7, 10}, java.util.Arrays.stream(Routines.EXERCISES).mapToInt(a -> a.length).toArray());
    }
    @Test public void expiryAlertsExactlyOnceAndRequiresConfirmation() {
        Session s = start(); s.finishSet(2000);
        assertEquals(180000, s.remaining(2000));
        assertFalse(s.tick(181999)); assertTrue(s.tick(182000)); assertFalse(s.tick(190000));
        assertEquals(Session.WAITING, s.phase[0]);
        s.finishSet(200000); assertEquals(1, s.totalSets());
        s.confirmNextSet(); s.finishSet(201000); assertEquals(2, s.totalSets());
    }
    @Test public void duplicateSetDuringRestIsIgnored() {
        Session s = start(); s.finishSet(2000); s.finishSet(2001);
        assertEquals(1, s.totalSets());
    }
    @Test public void restPauseKeepsRemainingTime() {
        Session s = start(); s.finishSet(2000); s.toggleRest(62000);
        assertEquals(120000, s.remaining(900000)); assertFalse(s.tick(900000));
        s.toggleRest(900000); assertEquals(60000, s.remaining(960000)); assertTrue(s.tick(1020000));
    }
    @Test public void pauseAtDeadlineWaitsForUser() {
        Session s = start(); s.finishSet(2000); s.toggleRest(182000);
        assertEquals(Session.WAITING, s.phase[0]); s.confirmNextSet(); assertEquals(Session.TRAINING, s.phase[0]);
    }
    @Test public void skipAlsoWaitsForConfirmation() {
        Session s = start(); s.finishSet(2000); s.skipRest();
        assertEquals(Session.WAITING, s.phase[0]); s.finishSet(3000); assertEquals(1, s.totalSets());
        s.confirmNextSet(); assertEquals(Session.TRAINING, s.phase[0]);
    }
    @Test public void extendAndShortenRestWithoutChangingPreference() {
        Session s = start(); s.finishSet(2000); s.adjustRest(30000, 32000);
        assertEquals(180000, s.remaining(32000)); assertEquals(Session.REST_MS, s.restMs[0]);
        s.adjustRest(-30000, 32000); assertEquals(150000, s.remaining(32000));
        s.adjustRest(-900000, 32000); assertEquals(Session.WAITING, s.phase[0]);
    }
    @Test public void extraRestAfterExpiryCanAlertAgain() {
        Session s = start(); s.finishSet(2000); assertTrue(s.tick(182000));
        s.adjustRest(30000, 190000); assertEquals(30000, s.remaining(190000));
        assertFalse(s.tick(219999)); assertTrue(s.tick(220000)); assertEquals(1, s.totalSets());
    }
    @Test public void adjustingPausedRestPreservesPause() {
        Session s = start(); s.finishSet(2000); s.toggleRest(62000); s.adjustRest(30000, 900000);
        assertEquals(Session.PAUSED, s.phase[0]); assertEquals(150000, s.remaining(999999));
    }
    @Test public void configurationAppliesToNextRestOnly() {
        Session s = start(); s.finishSet(2000); s.configure(0, 4, 60000);
        assertEquals(180000, s.remaining(2000));
        s.skipRest(); s.confirmNextSet(); s.finishSet(3000); assertEquals(60000, s.remaining(3000));
    }
    @Test public void targetsAreFlexibleAndNeverEraseCompletedSets() {
        Session s = start(); s.configure(0, 1, 30000);
        for (int i = 0; i < 3; i++) { s.finishSet(2000 + i); s.skipRest(); s.confirmNextSet(); }
        s.configure(0, 0, 30000); assertEquals(3, s.sets[0]); assertEquals(0, s.targets[0]);
        assertTrue(s.active); assertEquals(Session.STARTED, s.status[0]);
    }
    @Test public void configurationBoundsAreRespected() {
        Session s = start(); s.configure(0, -1, -100); assertEquals(0, s.targets[0]); assertEquals(30000, s.restMs[0]);
        s.configure(0, 999, 99999999); assertEquals(50, s.targets[0]); assertEquals(900000, s.restMs[0]);
    }
    @Test public void switchingFreezesExerciseTimeAndPendingRest() {
        Session s = start(); s.finishSet(2000); s.postpone(62000);
        assertEquals(-1, s.exercise); assertEquals(61000, s.exerciseTime(0, 500000));
        s.select(1, 100000); s.finishSet(101000); s.postpone(120000);
        s.select(0, 200000); assertEquals(120000, s.remaining(200000));
        assertEquals(71000, s.exerciseTime(0, 210000)); assertEquals(20000, s.exerciseTime(1, 210000));
        assertEquals(1, s.sets[0]); assertEquals(1, s.sets[1]);
    }
    @Test public void directlySelectingAnotherExerciseDoesNotDoubleCountTime() {
        Session s = start(); s.select(1, 11000); s.select(1, 15000); s.select(0, 21000);
        s.finish(31000); assertEquals(20000, s.spentMs[0]); assertEquals(10000, s.spentMs[1]);
        assertEquals(0, s.unassignedTime(100000));
    }
    @Test public void completedExerciseCanBeResumedWithoutLosingTimeOrSets() {
        Session s = start(); s.finishSet(2000); s.completeExercise(11000);
        assertEquals(Session.DONE, s.status[0]); assertEquals(-1, s.exercise);
        s.select(0, 21000); assertEquals(Session.STARTED, s.status[0]);
        s.finishSet(22000); s.completeExercise(31000);
        assertEquals(2, s.sets[0]); assertEquals(20000, s.spentMs[0]);
    }
    @Test public void omittedIsDifferentFromPendingAndCanBeResumed() {
        Session s = start(); s.omitExercise(11000);
        assertEquals(1, s.count(Session.OMITTED)); assertEquals(6, s.pendingCount());
        s.select(0, 21000); assertEquals(0, s.count(Session.OMITTED)); assertEquals(7, s.pendingCount());
        assertEquals(10000, s.exerciseTime(0, 21000));
    }
    @Test public void cannotCompleteExerciseWithoutRecordingASet() {
        Session s = start(); s.completeExercise(11000);
        assertEquals(0, s.exercise); assertEquals(0, s.count(Session.DONE));
    }
    @Test public void exerciseTimesAndTransitionsAddUpToSessionDuration() {
        Session s = start(); s.finishSet(2000); s.postpone(11000);
        s.select(2, 21000); s.finishSet(22000); s.completeExercise(41000); s.finish(51000);
        assertEquals(10000, s.spentMs[0]); assertEquals(20000, s.spentMs[2]);
        assertEquals(20000, s.unassignedTime(100000)); assertEquals(50000, s.elapsed(100000));
        assertFalse(s.complete); assertEquals(1, s.count(Session.DONE));
    }
    @Test public void confirmationWaitIsIncludedInExerciseTime() {
        Session s = start(); s.finishSet(2000); s.tick(182000); s.postpone(200000);
        assertEquals(199000, s.spentMs[0]); s.select(0, 300000);
        assertEquals(Session.WAITING, s.phase[0]); assertFalse(s.tick(300001));
    }
    @Test public void finishingAllExercisesInReverseOrderCompletesRoutine() {
        Session s = new Session(0, 0);
        for (int i = 6; i >= 0; i--) { s.select(i, 1000); s.finishSet(2000); s.completeExercise(3000); }
        assertTrue(s.active); assertEquals(0, s.pendingCount()); s.finish(100000);
        assertTrue(s.complete); assertFalse(s.active); assertEquals(7, s.totalSets());
    }
    @Test public void noMutationsAfterSessionFinishes() {
        Session s = start(); s.finishSet(2000); s.finish(3000);
        s.select(1, 5000); s.finishSet(6000); s.adjustRest(30000, 7000); s.finish(99999);
        assertEquals(-1, s.exercise); assertEquals(1, s.totalSets()); assertEquals(2000, s.elapsed(100000));
    }
    @Test public void pausedRestSurvivesSwitchingExercises() {
        Session s = start(); s.finishSet(2000); s.toggleRest(62000); s.postpone(63000);
        s.select(1, 70000); s.select(0, 900000);
        assertEquals(Session.PAUSED, s.phase[0]); assertEquals(120000, s.remaining(999999));
    }
    @Test public void newSessionWaitsForChoiceWithThreeSetsAndThreeMinutes() {
        Session s = new Session(1, 1000);
        assertEquals(-1, s.exercise);
        for (int i = 0; i < s.sets.length; i++) {
            assertEquals(3, s.targets[i]); assertEquals(180000, s.restMs[i]);
            assertEquals(Session.PENDING, s.status[i]); assertEquals(0, s.exerciseTime(i, 5000));
        }
        s.finishSet(2000); assertEquals(0, s.totalSets());
        s.select(5, 6000); assertEquals(5, s.exercise);
        assertEquals(5000, s.unassignedTime(7000)); assertEquals(1000, s.exerciseTime(5, 7000));
    }
    @Test public void generalConfigurationAppliesToEveryExerciseWithoutAutoStarting() {
        Session s = new Session(3, 1000, 5, 90000);
        assertEquals(-1, s.exercise);
        for (int i = 0; i < s.sets.length; i++) {
            assertEquals(5, s.targets[i]); assertEquals(90000, s.restMs[i]);
        }
        s.select(7, 2000); s.finishSet(3000); assertEquals(90000, s.remaining(3000));
    }
}
