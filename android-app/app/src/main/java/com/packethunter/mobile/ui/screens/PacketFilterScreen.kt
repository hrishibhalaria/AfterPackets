package com.packethunter.mobile.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.packethunter.mobile.data.*
import com.packethunter.mobile.filtering.*
import com.packethunter.mobile.ui.theme.*
import com.packethunter.mobile.ui.utils.HexUtils
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Enhanced Packet List Screen with comprehensive filtering
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacketFilterScreen(
    packets: List<PacketInfo>,
    filteredResult: FilteredResult,
    activeFilters: ActiveFilters,
    filterPresets: List<FilterPreset>,
    onPacketClick: (PacketInfo) -> Unit,
    onToggleSecurityFilter: (SecurityFilter) -> Unit,
    onAddAdvancedRule: (FilterRule) -> Unit,
    onRemoveAdvancedRule: (FilterRule) -> Unit,
    onClearFilters: () -> Unit,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (FilterPreset) -> Unit,
    onDeletePreset: (FilterPreset) -> Unit,
    onExportFilterPresets: () -> Unit,
    onImportFilterPresets: () -> Unit,
    selectedPacket: PacketInfo?
) {
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var showLoadPresetDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedField by remember { mutableStateOf(FilterField.PROTOCOL) }
    
    // Use LazyColumn for the entire screen to enable scrolling
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Filter Header with Stats
        item {
            FilterHeaderCard(
                totalCaptured = filteredResult.totalCaptured,
                totalFiltered = filteredResult.totalFiltered,
                activeFilters = activeFilters,
                onClearFilters = onClearFilters
            )
        }
        
        // Predefined Security Filters
        item {
            SecurityFilterChips(
                activeFilters = activeFilters.securityFilters,
                onToggleFilter = onToggleSecurityFilter
            )
        }
        
        // Advanced Filtering Controls
        item {
            AdvancedFilterControls(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                selectedField = selectedField,
                onFieldSelected = { selectedField = it },
                onSearch = {
                    if (searchQuery.isNotEmpty()) {
                        val rule = FilterRule(
                            field = selectedField,
                            operator = FilterOperator.CONTAINS,
                            value = searchQuery
                        )
                        onAddAdvancedRule(rule)
                        searchQuery = ""
                    }
                },
                onClear = onClearFilters
            )
        }
        
        // Preset Management Buttons
        item {
            PresetManagementButtons(
                onSaveClick = { showSavePresetDialog = true },
                onLoadClick = { showLoadPresetDialog = true },
                onExportClick = onExportFilterPresets,
                onImportClick = onImportFilterPresets
            )
        }
        
        // Suspicious Alerts Banner
        if (filteredResult.suspiciousAlerts.isNotEmpty()) {
            item {
                SuspiciousAlertsBanner(alerts = filteredResult.suspiciousAlerts)
            }
        }
        
        // Packet List
        if (filteredResult.filteredPackets.isEmpty()) {
            item {
                EmptyFilterState()
            }
        } else {
            items(
                items = filteredResult.filteredPackets,
                key = { it.id }
            ) { packet ->
                val isSuspicious = filteredResult.suspiciousAlerts.any { it.packetId == packet.id }
                PacketCard(
                    packet = packet,
                    onClick = { onPacketClick(packet) },
                    isSelected = packet == selectedPacket,
                    isSuspicious = isSuspicious,
                    suspiciousAlert = filteredResult.suspiciousAlerts.firstOrNull { it.packetId == packet.id }
                )
            }
        }
    }
    
    // Save Preset Dialog
    if (showSavePresetDialog) {
        SavePresetDialog(
            onDismiss = { showSavePresetDialog = false },
            onSave = { name ->
                onSavePreset(name)
                showSavePresetDialog = false
            }
        )
    }
    
    // Load Preset Dialog
    if (showLoadPresetDialog) {
        LoadPresetDialog(
            presets = filterPresets,
            onDismiss = { showLoadPresetDialog = false },
            onLoad = { preset ->
                onLoadPreset(preset)
                showLoadPresetDialog = false
            },
            onDelete = { preset ->
                onDeletePreset(preset)
            }
        )
    }
}

