package com.videocraft.editor.ui.stock

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.videocraft.editor.R
import com.videocraft.editor.data.model.StockMediaItem
import com.videocraft.editor.data.model.StockType
import com.videocraft.editor.databinding.ActivityStockMediaBinding
import com.videocraft.editor.utils.toast
import kotlinx.coroutines.launch

class StockMediaActivity : AppCompatActivity() {

    companion object {
        const val RESULT_MEDIA_URL = "result_media_url"
        const val RESULT_MEDIA_TYPE = "result_media_type"
    }

    private lateinit var binding: ActivityStockMediaBinding
    private val viewModel: StockMediaViewModel by viewModels()
    private lateinit var stockAdapter: StockMediaAdapter

    private var currentTab = StockType.PHOTO
    private var currentPage = 1
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStockMediaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViews()
        observeViewModel()
        viewModel.loadCuratedPhotos()
    }

    private fun setupViews() {
        binding.btnBack.setOnClickListener { finish() }

        // Search
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            performSearch()
            true
        }
        binding.btnSearch.setOnClickListener { performSearch() }

        // Tabs: Photos / Videos / GIFs
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Photos"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Videos"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("GIFs"))

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = when (tab?.position) {
                    0 -> StockType.PHOTO
                    1 -> StockType.VIDEO
                    2 -> StockType.GIF
                    else -> StockType.PHOTO
                }
                currentPage = 1
                performSearch()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // RecyclerView
        stockAdapter = StockMediaAdapter { item ->
            returnMedia(item)
        }

        val columns = if (currentTab == StockType.VIDEO) 2 else 3
        val layoutManager = GridLayoutManager(this, columns)
        binding.rvStockMedia.apply {
            this.layoutManager = layoutManager
            adapter = stockAdapter
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    val total = layoutManager.itemCount
                    val visible = layoutManager.childCount
                    val first = layoutManager.findFirstVisibleItemPosition()
                    if (!isLoading && total <= visible + first + 5) {
                        currentPage++
                        performSearch(append = true)
                    }
                }
            })
        }
    }

    private fun performSearch(append: Boolean = false) {
        val query = binding.etSearch.text?.toString()?.ifBlank { null }
        if (!append) currentPage = 1

        when (currentTab) {
            StockType.PHOTO -> {
                if (query != null) viewModel.searchPhotos(query, currentPage, append)
                else viewModel.loadCuratedPhotos(append)
            }
            StockType.VIDEO -> viewModel.searchVideos(query ?: "nature", currentPage, append)
            StockType.GIF -> viewModel.searchGifs(query ?: "trending", currentPage, append)
        }
    }

    private fun returnMedia(item: StockMediaItem) {
        val result = Intent().apply {
            putExtra(RESULT_MEDIA_URL, item.downloadUrl)
            putExtra(RESULT_MEDIA_TYPE, item.type.name)
        }
        setResult(RESULT_OK, result)
        finish()
    }

    private fun observeViewModel() {
        viewModel.mediaItems.observe(this) { items ->
            stockAdapter.submitList(items)
        }

        viewModel.isLoading.observe(this) { loading ->
            isLoading = loading
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                toast(it)
                viewModel.clearError()
            }
        }
    }
}
