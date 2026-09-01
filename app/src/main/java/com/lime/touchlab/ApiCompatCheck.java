package com.lime.touchlab;

import android.content.Context;
import android.view.MotionEvent;

import com.lime.rawtouchcollector.ClockSyncPoint;
import com.lime.rawtouchcollector.Diagnostics;
import com.lime.rawtouchcollector.DisplayProfile;
import com.lime.rawtouchcollector.PhoneSupportMode;
import com.lime.rawtouchcollector.Precision;
import com.lime.rawtouchcollector.RawTouchCollector;
import com.lime.rawtouchcollector.ScenarioType;
import com.lime.rawtouchcollector.SchemaFields;
import com.lime.rawtouchcollector.SessionStatus;
import com.lime.rawtouchcollector.SessionInfo;
import com.lime.rawtouchcollector.SyncMethod;
import com.lime.rawtouchcollector.TaskGroup;
import com.lime.rawtouchcollector.TrialListener;
import com.lime.rawtouchcollector.TrialSamples;
import com.lime.rawtouchcollector.TrialSnapshot;
import com.lime.rawtouchcollector.TrialStatus;

/**
 * Проверка Java-совместимости публичного API AAR.
 *
 * Этот класс нигде не вызывается — он существует, чтобы компилятор подтвердил, что
 * весь контракт доступен из Java без Kotlin-специфики: без suspend, без Flow, без
 * функциональных типов Kotlin, без обобщений в сигнатурах и без передачи data class.
 * Если что-то из этого просочится в публичный API, сборка :app упадёт здесь.
 *
 * Тот же код показывает, как контракт будет выглядеть при вызове из Unity через
 * AndroidJavaObject: строки и примитивы, обратная связь через listener-интерфейс.
 */
@SuppressWarnings("unused")
final class ApiCompatCheck {

    private ApiCompatCheck() {
    }

    static void exerciseEveryPublicMethod(Context context, MotionEvent event) {
        RawTouchCollector collector = new RawTouchCollector(context);

        collector.setTrialListener(new TrialListener() {
            @Override
            public void onTrialCompleted(String trialJson) {
            }

            @Override
            public void onTrialPersisted(String trialId) {
            }

            @Override
            public void onCollectorError(int code, String message) {
            }
        });

        collector.setTrialSink(trial -> {
            readEverySnapshotField(trial);
            return true;
        });

        collector.startSession("session-1", "participant-1");
        collector.updateDisplayProfile(1080, 2400, 1080, 2400, 120.0f, 480);
        collector.invalidateClockSync();
        collector.startTrial("trial-1", TaskGroup.TAP, ScenarioType.STAGE1_TAP);
        collector.processMotionEvent(event);

        String json = collector.getLastTrialJson();
        String schemaVersion = collector.getSchemaVersion();
        Diagnostics diagnostics = collector.getDiagnostics();
        long accepted = diagnostics.getAcceptedTrials();
        long confirmed = diagnostics.getConfirmedTrials();
        long overflows = diagnostics.getQueueOverflows();
        long pending = diagnostics.getPendingTrials();

        SessionInfo session = collector.currentSessionInfo();
        ClockSyncPoint sessionSync = collector.sessionClockSync();

        collector.clearBuffer();
        collector.reset();

        // Блокирующие вызовы — только с фонового потока.
        new Thread(new Runnable() {
            @Override
            public void run() {
                collector.awaitQuiescence(5000L);
                collector.endSession();
                collector.shutdown();
            }
        }).start();
    }

