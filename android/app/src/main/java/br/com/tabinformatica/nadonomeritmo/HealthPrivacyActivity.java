/* SPDX-License-Identifier: Apache-2.0 */
package br.com.tabinformatica.nadonomeritmo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

/** Local permission rationale, reachable from the Android Health Connect permission screen. */
public final class HealthPrivacyActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        text.setPadding(padding, padding, padding, padding);
        text.setTextSize(18);
        text.setText("Relógios e privacidade\n\nO Nado no meu ritmo consulta os últimos 28 dias de exercícios no Health Connect somente quando você pede. Apenas sessões de natação são exibidas. Distância e frequência cardíaca média são opcionais e limitadas ao horário e aplicativo de origem de cada sessão.\n\nVocê revisa os resultados e escolhe quais guardar. A leitura de exercícios é necessária para esta função; as demais permissões podem ser negadas.\n\nNão escrevemos, apagamos ou alteramos dados no Health Connect. Não há acesso em segundo plano nem permissão de internet. As cópias importadas ficam no aparelho e entram no backup manual do diário, que pode ser protegido por senha. Os dados no aparelho não têm criptografia própria do app.\n\nVocê pode revogar o acesso nas configurações do Health Connect. Revogar não apaga cópias já importadas; para isso, exclua a sessão no diário ou apague os registros nas configurações. Isso também não apaga dados do Mi Fitness, Samsung Health ou Health Connect.\n\nO tratamento e a eventual sincronização em nuvem pelos aplicativos de origem seguem as políticas deles. Não conectamos diretamente ao relógio nem enviamos treinos para ele.\n\nLicença Apache-2.0\nContato: Tiago Assis Brasil · tiago@tabinformatica.com.br");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text); setContentView(scroll);
    }
}
