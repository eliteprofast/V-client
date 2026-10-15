package com.vclient.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vclient.android.MainViewModel
import com.vclient.android.data.NewsPost
import com.vclient.android.ui.Panel
import com.vclient.android.ui.theme.Gold
import com.vclient.android.ui.theme.Online

@Composable
fun NewsScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("News", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (viewModel.newsLoading) CircularProgressIndicator(Modifier.padding(end = 12.dp), strokeWidth = 2.dp)
            IconButton(onClick = viewModel::refreshNews) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh news") }
        }
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = viewModel.newsFilter == null, onClick = { viewModel.filterNews(null) }, label = { Text("All") })
            FilterChip(selected = viewModel.newsFilter == "launcher", onClick = { viewModel.filterNews("launcher") }, label = { Text("Launcher") })
            FilterChip(selected = viewModel.newsFilter == "server", onClick = { viewModel.filterNews("server") }, label = { Text("Server") })
        }
        when {
            viewModel.newsError != null && viewModel.news.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(viewModel.newsError.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            viewModel.news.isEmpty() && !viewModel.newsLoading -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No posts yet. Check back soon.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(viewModel.news, key = { it.id }) { NewsCard(it) }
                item { Box(Modifier.padding(8.dp)) }
            }
        }
    }
}

@Composable
private fun NewsCard(post: NewsPost) {
    Panel {
        val tag = buildString {
            if (post.pinned) append("PINNED · ")
            append(post.category.uppercase())
            append(" · ")
            append(post.createdAt.take(10))
        }
        Text(
            tag,
            style = MaterialTheme.typography.labelSmall,
            color = if (post.category == "server") Online else Gold,
        )
        Text(post.title, style = MaterialTheme.typography.titleLarge)
        Text(post.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
