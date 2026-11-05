/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import android.app.job.JobService;
import android.app.job.JobParameters;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AutoBackupJobService extends JobService {
    private Future<?> work;
    private AtomicBoolean active;
    @Override public boolean onStartJob(JobParameters parameters){
        AtomicBoolean running=new AtomicBoolean(true);active=running;
        work=AutoBackupStore.WORK.submit(()->{
            boolean success=AutoBackupStore.deliver(getApplicationContext());
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{if(running.getAndSet(false))jobFinished(parameters,!success);});
        });return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){
        if(active!=null)active.set(false);if(work!=null)work.cancel(true);return !AutoBackupStore.target(this).isEmpty();
    }
}
