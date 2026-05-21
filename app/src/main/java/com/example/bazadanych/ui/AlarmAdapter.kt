package com.example.bazadanych.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.bazadanych.R
import com.example.bazadanych.data.db.AlarmItem
import com.google.android.material.card.MaterialCardView

class AlarmAdapter(
    private val alarms: List<AlarmItem>,
    private val onItemClick: (AlarmItem) -> Unit
) : RecyclerView.Adapter<AlarmAdapter.AlarmViewHolder>() {

    class AlarmViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardAlarm: MaterialCardView = view.findViewById(R.id.cardAlarm)
        val tvName: TextView = view.findViewById(R.id.tvAlarmName)
        val tvCode: TextView = view.findViewById(R.id.tvAlarmCode)
        val tvMachine: TextView = view.findViewById(R.id.tvAlarmMachine)
        val tvDate: TextView = view.findViewById(R.id.tvAlarmDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alarm, parent, false)
        return AlarmViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlarmViewHolder, position: Int) {
        val alarm = alarms[position]
        val context = holder.itemView.context

        holder.tvName.text = alarm.nazwaAlarmu
        holder.tvCode.text = alarm.kodAlarmu

        // Dynamiczne formatowanie tekstu z pliku strings.xml
        holder.tvMachine.text = context.getString(R.string.alarm_item_machine, alarm.nazwaMaszyny)
        holder.tvDate.text = context.getString(R.string.alarm_item_date, alarm.dataWystapienia)

        holder.cardAlarm.setOnClickListener {
            if (alarm.canDelete) {
                onItemClick(alarm)
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.alarm_item_no_permission),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun getItemCount(): Int = alarms.size
}