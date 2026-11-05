/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class TrainingReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null)return;
        if("br.com.tabinformatica.nadonomeritmo.TRAINING_REMINDER".equals(intent.getAction()))TrainingReminders.received(context,intent);
        else TrainingReminders.schedule(context);
    }
}
