import android.content.Context
import android.widget.TextView
import com.example.bazadanych.R
import com.example.bazadanych.data.db.FieldHistory
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF

class CustomMarkerView(context: Context, layoutResource: Int, private val dataList: List<FieldHistory>) : MarkerView(context, layoutResource) {
    private val tvDate: TextView = findViewById(R.id.tvMarkerDate)
    private val tvValue: TextView = findViewById(R.id.tvMarkerValue)

    override fun refreshContent(e: Entry?, highlight: Highlight?) {
        val index = e?.x?.toInt() ?: 0
        if (index >= 0 && index < dataList.size) {
            val history = dataList[index]
            tvDate.text = history.recorded_at?.substringAfter(" ")?.substringBeforeLast(":") ?: ""
            tvValue.text = "${e?.y}"
        }
        super.refreshContent(e, highlight)
    }

    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height - 20).toFloat())
    }
}