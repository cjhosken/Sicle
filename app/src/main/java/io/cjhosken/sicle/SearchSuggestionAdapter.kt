package io.cjhosken.sicle

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.mapbox.search.result.SearchSuggestion

class SearchSuggestionAdapter(
    private val context: Activity,
    private val items: List<SearchSuggestion>
) : ArrayAdapter<SearchSuggestion>(context, R.layout.item_location, items) {
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var rowView: View? = convertView
        val suggestion = items[position]

        if (rowView == null) {
            val layoutInflater: LayoutInflater =
                context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            rowView = layoutInflater.inflate(R.layout.item_location, parent, false)
        }
        rowView = rowView!!

        rowView.setOnClickListener {
            val result = Intent()
            result.putExtra("query", suggestion.name)
            context.setResult(Activity.RESULT_OK, result)
            context.finish()
        }

        val title: TextView = rowView.findViewById(R.id.location_name)
        title.text = suggestion.name

        val distance: TextView = rowView.findViewById(R.id.location_distance)
        val d = suggestion.distanceMeters?.div(1000)
        distance.text = String.format("%.2f km", d)


        return rowView
    }

    override fun getCount(): Int {
        return items.count()
    }

    override fun getItem(position: Int): SearchSuggestion {
        return items[position]
    }
}