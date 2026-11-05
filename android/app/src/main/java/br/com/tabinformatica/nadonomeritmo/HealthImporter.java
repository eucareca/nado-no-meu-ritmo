/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import android.annotation.TargetApi;
import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.ReadRecordsRequestUsingFilters;
import android.health.connect.ReadRecordsResponse;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.DistanceRecord;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.ExerciseSessionType;
import android.health.connect.datatypes.HeartRateRecord;
import android.health.connect.datatypes.units.Length;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Optional foreground, read-only access to the on-device Health Connect service, API 34+. */
@TargetApi(34)
@SuppressLint("MissingPermission") // All reads are gated by allowed(); permission revocation is handled as an error.
final class HealthImporter {
    static final String[] PERMISSIONS = {"android.permission.health.READ_EXERCISE", "android.permission.health.READ_DISTANCE", "android.permission.health.READ_HEART_RATE"};
    interface Reply { void send(String requestId, JSONObject value); }
    private final Activity activity;
    private final Reply reply;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HealthConnectManager manager;
    private int generation;
    private boolean partial;
    private boolean metricsMissing;
    private final List<ExerciseSessionRecord> sessions = new ArrayList<>();
    private Instant since, until;
    private String requestId;

    HealthImporter(Activity activity, Reply reply) {
        this.activity = activity;
        this.reply = reply;
        manager = activity.getSystemService(HealthConnectManager.class);
    }
    void cancel() { generation++; sessions.clear(); }
    private boolean allowed(String permission) { return activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }
    void read(String id) {
        cancel();
        requestId = id;
        partial = false; metricsMissing = false;
        final int run = generation;
        if (manager == null) { fail(run, "Health Connect não está disponível neste aparelho."); return; }
        if (!allowed(PERMISSIONS[0])) { fail(run, "Autorize a leitura de exercícios no Health Connect para consultar os treinos. Distância e frequência cardíaca são opcionais."); return; }
        until = Instant.now(); since = until.minus(Duration.ofDays(28));
        handler.postDelayed(() -> { if (run == generation) { fail(run, "A consulta demorou demais. Mantenha o aplicativo aberto e tente novamente."); cancel(); } }, 60000);
        page(run, -1, 0);
    }
    private boolean active(int run) { return run == generation && !activity.isFinishing() && !activity.isDestroyed(); }
    private void fail(int run, String message) {
        if (!active(run)) return;
        JSONObject result = new JSONObject();
        put(result, "error", message);
        reply.send(requestId, result);
    }
    private static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value); } catch (JSONException ignored) { }
    }
    private void page(int run, long token, int pages) {
        if (!active(run)) return;
        ReadRecordsRequestUsingFilters.Builder<ExerciseSessionRecord> builder = new ReadRecordsRequestUsingFilters.Builder<>(ExerciseSessionRecord.class)
                .setTimeRangeFilter(new TimeInstantRangeFilter.Builder().setStartTime(since).setEndTime(until).build()).setPageSize(100);
        if (token == -1) builder.setAscending(false); else builder.setPageToken(token);
        try {
            manager.readRecords(builder.build(), activity.getMainExecutor(), new OutcomeReceiver<ReadRecordsResponse<ExerciseSessionRecord>, HealthConnectException>() {
                @Override public void onResult(ReadRecordsResponse<ExerciseSessionRecord> response) {
                    if (!active(run)) return;
                    for (ExerciseSessionRecord session : response.getRecords()) {
                        int type = session.getExerciseType();
                        if (type != ExerciseSessionType.EXERCISE_SESSION_TYPE_SWIMMING_POOL && type != ExerciseSessionType.EXERCISE_SESSION_TYPE_SWIMMING_OPEN_WATER) continue;
                        long seconds = Duration.between(session.getStartTime(), session.getEndTime()).getSeconds();
                        if (seconds <= 0 || seconds > 86400) continue;
                        if (sessions.size() == 100) { partial = true; break; }
                        sessions.add(session);
                    }
                    long next = response.getNextPageToken();
                    if (next != -1 && sessions.size() < 100 && pages < 19) page(run, next, pages + 1);
                    else { if (next != -1) partial = true; enrich(run, 0, new JSONArray()); }
                }
                @Override public void onError(HealthConnectException error) { fail(run, "Não foi possível consultar os treinos. Confira as permissões e a sincronização no Health Connect e tente novamente."); }
            });
        } catch (RuntimeException error) { fail(run, "A leitura foi interrompida. Confira as permissões do Health Connect e tente novamente."); }
    }
    private void enrich(int run, int index, JSONArray rows) {
        if (!active(run)) return;
        if (index == sessions.size()) {
            JSONObject result = new JSONObject();
            put(result, "sessions", rows); put(result, "partial", partial); put(result, "metricsMissing", metricsMissing);
            reply.send(requestId, result);
            generation++; // Invalidate the timeout and any late callbacks after completion.
            sessions.clear();
            return;
        }
        ExerciseSessionRecord session = sessions.get(index);
        JSONObject row = new JSONObject();
        put(row, "sourceId", session.getMetadata().getId());
        put(row, "source", session.getMetadata().getDataOrigin().getPackageName());
        put(row, "start", session.getStartTime().toString()); put(row, "end", session.getEndTime().toString());
        ZoneId zone = session.getStartZoneOffset() == null ? ZoneId.systemDefault() : session.getStartZoneOffset();
        put(row, "date", session.getStartTime().atZone(zone).toLocalDate().toString());
        put(row, "type", session.getExerciseType() == ExerciseSessionType.EXERCISE_SESSION_TYPE_SWIMMING_POOL ? "pool" : "openWater");
        put(row, "title", session.getTitle() == null ? "Natação" : session.getTitle().toString().substring(0, Math.min(160, session.getTitle().length())));
        if (!allowed(PERMISSIONS[1])) { heart(run, index, rows, row); return; }
        AggregateRecordsRequest<Length> request = new AggregateRecordsRequest.Builder<Length>(new TimeInstantRangeFilter.Builder().setStartTime(session.getStartTime()).setEndTime(session.getEndTime()).build())
                .addAggregationType(DistanceRecord.DISTANCE_TOTAL).addDataOriginsFilter(session.getMetadata().getDataOrigin()).build();
        try {
            manager.aggregate(request, activity.getMainExecutor(), new OutcomeReceiver<AggregateRecordsResponse<Length>, HealthConnectException>() {
                @Override public void onResult(AggregateRecordsResponse<Length> response) {
                    if (!active(run)) return;
                    Length distance = response.get(DistanceRecord.DISTANCE_TOTAL);
                    if (distance != null) put(row, "distanceMeters", distance.getInMeters());
                    heart(run, index, rows, row);
                }
                @Override public void onError(HealthConnectException error) { if (!active(run)) return; metricsMissing = true; heart(run, index, rows, row); }
            });
        } catch (RuntimeException error) { metricsMissing = true; heart(run, index, rows, row); }
    }
    private void heart(int run, int index, JSONArray rows, JSONObject row) {
        if (!active(run)) return;
        if (!allowed(PERMISSIONS[2])) { rows.put(row); enrich(run, index + 1, rows); return; }
        ExerciseSessionRecord session = sessions.get(index);
        AggregateRecordsRequest<Long> request = new AggregateRecordsRequest.Builder<Long>(new TimeInstantRangeFilter.Builder().setStartTime(session.getStartTime()).setEndTime(session.getEndTime()).build())
                .addAggregationType(HeartRateRecord.BPM_AVG).addDataOriginsFilter(session.getMetadata().getDataOrigin()).build();
        try {
            manager.aggregate(request, activity.getMainExecutor(), new OutcomeReceiver<AggregateRecordsResponse<Long>, HealthConnectException>() {
                @Override public void onResult(AggregateRecordsResponse<Long> response) {
                    if (!active(run)) return;
                    Long bpm = response.get(HeartRateRecord.BPM_AVG);
                    if (bpm != null) put(row, "heartRateAverage", bpm);
                    rows.put(row); enrich(run, index + 1, rows);
                }
                @Override public void onError(HealthConnectException error) { if (!active(run)) return; metricsMissing = true; rows.put(row); enrich(run, index + 1, rows); }
            });
        } catch (RuntimeException error) { metricsMissing = true; rows.put(row); enrich(run, index + 1, rows); }
    }
}
