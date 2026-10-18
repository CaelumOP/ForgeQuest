package com.forgequest.ai.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.forgequest.ai.data.local.entity.VaultFoodItemEntity
import com.forgequest.ai.domain.model.AiModelTier
import com.forgequest.ai.domain.model.FoodCategory
import com.forgequest.ai.domain.model.HiddenFatAddition
import com.forgequest.ai.domain.model.PortionMultiplier
import com.forgequest.ai.domain.model.VisionFoodAnalysisResult
import com.forgequest.ai.ui.components.MorphingNeonCheckbox
import com.forgequest.ai.ui.haptics.HapticsManager
import com.forgequest.ai.ui.theme.BackgroundCanvas
import com.forgequest.ai.ui.theme.MuscleHotCoral
import com.forgequest.ai.ui.theme.OnPrimaryTeal
import com.forgequest.ai.ui.theme.PrimaryTeal
import com.forgequest.ai.ui.theme.PrimaryTealTrack
import com.forgequest.ai.ui.theme.SecondaryBlue
import com.forgequest.ai.ui.theme.SecondaryBlueTrack
import com.forgequest.ai.ui.theme.SurfaceCard
import com.forgequest.ai.ui.theme.SurfaceContainerHigh
import com.forgequest.ai.ui.theme.TextPrimary
import com.forgequest.ai.ui.theme.TextSecondary
import com.forgequest.ai.ui.theme.WarmAmberTrack
import com.forgequest.ai.ui.theme.WarmGold
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private val MULTIPLIER_STEPS = listOf(0.5f, 1.0f, 1.5f, 2.0f, 3.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodVaultScreen(
    vaultItems: List<VaultFoodItemEntity>,
    activeModelTier: AiModelTier,
    isAnalyzingPhoto: Boolean,
    visionAnalysisResult: VisionFoodAnalysisResult?,
    haptics: HapticsManager?,
    onSwitchModelTier: (AiModelTier) -> Unit,
    onSaveOrUpdateVaultItem: (VaultFoodItemEntity) -> Unit,
    onDeleteVaultItem: (VaultFoodItemEntity) -> Unit,
    onLogSelectedVaultItems: (List<Pair<VaultFoodItemEntity, Float>>) -> Unit,
    onAnalyzeFoodPhoto: (Bitmap?, AiModelTier) -> Unit,
    onConfirmVisionMealLog: (
        dishName: String,
        category: FoodCategory,
        totalCalories: Int,
        totalProteinGrams: Float,
        saveToVault: Boolean,
        pinToTop: Boolean
    ) -> Unit,
    onClearVisionResult: () -> Unit,
    hasApiKey: Boolean = true,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(FoodCategory.BREAKFAST) }

    // Multi-Select Checklist State: foodId -> quantityMultiplier
    val selectedFoodMultipliers = remember { mutableStateMapOf<Long, Float>() }

    // Edit / Create Vault Food Dialog State
    var editingVaultItem by remember { mutableStateOf<VaultFoodItemEntity?>(null) }
    var isCreatingNewVaultItem by remember { mutableStateOf(false) }

    // CameraX Hand-Scaled AI Modal State
    var showAiCameraModal by remember { mutableStateOf(false) }

    val filteredItems = remember(vaultItems, selectedCategory) {
        vaultItems
            .filter { it.category == selectedCategory }
            .sortedWith(compareByDescending<VaultFoodItemEntity> { it.isPinned }.thenBy { it.name })
    }

    // Calculate live totals for Bottom Floating Action Bar
    val selectedEntries = remember(vaultItems, selectedFoodMultipliers.toMap()) {
        vaultItems.mapNotNull { item ->
            val mult = selectedFoodMultipliers[item.foodId]
            if (mult != null) item to mult else null
        }
    }
    val totalSelectedCalories = selectedEntries.sumOf { (item, mult) ->
        (item.caloriesPerServing * mult).roundToInt()
    }
    val totalSelectedProtein = selectedEntries.sumOf { (item, mult) ->
        (item.proteinGramsPerServing * mult).toDouble()
    }.toFloat()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundCanvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top Action Header: Title "Food" (28.sp) on left; "Scan meal" & "Add custom" pills on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Food",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Normal,
                    color = TextPrimary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "Scan meal" Pill (Camera icon, #5CD6C0 fill, #00382E text)
                    Button(
                        onClick = {
                            haptics?.tick()
                            if (!hasApiKey) {
                                onAnalyzeFoodPhoto(null, activeModelTier)
                            } else {
                                showAiCameraModal = true
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryTeal,
                            contentColor = OnPrimaryTeal
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Scan meal",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // "Add custom" Pill (+ icon, #282A2C fill, #E3E3E3 text)
                    Surface(
                        onClick = {
                            haptics?.tick()
                            isCreatingNewVaultItem = true
                            editingVaultItem = VaultFoodItemEntity(
                                foodId = 0L,
                                name = "",
                                category = selectedCategory,
                                caloriesPerServing = 250,
                                proteinGramsPerServing = 20f,
                                servingDescription = "1 serving",
                                photoUri = null,
                                isPinned = true
                            )
                        },
                        shape = RoundedCornerShape(24.dp),
                        color = SurfaceContainerHigh
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add custom",
                                fontSize = 13.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5 Strict Category Filter Pills: Breakfast | Lunch | Dinner | Snacks | Drinks (borderless)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FoodCategory.entries.forEach { category ->
                    val isSelected = category == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics?.tick()
                            selectedCategory = category
                        },
                        label = {
                            Text(
                                text = category.displayName,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimaryTealTrack,
                            selectedLabelColor = PrimaryTeal,
                            containerColor = SurfaceCard,
                            labelColor = TextSecondary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-Select Visual Food Cards (Pinned Items First)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredItems.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No items in ${selectedCategory.displayName} yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap Add custom or Scan meal to save your favorite foods to this category.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    filteredItems.forEach { foodItem ->
                        val isChecked = selectedFoodMultipliers.containsKey(foodItem.foodId)
                        val currentMultiplier = selectedFoodMultipliers[foodItem.foodId] ?: 1.0f

                        VisualVaultFoodCard(
                            item = foodItem,
                            isSelected = isChecked,
                            multiplier = currentMultiplier,
                            onToggleSelect = { checked ->
                                haptics?.tick()
                                if (checked) {
                                    selectedFoodMultipliers[foodItem.foodId] = currentMultiplier
                                } else {
                                    selectedFoodMultipliers.remove(foodItem.foodId)
                                }
                            },
                            onStepMultiplier = { direction ->
                                haptics?.tick()
                                val currentIdx = MULTIPLIER_STEPS.indexOf(currentMultiplier).let {
                                    if (it == -1) 1 else it
                                }
                                val nextIdx = (currentIdx + direction).coerceIn(0, MULTIPLIER_STEPS.lastIndex)
                                selectedFoodMultipliers[foodItem.foodId] = MULTIPLIER_STEPS[nextIdx]
                            },
                            onEditThumbnailOrItem = {
                                haptics?.tick()
                                isCreatingNewVaultItem = false
                                editingVaultItem = foodItem
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        // Floating Bottom Action Bar when 1 or more items are checked
        if (selectedEntries.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        haptics?.setComplete()
                        onLogSelectedVaultItems(selectedEntries)
                        selectedFoodMultipliers.clear()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryTeal,
                        contentColor = OnPrimaryTeal
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Log ${selectedEntries.size} selected items ($totalSelectedCalories kcal · ${totalSelectedProtein.roundToInt()}g P)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // Editable Photo Thumbnail & Food Item Metadata Dialog
    editingVaultItem?.let { itemToEdit ->
        VaultItemEditorDialog(
            initialItem = itemToEdit,
            isNew = isCreatingNewVaultItem,
            haptics = haptics,
            onDismiss = { editingVaultItem = null },
            onSave = { updated ->
                onSaveOrUpdateVaultItem(updated)
                editingVaultItem = null
            },
            onDelete = { toDelete ->
                onDeleteVaultItem(toDelete)
                selectedFoodMultipliers.remove(toDelete.foodId)
                editingVaultItem = null
            }
        )
    }

    // Hand-Scaled AI Camera Modal (CameraX)
    if (showAiCameraModal) {
        HandScaledAiCameraModal(
            activeModelTier = activeModelTier,
            isAnalyzing = isAnalyzingPhoto,
            haptics = haptics,
            onSwitchModelTier = onSwitchModelTier,
            onCaptureOrAnalyzeBitmap = { bitmap ->
                if (!hasApiKey) {
                    showAiCameraModal = false
                }
                onAnalyzeFoodPhoto(bitmap, activeModelTier)
            },
            onDismiss = { showAiCameraModal = false }
        )
    }

    // AI Camera Confirmation Sheet (Zero Text Collisions & Zero Cutoffs)
    if (visionAnalysisResult != null) {
        showAiCameraModal = false
        VisionPostPhotoConfirmationSheet(
            result = visionAnalysisResult,
            defaultCategory = selectedCategory,
            haptics = haptics,
            onConfirmLog = { dishName, category, totalKcal, totalProtein, saveToVault, pinToTop ->
                haptics?.questClearAndPr()
                onConfirmVisionMealLog(
                    dishName,
                    category,
                    totalKcal,
                    totalProtein,
                    saveToVault,
                    pinToTop
                )
            },
            onDismiss = onClearVisionResult
        )
    }
}

@Composable
private fun VisualVaultFoodCard(
    item: VaultFoodItemEntity,
    isSelected: Boolean,
    multiplier: Float,
    onToggleSelect: (Boolean) -> Unit,
    onStepMultiplier: (Int) -> Unit,
    onEditThumbnailOrItem: () -> Unit
) {
    val displayCalories = (item.caloriesPerServing * multiplier).roundToInt()
    val displayProtein = (item.proteinGramsPerServing * multiplier).roundToInt()
    val multText = if (multiplier % 1f == 0f) "${multiplier.toInt()}x" else "${multiplier}x"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Left: Bouncy Checkbox [✓]
            MorphingNeonCheckbox(
                checked = isSelected,
                onCheckedChange = onToggleSelect
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Center-Left: 48.dp rounded-square (12.dp radius) Food Thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceContainerHigh)
                    .clickable { onEditThumbnailOrItem() },
                contentAlignment = Alignment.Center
            ) {
                if (!item.photoUri.isNullOrBlank()) {
                    AsyncImage(
                        model = item.photoUri,
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Edit photo",
                        tint = PrimaryTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Center: Item Name (16.sp, #E3E3E3, single line with TextOverflow.Ellipsis) & Subtitle
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
                    .clickable { onEditThumbnailOrItem() }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = WarmGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = item.name,
                        fontSize = 16.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$displayCalories kcal · ${displayProtein}g protein",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Right: Compact quantity stepper (- 1x +) in #282A2C with haptic tick
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = SurfaceContainerHigh
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { onStepMultiplier(-1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease quantity",
                            tint = TextPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Text(
                        text = multText,
                        fontSize = 13.sp,
                        color = PrimaryTeal,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .clickable { onStepMultiplier(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase quantity",
                            tint = PrimaryTeal,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultItemEditorDialog(
    initialItem: VaultFoodItemEntity,
    isNew: Boolean,
    haptics: HapticsManager?,
    onDismiss: () -> Unit,
    onSave: (VaultFoodItemEntity) -> Unit,
    onDelete: (VaultFoodItemEntity) -> Unit
) {
    val context = LocalContext.current
    var name by remember(initialItem) { mutableStateOf(initialItem.name) }
    var calories by remember(initialItem) { mutableStateOf(initialItem.caloriesPerServing.toString()) }
    var protein by remember(initialItem) { mutableStateOf(initialItem.proteinGramsPerServing.toString()) }
    var servingDesc by remember(initialItem) { mutableStateOf(initialItem.servingDescription) }
    var category by remember(initialItem) { mutableStateOf(initialItem.category) }
    var isPinned by remember(initialItem) { mutableStateOf(initialItem.isPinned) }
    var photoUri by remember(initialItem) { mutableStateOf(initialItem.photoUri) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            photoUri = uri.toString()
        }
    }

    val cameraThumbLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            runCatching {
                val dir = File(context.filesDir, "vault_photos").apply { mkdirs() }
                val file = File(dir, "thumb_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                photoUri = file.toURI().toString()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceCard,
        title = {
            Text(
                text = if (isNew) "Add custom food" else "Edit food item",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceContainerHigh)
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!photoUri.isNullOrBlank()) {
                            AsyncImage(
                                model = photoUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = PrimaryTeal)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            onClick = {
                                haptics?.tick()
                                galleryLauncher.launch("image/*")
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = PrimaryTeal, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gallery photo", fontSize = 12.sp, color = TextPrimary)
                            }
                        }

                        Surface(
                            onClick = {
                                haptics?.tick()
                                cameraThumbLauncher.launch(null)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = SecondaryBlue, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Take photo", fontSize = 12.sp, color = TextPrimary)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = calories,
                        onValueChange = { calories = it },
                        label = { Text("Calories (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("Protein (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = servingDesc,
                    onValueChange = { servingDesc = it },
                    label = { Text("Serving size") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Category", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FoodCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.displayName) },
                            border = null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryTealTrack,
                                selectedLabelColor = PrimaryTeal,
                                containerColor = SurfaceContainerHigh,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pin to top", color = TextPrimary)
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryTeal)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        haptics?.tick()
                        onSave(
                            initialItem.copy(
                                name = name.trim(),
                                category = category,
                                caloriesPerServing = (calories.toIntOrNull() ?: 200).coerceAtLeast(0),
                                proteinGramsPerServing = (protein.toFloatOrNull() ?: 10f).coerceAtLeast(0f),
                                servingDescription = servingDesc.ifBlank { "1 serving" },
                                photoUri = photoUri,
                                isPinned = isPinned
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryTeal,
                    contentColor = OnPrimaryTeal
                )
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                if (!isNew) {
                    TextButton(onClick = { onDelete(initialItem) }) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MuscleHotCoral, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = MuscleHotCoral)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    )
}

/**
 * Hand-Scaled AI Camera Modal (CameraX)
 */
@Composable
private fun HandScaledAiCameraModal(
    activeModelTier: AiModelTier,
    isAnalyzing: Boolean,
    haptics: HapticsManager?,
    onSwitchModelTier: (AiModelTier) -> Unit,
    onCaptureOrAnalyzeBitmap: (Bitmap?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = try {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            } catch (_: Throwable) {
                null
            }
            onCaptureOrAnalyzeBitmap(bmp)
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundCanvas)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.surfaceProvider = previewView.surfaceProvider
                                }
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageCapture
                                )
                            } catch (_: Throwable) {
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Camera access for meal scanning",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Take a photo of your meal or choose one from your gallery.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryTeal,
                            contentColor = OnPrimaryTeal
                        )
                    ) {
                        Text("Allow camera", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Top Overlay: Fast vs Deep Pill Toggle + Hand Calibration Guide
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(BackgroundCanvas.copy(alpha = 0.92f), BackgroundCanvas.copy(alpha = 0.0f))
                        )
                    )
                    .statusBarsPadding()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SurfaceContainerHigh
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            AiModelTier.entries.forEach { tier ->
                                val selected = tier == activeModelTier
                                val isFast = tier == AiModelTier.GEMINI_3_8_FLASH
                                val pillBg = when {
                                    selected && isFast -> PrimaryTeal
                                    selected && !isFast -> SecondaryBlue
                                    else -> SurfaceContainerHigh
                                }
                                val pillText = if (selected) OnPrimaryTeal else TextSecondary
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(pillBg)
                                        .clickable {
                                            haptics?.tick()
                                            onSwitchModelTier(tier)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = if (isFast) "Fast" else "Deep",
                                        fontSize = 13.sp,
                                        color = pillText,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PanTool,
                            contentDescription = null,
                            tint = SecondaryBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Place your hand flat next to the plate for automatic portion scaling.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            // Bottom Capture Controls
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(BackgroundCanvas.copy(alpha = 0.92f))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(color = PrimaryTeal)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Analyzing meal...",
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryTeal
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                haptics?.tick()
                                galleryPickerLauncher.launch("image/*")
                            },
                            shape = RoundedCornerShape(24.dp),
                            color = SurfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gallery", color = TextPrimary, fontWeight = FontWeight.Medium)
                            }
                        }

                        // Shutter Capture Button
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(PrimaryTeal)
                                .clickable {
                                    haptics?.setComplete()
                                    if (hasCameraPermission) {
                                        try {
                                            imageCapture.takePicture(
                                                ContextCompat.getMainExecutor(context),
                                                object : ImageCapture.OnImageCapturedCallback() {
                                                    override fun onCaptureSuccess(image: ImageProxy) {
                                                        val bitmap = image.toBitmap()
                                                        image.close()
                                                        onCaptureOrAnalyzeBitmap(bitmap)
                                                    }

                                                    override fun onError(exception: ImageCaptureException) {
                                                        onCaptureOrAnalyzeBitmap(null)
                                                    }
                                                }
                                            )
                                        } catch (_: Throwable) {
                                            onCaptureOrAnalyzeBitmap(null)
                                        }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.CAMERA)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Capture meal",
                                tint = OnPrimaryTeal,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Surface(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(24.dp),
                            color = SurfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Cancel", color = TextSecondary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * AI Camera Confirmation Sheet (Zero Text Collisions & Zero Cutoffs):
 * - Header: Dish Name on left (Modifier.weight(1f).padding(end = 12.dp)), #1C2E2B pill on right showing total kcal · g P.
 *   Subtitle underneath: "✋ Hand size calibrated" (only if handScaleDetected == true).
 * - Detected Items List: Checkbox + Item Name + Grams Column has Modifier.weight(1f).padding(end = 12.dp)
 *   and Calories/Protein sits in a right-aligned Column so text NEVER collides.
 * - Portion Size Selector: Full-width 3-segment row (Small 0.75x | Normal 1.0x | Large 1.25x) with Modifier.weight(1f).
 * - Cooking Oil / Ghee / Sauce Selector: Full-width 3-segment row with Modifier.weight(1f) and concise labels
 *   (Light (+0) | Normal (+50) | Heavy (+150)).
 * - Save to Vault Toggle: "Save to Food Vault", 5 category pills in FlowRow, "Pin to top", and "Log meal" button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun VisionPostPhotoConfirmationSheet(
    result: VisionFoodAnalysisResult,
    defaultCategory: FoodCategory,
    haptics: HapticsManager?,
    onConfirmLog: (
        dishName: String,
        category: FoodCategory,
        totalCalories: Int,
        totalProteinGrams: Float,
        saveToVault: Boolean,
        pinToTop: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var itemsState by remember(result) { mutableStateOf(result.detectedItems) }
    var portionMultiplier by remember { mutableStateOf(PortionMultiplier.NORMAL) }
    var hiddenFat by remember { mutableStateOf(HiddenFatAddition.LIGHT) }
    var targetCategory by remember { mutableStateOf(defaultCategory) }
    var saveToVault by remember { mutableStateOf(true) }
    var pinToTop by remember { mutableStateOf(true) }

    val baseCalories = itemsState.filter { it.selected }.sumOf { it.calories }
    val baseProtein = itemsState.filter { it.selected }.sumOf { it.proteinGrams.toDouble() }.toFloat()

    val finalCalories = (baseCalories * portionMultiplier.factor).roundToInt() + hiddenFat.extraCalories
    val finalProtein = baseProtein * portionMultiplier.factor

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceCard,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header: Dish Name on left + #1C2E2B total pill on right
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = result.dishName,
                        fontSize = 20.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PrimaryTealTrack
                    ) {
                        Text(
                            text = "$finalCalories kcal · ${finalProtein.roundToInt()}g P",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 13.sp,
                            color = PrimaryTeal,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (result.handScaleDetected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "✋ Hand size calibrated",
                        fontSize = 13.sp,
                        color = PrimaryTeal
                    )
                }
            }

            // Detected Items List (Zero Collision Layout)
            Text(
                text = "Detected items",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )

            itemsState.forEachIndexed { index, detected ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainerHigh)
                        .clickable {
                            haptics?.tick()
                            itemsState = itemsState.toMutableList().also {
                                it[index] = detected.copy(selected = !detected.selected)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Checkbox(
                            checked = detected.selected,
                            onCheckedChange = { checked ->
                                haptics?.tick()
                                itemsState = itemsState.toMutableList().also {
                                    it[index] = detected.copy(selected = checked)
                                }
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PrimaryTeal,
                                checkmarkColor = OnPrimaryTeal
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = detected.name,
                                fontSize = 15.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "~${detected.estimatedGrams}g",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${detected.calories} kcal",
                            fontSize = 14.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${detected.proteinGrams.roundToInt()}g P",
                            fontSize = 12.sp,
                            color = PrimaryTeal
                        )
                    }
                }
            }

            // Portion Size Selector: Full-width 3-segment row (Small 0.75x | Normal 1.0x | Large 1.25x)
            Text(
                text = "Portion size",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val portionLabels = listOf(
                    PortionMultiplier.SMALL to "Small 0.75x",
                    PortionMultiplier.NORMAL to "Normal 1.0x",
                    PortionMultiplier.LARGE to "Large 1.25x"
                )
                portionLabels.forEach { (option, conciseLabel) ->
                    val selected = portionMultiplier == option
                    Surface(
                        onClick = {
                            haptics?.tick()
                            portionMultiplier = option
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) PrimaryTealTrack else SurfaceContainerHigh
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = conciseLabel,
                                fontSize = 12.sp,
                                color = if (selected) PrimaryTeal else TextSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Cooking Oil / Ghee / Sauce Selector (Full-width 3-segment row with concise labels)
            Text(
                text = "Cooking oil, ghee or sauce",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val fatLabels = listOf(
                    HiddenFatAddition.LIGHT to "Light (+0)",
                    HiddenFatAddition.NORMAL to "Normal (+50)",
                    HiddenFatAddition.HEAVY to "Heavy (+150)"
                )
                fatLabels.forEach { (fatOption, conciseLabel) ->
                    val selected = hiddenFat == fatOption
                    Surface(
                        onClick = {
                            haptics?.tick()
                            hiddenFat = fatOption
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) WarmAmberTrack else SurfaceContainerHigh
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = conciseLabel,
                                fontSize = 12.sp,
                                color = if (selected) WarmGold else TextSecondary,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Save to Vault Toggle
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = BackgroundCanvas),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Save to Food Vault",
                            fontSize = 15.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Switch(
                            checked = saveToVault,
                            onCheckedChange = {
                                haptics?.tick()
                                saveToVault = it
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryTeal)
                        )
                    }

                    if (saveToVault) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FoodCategory.entries.forEach { cat ->
                                val selected = targetCategory == cat
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        haptics?.tick()
                                        targetCategory = cat
                                    },
                                    label = { Text(cat.displayName) },
                                    border = null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PrimaryTealTrack,
                                        selectedLabelColor = PrimaryTeal,
                                        containerColor = SurfaceContainerHigh,
                                        labelColor = TextSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Pin to top", fontSize = 14.sp, color = TextSecondary)
                            Switch(
                                checked = pinToTop,
                                onCheckedChange = {
                                    haptics?.tick()
                                    pinToTop = it
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = WarmGold)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onConfirmLog(
                        result.dishName,
                        targetCategory,
                        finalCalories,
                        finalProtein,
                        saveToVault,
                        pinToTop
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryTeal,
                    contentColor = OnPrimaryTeal
                )
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Log meal",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
