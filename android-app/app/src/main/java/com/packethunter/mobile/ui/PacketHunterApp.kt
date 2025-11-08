package com.packethunter.mobile.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.packethunter.mobile.ui.screens.*
import com.packethunter.mobile.ui.theme.*
import com.packethunter.mobile.ui.formatBytes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacketHunterApp(
    viewModel: MainViewModel,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onExit: () -> Unit,
  onExportFilterPresets: () -> Unit,
        onImportFilterPresets: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val packets by viewModel.packets.collectAsState() // Observe packets Flow
    
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    // Show consent dialog first
    if (!uiState.consentGiven) {
        ConsentDialog(
            onAccept = {
                viewModel.setConsentGiven(true)
            },
            onDecline = {
                onExit()
            }
        )
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                currentScreen = uiState.currentScreen,
                onScreenSelected = { screen ->
                    viewModel.setCurrentScreen(screen)
                    scope.launch { drawerState.close() }
                },
                alertCount = alerts.size
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { 
                        Text(
                            getScreenTitle(uiState.currentScreen),
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen
                        ) 
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BackgroundBlack
                    ),
                    navigationIcon = {
                        IconButton(onClick = { 
                            scope.launch { drawerState.open() } 
                        }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = NeonGreen
                            )
                        }
                    },
                    actions = {
                        // Capture toggle
                        if (uiState.isCapturing) {
                            IconButton(onClick = onStopCapture) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = AlertRed
                                )
                            }
                        } else {
                            IconButton(onClick = onStartCapture) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Start",
                                    tint = NeonGreen
                                )
                            }
                        }
                        
                        // Alert badge
                        if (alerts.isNotEmpty()) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = AlertRed) { 
                                        Text("${alerts.size}") 
                                    }
                                }
                            ) {
                                IconButton(onClick = {
                                    viewModel.setCurrentScreen(Screen.Alerts)
                                    scope.launch { drawerState.close() }
                                }) {
                                    Icon(
                                        Icons.Default.Warning, 
                                        "Alerts",
                                        tint = AlertRed
                                    )
                                }
                            }
                        }
                    }
                )
            }
        ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (uiState.currentScreen) {
                Screen.Dashboard -> DashboardScreen(
                    stats = uiState.stats,
                    isCapturing = uiState.isCapturing,
                    appTalkers = uiState.appTalkers
                )
                Screen.Packets -> {
                    val filteredResult = viewModel.getFilteredResult(packets)
                    PacketFilterScreen(
                        packets = packets,
                        filteredResult = filteredResult,
                        activeFilters = uiState.activeFilters,
                        filterPresets = viewModel.filterPresets.value,
                        onPacketClick = { 
                            viewModel.setSelectedPacket(it)
                            viewModel.getConnectionPackets(it)
                        },
                        onToggleSecurityFilter = { viewModel.toggleSecurityFilter(it) },
                        onAddAdvancedRule = { viewModel.addAdvancedRule(it) },
                        onRemoveAdvancedRule = { viewModel.removeAdvancedRule(it) },
                        onClearFilters = { viewModel.clearAllFilters() },
                        onSavePreset = { viewModel.saveFilterPreset(it) },
                        onLoadPreset = { viewModel.loadFilterPreset(it) },
                        onDeletePreset = { viewModel.deleteFilterPreset(it) },
                        onExportFilterPresets = onExportFilterPresets,
                        onImportFilterPresets = onImportFilterPresets,
                        selectedPacket = uiState.selectedPacket
                    )
                }
                Screen.AppTalkers -> AppTalkersScreen(
                    packets = viewModel.getFilteredPackets(packets),
                    onPacketClick = { 
                        viewModel.setSelectedPacket(it)
                        viewModel.getConnectionPackets(it)
                    }
                )
                Screen.Alerts -> AlertsScreen(
                    alerts = alerts,
                    onAcknowledge = { viewModel.acknowledgeAlert(it) }
                )
                Screen.Rules -> RulesScreen(
                    rules = viewModel.rules.collectAsState().value,
                    onAddRule = { viewModel.addRule(it) },
                    onUpdateRule = { viewModel.updateRule(it) },
                    onDeleteRule = { viewModel.deleteRule(it) }
                )
                Screen.Export -> ExportScreen(
                    isExporting = uiState.isExporting,
                    exportResult = uiState.exportResult,
                    onExportPcap = { viewModel.exportPcap() },
                    onExportJson = { viewModel.exportJson() },
                    onExportBundle = { viewModel.exportBundle() },
                    onClearResult = { viewModel.clearExportResult() }
                )
                Screen.Map -> MapScreen(
                    talkers = uiState.stats.topTalkers
                )
            }
        }
        }
        
        // Packet detail screen
        uiState.selectedPacket?.let { selectedPacket ->
            if (uiState.connectionPackets.isNotEmpty()) {
                // Show connection details if available
                ConnectionDetailsScreen(
                    connectionPackets = uiState.connectionPackets,
                    onBack = { 
                        viewModel.setSelectedPacket(null)
                        viewModel.setConnectionPackets(emptyList())
                    },
                    onPacketClick = { packet ->
                        viewModel.setSelectedPacket(packet)
                        viewModel.getConnectionPackets(packet)
                    }
                )
            } else {
                // Show individual packet details
                PacketDetailScreen(
                    packet = selectedPacket,
                    onBack = { viewModel.setSelectedPacket(null) }
                )
            }
        }
    }
}

