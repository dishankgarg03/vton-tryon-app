package com.example.vtonapp

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel: TryOnViewModel = viewModel()
                    val uiState by viewModel.uiState.collectAsState()
                    val context = LocalContext.current

                    var personImageUri by remember { mutableStateOf<Uri?>(null) }
                    var garmentImageUri by remember { mutableStateOf<Uri?>(null) }
                    var selectedCategory by remember { mutableStateOf("tops") }
                    var dropdownExpanded by remember { mutableStateOf(false) }

                    val personPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { personImageUri = it }
                    val garmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { garmentImageUri = it }

                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(text = "AI Virtual Trial Room", style = MaterialTheme.typography.headlineMedium)

                        when (val state = uiState) {
                            is TryOnUiState.Loading -> {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text("AI Processing... Swapping clothes & updating lighting.")
                                    }
                                }
                            }
                            is TryOnUiState.Success -> {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        AsyncImage(
                                            model = state.imageUrl,
                                            contentDescription = "Output result",
                                            modifier = Modifier.fillMaxHeight(0.85f).fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                                            contentScale = ContentScale.Fit
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(onClick = { viewModel.resetState() }) { Text("Try On Another Item") }
                                    }
                                }
                            }
                            else -> {
                                if (state is TryOnUiState.Error) {
                                    Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                                    viewModel.resetState()
                                }
                                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    ImageSelectorBox(modifier = Modifier.weight(1f), title = "Your Photo", uri = personImageUri) { personPicker.launch("image/*") }
                                    ImageSelectorBox(modifier = Modifier.weight(1f), title = "Garment Item", uri = garmentImageUri) { garmentPicker.launch("image/*") }
                                }
                            }
                        }

                        if (uiState is TryOnUiState.Idle) {
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded,
                                onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedCategory.uppercase(),
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select Apparel Category") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                                    modifier = Modifier.fillMaxWidth().menuAnchor()
                                )
                                ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                                    DropdownMenuItem(text = { Text("TOPS (T-Shirt, Shirt, Blazer, Coat)") }, onClick = { selectedCategory = "tops"; dropdownExpanded = false })
                                    DropdownMenuItem(text = { Text("BOTTOMS (Jeans, Pants, Skirt)") }, onClick = { selectedCategory = "bottoms"; dropdownExpanded = false })
                                    DropdownMenuItem(text = { Text("ONE-PIECES (Dress, Jumpsuit, Full Suit)") }, onClick = { selectedCategory = "one-pieces"; dropdownExpanded = false })
                                }
                            }

                            Button(
                                onClick = { viewModel.processVirtualTryOn(context, personImageUri!!, garmentImageUri!!, selectedCategory) },
                                enabled = (personImageUri != null && garmentImageUri != null),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Generate Photorealistic Try-On")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImageSelectorBox(modifier: Modifier, title: String, uri: Uri?, onClick: () -> Unit) {
    Box(
        modifier = modifier.fillMaxHeight().border(2.dp, Color.LightGray, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            AsyncImage(model = uri, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(text = "Tap to choose\n$title", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
