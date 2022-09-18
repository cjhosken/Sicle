package io.cjhosken.sicle

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.ListView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doOnTextChanged
import com.mapbox.search.*
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion

class SearchActivity : Activity() {

    private lateinit var searchEngine: SearchEngine
    private lateinit var searchRequestTask: SearchRequestTask

    private val searchCallback = object : SearchSelectionCallback {
        override fun onSuggestions(
            suggestions: List<SearchSuggestion>,
            responseInfo: ResponseInfo
        ) {
            Log.i("SEARCH", suggestions.toString())
            val adapter = SearchSuggestionAdapter(this@SearchActivity, suggestions)

            val suggestionList: ListView = findViewById(R.id.suggestions_list)
            suggestionList.adapter = adapter

        }

        override fun onResult(
            suggestion: SearchSuggestion,
            result: SearchResult,
            responseInfo: ResponseInfo
        ) {
        }

        override fun onCategoryResult(
            suggestion: SearchSuggestion,
            results: List<SearchResult>,
            responseInfo: ResponseInfo
        ) {
        }

        override fun onError(e: Exception) {}
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        hideSystemBars()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        actionBar?.title = ""
        actionBar?.setBackgroundDrawable(ColorDrawable(getColor(R.color.mid)))

        init()
    }

    private fun init() {
        initSearch()
        initLayout()
    }

    private fun initSearch() {
        searchEngine = MapboxSearchSdk.getSearchEngine()
        searchRequestTask =
            searchEngine.search("", SearchOptions(fuzzyMatch = true), searchCallback)
    }

    private fun initLayout() {
        val searchBar: EditText = findViewById(R.id.search_bar)
        searchBar.doOnTextChanged { text, _, _, _ ->
            searchRequestTask = searchEngine.search(
                text.toString(), SearchOptions(
                    fuzzyMatch = true
                ), searchCallback
            )
        }
    }

    private fun hideSystemBars() {
        val windowInsetsController =
            ViewCompat.getWindowInsetsController(window.decorView) ?: return
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    override fun onDestroy() {
        searchRequestTask.cancel()
        super.onDestroy()
    }
}