@Composable
fun NavigationDrawerContent(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    alertCount: Int
) {
    ModalDrawerSheet(
        drawerContainerColor = SurfaceGray,
        modifier = Modifier.width(280.dp)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BackgroundBlack)
                .padding(24.dp)
        ) {
            Icon(
                Icons.Default.Security,
                contentDescription = null,
                tint = NeonGreen,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "🎯 Packet Hunter",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = NeonGreen
            )
            Text(
                "Network Analysis Tool",
                style = MaterialTheme.typography.bodySmall,
                color = TextGray
            )
        }
        
        Divider(color = BorderGray)
        
        // Menu items
        val menuItems = listOf(
            MenuItem(Screen.Dashboard, "Dashboard", Icons.Default.Dashboard),
            MenuItem(Screen.Packets, "Packet List", Icons.Default.List),
            MenuItem(Screen.AppTalkers, "App Usage", Icons.Default.Apps),
            MenuItem(Screen.Map, "Geo Map", Icons.Default.Map),
            MenuItem(Screen.Alerts, "Alerts", Icons.Default.Warning, alertCount),
            MenuItem(Screen.Rules, "Detection Rules", Icons.Default.Rule),
            MenuItem(Screen.Export, "Export Data", Icons.Default.FileDownload)
        )
        
        menuItems.forEach { item ->
            NavigationDrawerItem(
                label = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(item.title, color = if (currentScreen == item.screen) NeonGreen else TextGray)
                        if (item.badgeCount > 0) {
                            Badge(containerColor = AlertRed) {
                                Text("${item.badgeCount}")
                            }
                        }
                    }
                },
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = item.title,
                        tint = if (currentScreen == item.screen) NeonGreen else TextGray
                    )
                },
                selected = currentScreen == item.screen,
                onClick = { onScreenSelected(item.screen) },
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = SurfaceGrayDark,
                    unselectedContainerColor = SurfaceGray
                ),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

data class MenuItem(
    val screen: Screen,
    val title: String,
    val icon: ImageVector,
    val badgeCount: Int = 0
)

fun getScreenTitle(screen: Screen): String {
    return when (screen) {
        Screen.Dashboard -> "🎯 Dashboard"
        Screen.Packets -> "📦 Packets"
        Screen.AppTalkers -> "📱 App Usage"
        Screen.Map -> "🗺️ Geo Map"
        Screen.Alerts -> "⚠️ Alerts"
        Screen.Rules -> "🔐 Rules"
        Screen.Export -> "💾 Export"
    }
}

@Composable
fun ConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("⚠️ Legal Notice") },
        text = {
            Text(
                "By using this app, you confirm that you have legal permission to " +
                        "capture and analyze network traffic on this device and network.\n\n" +
                        "Unauthorized network monitoring may be illegal in your jurisdiction.\n\n" +
                        "This tool is intended for security professionals, researchers, and " +
                        "authorized network administrators only."
            )
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("I Understand and Accept")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("Decline")
            }
        }
    )
}

@Composable
fun AppDetailsDialog(app: com.packethunter.mobile.data.AppTalker, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceGray,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Apps,
                    contentDescription = null,
                    tint = NeonGreen,
                    modifier = Modifier.size(32.dp)
                )
                Column {
                    Text(
                        app.appName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = NeonGreen
                    )
                    Text(
                        app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextGray
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Data usage
                DetailRow("Total Data", formatBytes(app.totalBytes), CyberCyan)
                DetailRow("Sent", formatBytes(app.sentBytes), NeonGreen)
                DetailRow("Received", formatBytes(app.receivedBytes), CyberBlue)
                
                Divider(color = BorderGray)
                
                // Packet stats
                DetailRow("Sent Packets", app.sentPackets.toString(), NeonGreen)
                DetailRow("Received Packets", app.receivedPackets.toString(), CyberBlue)
                DetailRow("Total Packets", (app.sentPackets + app.receivedPackets).toString(), NeonPurple)
                
                Divider(color = BorderGray)
                
                // Network stats
                DetailRow("Remote Hosts", app.uniqueRemoteHosts.toString(), AlertOrange)
                DetailRow("Protocols", app.protocols.joinToString(", "), CyberCyan)
                
                // Packets per second (estimate)
                val pps = if (app.sentPackets + app.receivedPackets > 0) {
                    String.format("%.1f pkt/s", (app.sentPackets + app.receivedPackets) / 60.0)
                } else "0 pkt/s"
                DetailRow("Rate (est.)", pps, NeonGreen)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CyberBlue)
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
fun DetailRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextGray
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