    private static void readEverySnapshotField(TrialSnapshot trial) {
        String trialId = trial.getTrialId();
        String sessionId = trial.getSessionId();
        String participantId = trial.getParticipantId();
        int trialIndex = trial.getTrialIndex();
        String taskGroup = trial.getTaskGroup();
        String scenarioType = trial.getScenarioType();
        String schemaVersion = trial.getSchemaVersion();

        long downNs = trial.getTouchDownCommonTimestampNs();
        long upNs = trial.getTouchUpCommonTimestampNs();
        long durationNs = trial.getContactDurationNs();

        boolean isUp = TrialStatus.UP.equals(trial.getCompletionStatus());
        boolean isCancel = TrialStatus.CANCEL.equals(trial.getCompletionStatus());
        boolean isMultitouch =
                TrialStatus.MULTITOUCH_ERROR.equals(trial.getCompletionStatus());

        int currentCount = trial.getCurrentSampleCount();
        int historicalCount = trial.getHistoricalSampleCount();
        boolean secondPointer = trial.getSecondPointerObserved();
        boolean nanoTime = Precision.NANOSECONDS.equals(trial.getTimestampPrecision());
        boolean nanoReceipt =
                Precision.NANOSECONDS.equals(trial.getAppReceiptUptimePrecision());

        DisplayProfile profile = trial.getDisplayProfile();
        String profileId = profile.getDisplayProfileId();
        int windowWidth = profile.getWindowWidthPx();
        int windowHeight = profile.getWindowHeightPx();
        int modeWidth = profile.getDisplayModeWidthPx();
        int modeHeight = profile.getDisplayModeHeightPx();
        float refreshRate = profile.getDisplayRefreshRateHz();
        int densityDpi = profile.getDensityDpi();
        long profileCapturedAt = profile.getCapturedAtElapsedNs();

        ClockSyncPoint sync = trial.getClockSync();
        String syncId = sync.getClockSyncId();
        long uptimeNs = sync.getUptimeTimestampNs();
        long elapsedNs = sync.getElapsedRealtimeTimestampNs();
        long offsetNs = sync.getOffsetNs();
        long sampling = sync.getSamplingUncertaintyNs();
        long quantization = sync.getQuantizationUncertaintyNs();
        boolean boundary = SyncMethod.MS_BOUNDARY.equals(sync.getSyncMethod());
        String syncPrecision = sync.getUptimeMeasurementPrecision();
        long common = sync.toCommonTimestampNs(uptimeNs);

        TrialSamples samples = trial.getSamples();
        for (int i = 0; i < samples.getCount(); i++) {
            int action = samples.eventAction(i);
            int pointerId = samples.pointerId(i);
            int pointerIndex = samples.pointerIndex(i);
            int actionIndex = samples.actionIndex(i);
            int pointerCount = samples.pointerCount(i);
            int toolType = samples.toolType(i);
            int historyIndex = samples.historyIndex(i);
            boolean historical = samples.isHistorical(i);

            long timeMs = samples.eventTimeUptimeMs(i);
            long timeNs = samples.eventTimeUptimeNs(i);
            long commonNs = samples.commonTimestampNs(i);
            long receiptUptime = samples.appReceiptTimeUptimeNs(i);
            long receiptElapsed = samples.appReceiptTimeElapsedNs(i);
            double relativeMs = samples.relativeTimeMs(i);

            float x = samples.x(i);
            float y = samples.y(i);
            float touchMajor = samples.touchMajor(i);
            float touchMinor = samples.touchMinor(i);
            float size = samples.size(i);
            float pressure = samples.pressure(i);
            float orientation = samples.orientation(i);
        }
    }

    private static void constantsAreReachableFromJava() {
        String hand = PhoneSupportMode.HAND;
        String tap = TaskGroup.TAP;
        String scenario = ScenarioType.STAGE1_TAP;

        // Имена полей схемы и состояния сессии — тоже часть публичного контракта:
        // ими пользуется CSV-экспорт, и второй копии этих строк быть не должно.
        String trialIdField = SchemaFields.TRIAL_ID;
        String sessionIdField = SchemaFields.SESSION_ID;
        String deviceIdField = SchemaFields.DEVICE_ID;
        String statusField = SchemaFields.SESSION_STATUS;
        String completed = SessionStatus.COMPLETED;
        String incomplete = SessionStatus.INCOMPLETE;
    }
}