@Composable
fun FilterHeaderCard(
    totalCaptured: Int,
    totalFiltered: Int,
    activeFilters: ActiveFilters,
    onClearFilters: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "📦 Captured Packets",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Filtered: $totalFiltered / $totalCaptured",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (totalFiltered < totalCaptured) CyberCyan else TextGray
                    )
                }
                Text(
                    "$totalFiltered",
                    style = MaterialTheme.typography.headlineMedium,
                    color = CyberCyan,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Active Filter Chips
            if (activeFilters.securityFilters.isNotEmpty() || activeFilters.advancedRules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Active Filters:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextGray
                    )
                    
                    // Security filter chips
                    activeFilters.securityFilters.forEach { filter ->
                        FilterChip(
                            label = { Text(getFilterLabel(filter), style = MaterialTheme.typography.labelSmall) },
                            selected = true,
                            onClick = { },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberBlue,
                                selectedLabelColor = Color.Black
                            ),
                            modifier = Modifier.clickable { onClearFilters() }
                        )
                    }
                    
                    // Advanced rule chips
                    activeFilters.advancedRules.forEach { rule ->
                        FilterChip(
                            label = { 
                                Text(
                                    "${getFieldLabel(rule.field)}: ${rule.value}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            selected = true,
                            onClick = { },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonPurple,
                                selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityFilterChips(
    activeFilters: Set<SecurityFilter>,
    onToggleFilter: (SecurityFilter) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceGray)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                "🔒 Security Filters",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = NeonGreen
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Compact row for filter chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecurityFilter.values().filter { it != SecurityFilter.ALL }.forEach { filter ->
                    FilterChip(
                        label = { 
                            Text(
                                getFilterLabel(filter),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        selected = activeFilters.contains(filter),
                        onClick = { onToggleFilter(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberBlue,
                            selectedLabelColor = Color.White,
                            containerColor = SurfaceGrayLight,
                            labelColor = TextWhite
                        ),
                        modifier = Modifier.height(40.dp),
                        leadingIcon = if (activeFilters.contains(filter)) {
                            { 
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun AdvancedFilterControls(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedField: FilterField,
    onFieldSelected: (FilterField) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "🔍 Advanced Filtering",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Field dropdown
                var expanded by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = getFieldLabel(selectedField),
                            onValueChange = { },
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextGray
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            FilterField.values().forEach { field ->
                                DropdownMenuItem(
                                    text = { Text(getFieldLabel(field)) },
                                    onClick = {
                                        onFieldSelected(field)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Search button
                Button(
                    onClick = onSearch,
                    enabled = searchQuery.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
                
                // Clear button
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = AlertRed)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Search text field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { 
                    Text(
                        "Search by ${getFieldLabel(selectedField).lowercase()} (e.g. ${getFieldExample(selectedField)})",
                        color = TextGray
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = CyberCyan)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextGray,
                    focusedBorderColor = CyberCyan,
                    unfocusedBorderColor = BorderGray
                ),
                singleLine = true
            )
        }
    }
}

@Composable
fun PresetManagementButtons(
    onSaveClick: () -> Unit,
    onLoadClick: () -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceGray),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "💾 Filter Presets",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CyberCyan
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Save button - Enhanced visibility
                Button(
                    onClick = onSaveClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Save",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Save Filters",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Load button - Enhanced visibility
                Button(
                    onClick = onLoadClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = ButtonDefaults.buttonElevation(8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = "Load",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Load Filters",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            // Export/Import buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Export button
                OutlinedButton(
                    onClick = onExportClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CyberCyan
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = 2.dp,
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(CyberCyan, NeonPurple)
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Export",
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Export",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                // Import button
                OutlinedButton(
                    onClick = onImportClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeonGreen
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = 2.dp,
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = listOf(NeonGreen, CyberBlue)
                        )
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Import",
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Import",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Text(
                "Save your current filter configuration or load a previously saved preset",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SuspiciousAlertsBanner(alerts: List<SuspiciousTrafficAlert>) {
    val severityColor = when (alerts.firstOrNull()?.severity) {
        "high" -> AlertRed
        "medium" -> AlertOrange
        else -> CyberCyan
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = severityColor.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = severityColor,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "⚠️ ${alerts.size} Suspicious Traffic Alert${if (alerts.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = severityColor
                )
                Text(
                    alerts.firstOrNull()?.message ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextWhite
                )
            }
        }
    }
}

@Composable
fun PacketCard(
    packet: PacketInfo,
    onClick: () -> Unit,
    isSelected: Boolean,
    isSuspicious: Boolean = false,
    suspiciousAlert: SuspiciousTrafficAlert? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
            .then(
                if (isSuspicious) {
                    Modifier.border(
                        width = 2.dp,
                        color = when (suspiciousAlert?.severity) {
                            "high" -> AlertRed
                            "medium" -> AlertOrange
                            else -> CyberCyan
                        },
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SurfaceGrayLight else SurfaceGray
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        packet.protocol,
                        style = MaterialTheme.typography.labelLarge,
                        color = getProtocolColorFilter(packet.protocol),
                        fontWeight = FontWeight.Bold
                    )
                    if (isSuspicious) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Suspicious",
                            tint = when (suspiciousAlert?.severity) {
                                "high" -> AlertRed
                                "medium" -> AlertOrange
                                else -> CyberCyan
                            },
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    formatTimestampFilter(packet.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Connection info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    packet.sourceIp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { onClick() }
                )
                Text(":", color = TextWhite, fontFamily = FontFamily.Monospace)
                Text(
                    packet.sourcePort.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextWhite,
                    fontFamily = FontFamily.Monospace
                )
                Text(" → ", color = TextWhite)
                Text(
                    packet.destIp,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CyberCyan,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.clickable { onClick() }
                )
                Text(":", color = TextWhite, fontFamily = FontFamily.Monospace)
                Text(
                    packet.destPort.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextWhite,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            // Suspicious alert message
            if (isSuspicious && suspiciousAlert != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = when (suspiciousAlert.severity) {
                            "high" -> AlertRed.copy(alpha = 0.2f)
                            "medium" -> AlertOrange.copy(alpha = 0.2f)
                            else -> CyberCyan.copy(alpha = 0.2f)
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            suspiciousAlert.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = when (suspiciousAlert.severity) {
                                "high" -> AlertRed
                                "medium" -> AlertOrange
                                else -> CyberCyan
                            },
                            fontWeight = FontWeight.Bold
                        )
                        if (suspiciousAlert.recommendation != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "💡 ${suspiciousAlert.recommendation}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextGray
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Payload preview
            if (packet.payload != null && packet.payload.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BackgroundBlack)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            "Payload Preview:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        val previewSize = minOf(32, packet.payload.size)
                        val previewBytes = packet.payload.sliceArray(0 until previewSize)
                        Text(
                            HexUtils.bytesToHex(previewBytes),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = CyberCyan,
                            maxLines = 2
                        )
                    }
                }
            }
            
            // Flags and length
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (packet.flags.isNotEmpty()) {
                    Text(
                        packet.flags,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberBlue
                    )
                }
                Text(
                    "${packet.length} bytes",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextGray
                )
            }
        }
    }
}

@Composable
fun EmptyFilterState() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceGray)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.FilterList,
                contentDescription = null,
                tint = TextGray,
                modifier = Modifier.size(64.dp)
            )
            Text(
                "No packets match the current filters",
                style = MaterialTheme.typography.titleMedium,
                color = TextGray
            )
            Text(
                "Try adjusting your filter criteria",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }
    }
}

@Composable
fun SavePresetDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var presetName by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("💾 Save Filter Preset", fontWeight = FontWeight.Bold)
        },
        text = {
            OutlinedTextField(
                value = presetName,
                onValueChange = { presetName = it },
                label = { Text("Preset Name") },
                placeholder = { Text("e.g., HTTP Traffic Only") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { onSave(presetName) },
                enabled = presetName.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = SurfaceGray
    )
}

@Composable
fun LoadPresetDialog(
    presets: List<FilterPreset>,
    onDismiss: () -> Unit,
    onLoad: (FilterPreset) -> Unit,
    onDelete: (FilterPreset) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("📂 Load Filter Preset", fontWeight = FontWeight.Bold)
        },
        text = {
            if (presets.isEmpty()) {
                Text("No saved presets available", color = TextGray)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presets) { preset ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLoad(preset) },
                            colors = CardDefaults.cardColors(containerColor = BackgroundBlack)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        preset.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite
                                    )
                                    Text(
                                        "Last used: ${formatPresetDate(preset.lastUsed)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                }
                                IconButton(onClick = { onDelete(preset) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = AlertRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = SurfaceGray
    )
}

@Composable
fun ManagePresetsDialog(
    presets: List<FilterPreset>,
    onDismiss: () -> Unit,
    onDelete: (FilterPreset) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("⚙️ Manage Presets", fontWeight = FontWeight.Bold)
        },
        text = {
            if (presets.isEmpty()) {
                Text("No saved presets", color = TextGray)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presets) { preset ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = BackgroundBlack)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        preset.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextWhite
                                    )
                                    Text(
                                        "Created: ${formatPresetDate(preset.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextGray
                                    )
                                }
                                IconButton(onClick = { onDelete(preset) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = AlertRed
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = SurfaceGray
    )
}

// Helper functions
fun getFilterLabel(filter: SecurityFilter): String {
    return when (filter) {
        SecurityFilter.HTTP -> "HTTP"
        SecurityFilter.HTTPS_TLS -> "HTTPS/TLS"
        SecurityFilter.DNS -> "DNS"
        SecurityFilter.ICMP -> "ICMP"
        SecurityFilter.SUSPICIOUS_PORTS -> "Suspicious Ports"
        SecurityFilter.LARGE_OUTBOUND -> "Large Outbound"
        SecurityFilter.ALL -> "All"
    }
}

fun getFieldLabel(field: FilterField): String {
    return when (field) {
        FilterField.SOURCE_IP -> "Source IP"
        FilterField.DESTINATION_IP -> "Destination IP"
        FilterField.SOURCE_PORT -> "Source Port"
        FilterField.DESTINATION_PORT -> "Destination Port"
        FilterField.PROTOCOL -> "Protocol"
    }
}

fun getFieldExample(field: FilterField): String {
    return when (field) {
        FilterField.SOURCE_IP -> "192.168.1.1"
        FilterField.DESTINATION_IP -> "8.8.8.8"
        FilterField.SOURCE_PORT -> "8080"
        FilterField.DESTINATION_PORT -> "443"
        FilterField.PROTOCOL -> "HTTP"
    }
}

fun getProtocolColorFilter(protocol: String): Color {
    return when (protocol.uppercase()) {
        "TCP" -> ProtocolTCP
        "UDP" -> ProtocolUDP
        "ICMP" -> ProtocolICMP
        "HTTP" -> ProtocolHTTP
        "HTTPS" -> ProtocolHTTPS
        "DNS" -> ProtocolDNS
        else -> TextGray
    }
}

fun formatPresetDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd/yy HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatTimestampFilter(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

