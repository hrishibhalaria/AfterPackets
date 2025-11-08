package com.packethunter.mobile.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.packethunter.mobile.MainActivity
import com.packethunter.mobile.R
import com.packethunter.mobile.data.PacketDatabase
import com.packethunter.mobile.data.PacketInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * VPN Service for capturing network packets
 * Uses VpnService API to intercept all network traffic
 */
class PacketCaptureService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private lateinit var packetProcessor: PacketProcessor
    private val nativeParser = NativePacketParser()
    
    private var isRunning = false
    
    // Forwarding metrics
    private val forwardedPackets = java.util.concurrent.atomic.AtomicLong(0)
    private val forwardedBytes = java.util.concurrent.atomic.AtomicLong(0)
    private val forwardingStartTime = java.util.concurrent.atomic.AtomicLong(0)
    
    companion object {
        private const val TAG = "PacketCaptureService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "packet_capture_channel"
        const val ACTION_START = "com.packethunter.mobile.START_CAPTURE"
        const val ACTION_STOP = "com.packethunter.mobile.STOP_CAPTURE"
        
        // MTU and buffer sizes
        private const val MTU_SIZE = 1400 // Optimized to prevent fragmentation
        private const val BUFFER_SIZE = 32767
        
        // Static reference to service instance for accessing data
        @Volatile
        private var instance: PacketCaptureService? = null
        
        fun getInstance(): PacketCaptureService? = instance
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        
        // Set instance reference
        instance = this
        
        // Initialize native parser
        nativeParser.initParser()
        
        // Initialize packet processor with NetworkStatsTracker
        val database = PacketDatabase.getDatabase(applicationContext)
        val appTracker = AppTracker(applicationContext)
        val networkStatsTracker = NetworkStatsTracker(applicationContext)
        packetProcessor = PacketProcessor(database, nativeParser, appTracker, networkStatsTracker)
        
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START -> startCapture()
            ACTION_STOP -> stopCapture()
        }
        
        return START_STICKY
    }

    private fun startCapture() {
        if (isRunning) {
            Log.d(TAG, "Capture already running")
            return
        }
        
        Log.d(TAG, "Starting packet capture")
        
        // Show notification
        val notification = createNotification()
        
        // Start foreground with proper service type for Android 14+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        // Establish VPN interface
        vpnInterface = establishVpnInterface()
        
        if (vpnInterface == null) {
            Log.e(TAG, "Failed to establish VPN interface")
            stopSelf()
            return
        }
        
        // Verify VPN interface
        if (!VpnDiagnostics.verifyVpnInterface(vpnInterface)) {
            Log.e(TAG, "VPN interface verification failed")
            stopSelf()
            return
        }
        
        isRunning = true
        
        // Run diagnostic tests (non-blocking) after VPN stabilizes
        scope.launch {
            delay(2000) // Wait 2 seconds for VPN to stabilize
            Log.i(TAG, "Running VPN diagnostic tests...")
            VpnDiagnostics.testUdpForward(this@PacketCaptureService)
            VpnDiagnostics.testDnsResolution()
        }
        
        // Start packet processing
        packetProcessor.startProcessing()
        
        // Start capture loop
        captureJob = scope.launch {
            capturePackets()
        }
    }

    /**
     * Establish VPN interface with optimized routing and DNS configuration
     * 
     * Configuration:
     * - Address: 10.0.0.2/32 (single host)
     * - Route: 0.0.0.0/0 (all traffic)
     * - DNS: 1.1.1.1 (Cloudflare) and 8.8.8.8 (Google) for reliability
     * - MTU: 1400 to prevent fragmentation and improve performance
     */
    private fun establishVpnInterface(): ParcelFileDescriptor? {
        return try {
            Log.i(TAG, "=== VPN ESTABLISHMENT START ===")
            Log.i(TAG, "Establishing VPN: addr=10.0.0.2/32 route=0.0.0.0/0 dns=1.1.1.1,8.8.8.8 mtu=1400")
            
            val builder = Builder()
                .setSession("Mobile Packet Hunter")
                .addAddress("10.0.0.2", 32) // Single host address
                .addRoute("0.0.0.0", 0) // Route all traffic through VPN
                .addDnsServer("1.1.1.1") // Cloudflare DNS (primary)
                .addDnsServer("8.8.8.8") // Google DNS (fallback)
                .setMtu(1400) // Optimized MTU to prevent fragmentation
            
            // Non-blocking mode for better performance
            builder.setBlocking(false)
            
            // Allow apps to bypass VPN if needed (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.allowBypass()
                Log.d(TAG, "allowBypass() enabled for Android 10+")
            }
            
            Log.i(TAG, "Calling builder.establish()...")
            val vpnInterface = try {
                builder.establish()
            } catch (e: Exception) {
                Log.e(TAG, "VPN establish() threw exception", e)
                throw e
            }
            
            if (vpnInterface == null) {
                Log.e(TAG, "VPN interface establishment returned null")
                return null
            }
            
            Log.i(TAG, "VPN established successfully: pfd=$vpnInterface")
            Log.i(TAG, "VPN file descriptor: ${vpnInterface.fileDescriptor}")
            Log.i(TAG, "VPN Configuration: 10.0.0.2/32, MTU=1400, DNS=[1.1.1.1, 8.8.8.8]")
            Log.i(TAG, "=== VPN ESTABLISHMENT SUCCESS ===")
            
            vpnInterface
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception establishing VPN - permission denied", e)
            null
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Illegal state establishing VPN - another VPN may be active", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error establishing VPN", e)
            null
        }
    }

    /**
     * Capture and forward packets through VPN interface
     * 
     * This method implements zero-delay forwarding:
     * 1. Read packet from VPN TUN interface
     * 2. Forward packet immediately to network (no delay)
     * 3. Process packet asynchronously in background (fire-and-forget)
     * 
     * All forwarding happens in a dedicated high-priority thread to ensure
     * network traffic is never blocked.
     */
    private suspend fun capturePackets() {
        val vpn = vpnInterface ?: return
        
        val fd = vpn.fileDescriptor
        Log.i(TAG, "=== TUN READ/WRITE LOOP START ===")
        Log.i(TAG, "Opening TUN file descriptor: fd=$fd")
        
        val inputStream = FileInputStream(fd)
        val outputStream = FileOutputStream(fd)
        val buffer = ByteArray(BUFFER_SIZE)
        
        forwardingStartTime.set(System.currentTimeMillis())
        var totalRead = 0L
        var totalWritten = 0L
        var readCount = 0L
        var writeCount = 0L
        
        Log.i(TAG, "=== TUN READ/WRITE LOOP START ===")
        Log.i(TAG, "Opening TUN file descriptor: fd=$fd")
        Log.i(TAG, "Capture loop started - ZERO-DELAY forwarding enabled")
        Log.i(TAG, "TUN buffer size: $BUFFER_SIZE bytes")
        Log.d(TAG, "VPN forwarding metrics tracking enabled")
        
        // Use a dedicated thread for forwarding to ensure maximum priority
        val forwardingThread = Thread {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            Log.i(TAG, "Forwarding thread started with priority URGENT_AUDIO")
            
            try {
                while (isRunning) {
                    try {
                        val length = inputStream.read(buffer)
                        
                        if (length > 0) {
                            readCount++
                            totalRead += length
                            
                            // Log every 50KB read
                            if (totalRead % (1024 * 50) == 0L) {
                                Log.i(TAG, "TUN read: totalBytes=$totalRead lastRead=$length readCount=$readCount")
                            }
                            
                            // FORWARD IMMEDIATELY - NO DELAY, NO PROCESSING
                            // This ensures network traffic continues without any blocking
                            val written = try {
                                outputStream.write(buffer, 0, length)
                                outputStream.flush()
                                length // write() returns Unit, assume all written if no exception
                            } catch (e: Exception) {
                                Log.e(TAG, "TUN write failed for $length bytes", e)
                                0
                            }
                            
                            if (written > 0) {
                                writeCount++
                                totalWritten += written
                                
                                // Track forwarding metrics
                                forwardedPackets.incrementAndGet()
                                forwardedBytes.addAndGet(written.toLong())
                                
                                // Log every 100 packets
                                if (writeCount % 100 == 0L) {
                                    Log.d(TAG, "TUN forward: packets=$writeCount bytes=$totalWritten")
                                }
                                
                                // Copy packet for async processing (fire-and-forget)
                                val packetData = buffer.copyOfRange(0, length)
                                
                                // Process in background - don't wait, don't block
                                // Processing errors won't affect forwarding
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        packetProcessor.processPacket(packetData)
                                    } catch (e: Exception) {
                                        // Silently ignore processing errors - don't affect forwarding
                                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                                            Log.d(TAG, "Packet processing error (non-critical)", e)
                                        }
                                    }
                                }
                            }
                        } else if (length == 0) {
                            // No data available (non-blocking mode)
                            // Small delay to prevent busy-waiting
                            Thread.sleep(1)
                        } else if (length < 0) {
                            // End of stream
                            Log.w(TAG, "TUN read returned $length (end of stream)")
                            break
                        }
                    } catch (e: java.io.IOException) {
                        if (isRunning) {
                            Log.e(TAG, "I/O error in forwarding thread", e)
                        }
                        break
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Unexpected error in forwarding thread", e)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in forwarding thread", e)
            } finally {
                val totalPackets = forwardedPackets.get()
                val totalBytes = forwardedBytes.get()
                val duration = System.currentTimeMillis() - forwardingStartTime.get()
                Log.i(TAG, "=== TUN READ/WRITE LOOP END ===")
                Log.i(TAG, "Forwarding thread ended. Stats:")
                Log.i(TAG, "  Total read: $totalRead bytes ($readCount reads)")
                Log.i(TAG, "  Total written: $totalWritten bytes ($writeCount writes)")
                Log.i(TAG, "  Forwarded packets: $totalPackets")
                Log.i(TAG, "  Forwarded bytes: $totalBytes")
                Log.i(TAG, "  Duration: ${duration}ms")
                if (duration > 0) {
                    val pps = (totalPackets * 1000) / duration
                    val bps = (totalBytes * 1000) / duration
                    Log.i(TAG, "  Average: $pps packets/sec, $bps bytes/sec")
                }
            }
        }
        
        forwardingThread.name = "VPN-Forwarding"
        forwardingThread.start()
        Log.i(TAG, "Forwarding thread launched: ${forwardingThread.name}")
        
        try {
            // Wait for thread to complete
            forwardingThread.join()
        } catch (e: Exception) {
            Log.e(TAG, "Error waiting for forwarding thread", e)
        } finally {
            Log.d(TAG, "Capture loop ended")
            try {
                inputStream.close()
                outputStream.close()
                Log.d(TAG, "TUN streams closed")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing streams", e)
            }
        }
    }
    
    /**
     * Get forwarding statistics
     */
    fun getForwardingStats(): Pair<Long, Long> {
        return Pair(forwardedPackets.get(), forwardedBytes.get())
    }

    private fun stopCapture() {
        Log.d(TAG, "Stopping packet capture")
        
        isRunning = false
        
        // Cancel capture job
        captureJob?.cancel()
        captureJob = null
        
        // Stop packet processor
        packetProcessor.stopProcessing()
        
        // Close VPN interface
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface", e)
        }
        vpnInterface = null
        
        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        
        stopCapture()
        nativeParser.destroyParser()
        scope.cancel()
        instance = null
        
        super.onDestroy()
    }
    
    // Public methods to access processor data
    fun getStats() = packetProcessor.stats
    fun getAppTalkers() = packetProcessor.getAppTalkers()

    override fun onRevoke() {
        Log.d(TAG, "VPN permission revoked")
        stopCapture()
        super.onRevoke()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_service_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when packet capture is active"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_service_notification_title))
            .setContentText(getString(R.string.vpn_service_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}

