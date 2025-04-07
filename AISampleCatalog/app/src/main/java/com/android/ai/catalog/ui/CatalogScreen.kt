package com.android.ai.catalog.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.android.ai.catalog.R
import com.android.ai.catalog.ui.domain.SampleCatalog
import com.android.ai.catalog.ui.domain.SampleCatalogItem
import kotlinx.serialization.Serializable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(modifier: Modifier) {
    val navController = rememberNavController()
    val catalog = SampleCatalog(
        modifier = modifier,
        LocalContext.current
    )

    NavHost(
        navController = navController,
        startDestination = HomeScreen,
    ) {
        composable<HomeScreen> {
            Scaffold (
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                        ),
                        title = {
                            Text(text = stringResource(id = R.string.top_bar_title))
                        }
                    )
                },
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier.padding(innerPadding)
                ) {
                    items(catalog.list) {
                        CatalogListItem(catalogItem = it) {
                           navController.navigate(it.route)
                        }
                    }
                }
            }
        }

        catalog.list.forEach {
            val catalogItem = it
            composable(catalogItem.route) {
                catalogItem.sampleEntryScreen()
            }
        }
    }
}

@Composable
fun CatalogListItem(
    catalogItem: SampleCatalogItem,
    onButtonClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.padding(18.dp)
    ) {
        Column(
            Modifier.padding(15.dp)
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                text = catalogItem.title
            )
            Text(
                modifier = Modifier.padding(bottom = 8.dp),
                text = catalogItem.description,
            )
            Row {
                Spacer(Modifier.weight(1f))
                Button(onClick = onButtonClick) {
                    Text(text = stringResource(R.string.open_sample_button))
                }
            }
        }
    }
}

@Serializable
object HomeScreen