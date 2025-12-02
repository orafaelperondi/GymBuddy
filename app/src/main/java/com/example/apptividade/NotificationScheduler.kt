package com.example.apptividade

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

object NotificationScheduler {

    // Função para AGENDAR
    fun scheduleWaterReminders(context: Context, peso: Float, inicioStr: String, fimStr: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)

        // 1. Primeiro, limpa qualquer alarme anterior para não duplicar ou misturar contas
        cancelAll(context)

        // 2. Calcular Meta
        val metaTotal = (peso * 35).toInt()
        val qtdCopos = metaTotal / 200 // Copos de 200ml

        if (qtdCopos <= 0) return

        // 3. Converter horários
        val inicioMinutos = timeToMinutes(inicioStr)
        val fimMinutos = timeToMinutes(fimStr)
        val tempoTotalAtivo = fimMinutos - inicioMinutos

        if (tempoTotalAtivo <= 0) return

        // 4. Calcular intervalo
        val intervaloMinutos = tempoTotalAtivo / qtdCopos

        // 5. Agendar
        val calendar = Calendar.getInstance()
        val agoraMinutos = (calendar.get(Calendar.HOUR_OF_DAY) * 60) + calendar.get(Calendar.MINUTE)

        for (i in 0 until qtdCopos) {
            val momentoDoCopo = inicioMinutos + (intervaloMinutos * i)

            // Só agenda se o horário for futuro (ainda hoje)
            if (momentoDoCopo > agoraMinutos) {
                val alarmeCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, momentoDoCopo / 60)
                    set(Calendar.MINUTE, momentoDoCopo % 60)
                    set(Calendar.SECOND, 0)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    i, // ID único (0, 1, 2...)
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                try {
                    // Agenda o alarme exato
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmeCalendar.timeInMillis,
                        pendingIntent
                    )
                    Log.d("Scheduler", "Alarme $i agendado para: ${alarmeCalendar.time}")
                } catch (e: SecurityException) {
                    Log.e("Scheduler", "Sem permissão para alarme")
                }
            }
        }
    }

    // Função para CANCELAR TUDO (Usada no Logout)
    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)

        // Tenta cancelar possíveis 50 alarmes (um número seguro, ninguém bebe 50 copos)
        for (i in 0..50) {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                i,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        Log.d("Scheduler", "Todos os alarmes cancelados.")
    }

    private fun timeToMinutes(time: String): Int {
        try {
            val parts = time.split(":")
            return (parts[0].toInt() * 60) + parts[1].toInt()
        } catch (e: Exception) { return 0 }
    }
}