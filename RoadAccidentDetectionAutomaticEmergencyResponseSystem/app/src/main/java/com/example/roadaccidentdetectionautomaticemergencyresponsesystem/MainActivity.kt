package com.example.roadaccidentdetectionautomaticemergencyresponsesystem

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.window.Dialog
import com.example.roadaccidentdetectionautomaticemergencyresponsesystem.ui.theme.RoadAccidentDetectionAutomaticEmergencyResponseSystemTheme
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class EmergencyContact(
    val name: String,
    val phone: String
)

data class Incident(
    val date: String,
    val location: String,
    val status: String
)

enum class Screen {
    Dashboard,
    Contacts,
    Settings,
    History
}

class MainActivity : ComponentActivity() {

    private lateinit var emergencyManager: EmergencyManager
    private val backgroundColor = Color(0xFF1B1B1F)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        emergencyManager = EmergencyManager(this)
        
        enableEdgeToEdge()
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        setContent {
            RoadAccidentDetectionAutomaticEmergencyResponseSystemTheme {
                var showLoading by remember { mutableStateOf(true) }
                var showOnboarding by remember { mutableStateOf(false) }
                var isRegistered by remember { mutableStateOf(false) }
                var contacts by remember { mutableStateOf(loadContacts(this)) }
                var isRunning by remember { mutableStateOf(false) }
                var isEmergencyTriggered by remember { mutableStateOf(false) }
                var showSuccess by remember { mutableStateOf(false) }
                var incidents by remember { mutableStateOf(loadIncidents(this)) }
                var userName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
                val navController = rememberNavController()

                // Listen for accident detection from Foreground Service
                val context = androidx.compose.ui.platform.LocalContext.current
                DisposableEffect(Unit) {
                    val receiver = object : android.content.BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            when (intent?.action) {
                                "com.example.ACCIDENT_DETECTED" -> isEmergencyTriggered = true
                                "com.example.HISTORY_UPDATED" -> {
                                    incidents = loadIncidents(context!!)
                                }
                                "com.example.EMERGENCY_SENT" -> {
                                    showSuccess = true
                                    isEmergencyTriggered = false
                                    showStatusNotification("🚨 Alert Sent", "Emergency message sent successfully")
                                }
                            }
                        }
                    }
                    val filter = IntentFilter().apply {
                        addAction("com.example.ACCIDENT_DETECTED")
                        addAction("com.example.HISTORY_UPDATED")
                        addAction("com.example.EMERGENCY_SENT")
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        context.registerReceiver(receiver, filter)
                    }
                    onDispose {
                        context.unregisterReceiver(receiver)
                    }
                }

                LaunchedEffect(Unit) {
                    delay(2000)
                    showLoading = false
                    val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding", false)
                    isRegistered = prefs.getBoolean("is_registered", false)
                    
                    if (!hasSeenOnboarding) {
                        showOnboarding = true
                    }
                }

                Surface(modifier = Modifier.fillMaxSize(), color = backgroundColor) {
                    val requestPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions()
                    ) { permissions ->
                        val allGranted = permissions.values.all { it }
                        if (!allGranted) {
                            Toast.makeText(this@MainActivity, "Permissions required for safety features", Toast.LENGTH_LONG).show()
                        }
                    }

                    LaunchedEffect(Unit) {
                        val permissions = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.SEND_SMS
                        )
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        requestPermissionLauncher.launch(permissions.toTypedArray())
                    }

                    if (showLoading) {
                        LoadingScreen()
                    } else if (showOnboarding) {
                        OnboardingScreen(onFinished = { 
                            showOnboarding = false 
                            prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                        })
                    } else if (!isRegistered) {
                        RegisterScreen(
                            onRegisterSuccess = { 
                                isRegistered = true 
                                userName = prefs.getString("user_name", "") ?: ""
                                Toast.makeText(this@MainActivity, "Registration Successful!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    } else {
                        Scaffold(
                            bottomBar = {
                                BottomNavigationBar(navController)
                            },
                            containerColor = backgroundColor
                        ) { paddingValues ->
                            Box(modifier = Modifier.padding(paddingValues)) {
                                NavHost(
                                    navController = navController,
                                    startDestination = Screen.Dashboard.name
                                ) {
                                    composable(Screen.Dashboard.name) {
                                        DashboardScreen(
                                            isRunning = isRunning,
                                            isEmergencyTriggered = isEmergencyTriggered,
                                            userName = userName,
                                            onToggleDetection = {
                                                if (contacts.isEmpty()) {
                                                    Toast.makeText(this@MainActivity, "Please add at least one contact", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    if (isRunning) {
                                                        stopService(Intent(this@MainActivity, AccidentDetectionService::class.java))
                                                        isRunning = false
                                                        isEmergencyTriggered = false
                                                    } else {
                                                        val serviceIntent = Intent(this@MainActivity, AccidentDetectionService::class.java)
                                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                            startForegroundService(serviceIntent)
                                                        } else {
                                                            startService(serviceIntent)
                                                        }
                                                        isRunning = true
                                                    }
                                                }
                                            },
                                            onCancelSOS = { 
                                                isEmergencyTriggered = false
                                                prefs.edit().putBoolean("is_emergency_cancelled", true).apply()
                                                showStatusNotification("🟢 SOS Cancelled", "No emergency alert was sent")
                                            },
                                            onManualSOS = {
                                                if (contacts.isEmpty()) {
                                                    Toast.makeText(this@MainActivity, "No contacts saved!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    emergencyManager.triggerEmergency(contacts.map { it.phone })
                                                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault())
                                                    val currentDate = sdf.format(java.util.Date())
                                                    saveIncidents(this@MainActivity, incidents + Incident(currentDate, "Manual SOS", "Sent"))
                                                    incidents = loadIncidents(this@MainActivity)
                                                    showSuccess = true
                                                }
                                            }
                                        )
                                    }
                                    composable(Screen.Contacts.name) {
                                        ContactsScreen(
                                            contacts = contacts,
                                            onAddContact = { name, phone ->
                                                val newList = contacts + EmergencyContact(name, phone)
                                                contacts = newList
                                                saveContacts(this@MainActivity, newList)
                                            },
                                            onDeleteContact = { contact ->
                                                val newList = contacts.filter { it != contact }
                                                contacts = newList
                                                saveContacts(this@MainActivity, newList)
                                            }
                                        )
                                    }
                                    composable(Screen.History.name) {
                                        HistoryScreen(incidents)
                                    }
                                    composable(Screen.Settings.name) {
                                        SettingsScreen(prefs)
                                    }
                                }
                            }
                        }

                        if (isEmergencyTriggered) {
                            EmergencyCountdownDialog(
                                onCancel = {
                                    isEmergencyTriggered = false
                                    prefs.edit().putBoolean("is_emergency_cancelled", true).apply()
                                    showStatusNotification("🟢 SOS Cancelled", "No emergency alert was sent")
                                },
                                onConfirm = {
                                    isEmergencyTriggered = false
                                }
                            )
                        }

                        if (showSuccess) {
                            LaunchedEffect(Unit) {
                                delay(2500)
                                showSuccess = false
                            }

                            Dialog(onDismissRequest = { showSuccess = false }) {
                                Card(
                                    modifier = Modifier.size(200.dp),
                                    shape = RoundedCornerShape(28.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        var isVisible by remember { mutableStateOf(false) }
                                        LaunchedEffect(Unit) { delay(100); isVisible = true }
                                        
                                        AnimatedVisibility(
                                            visible = isVisible,
                                            enter = scaleIn(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                modifier = Modifier.size(90.dp),
                                                tint = Color.Green
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = "ALERT SENT",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveIncidents(context: Context, incidents: List<Incident>) {
        val prefs = context.getSharedPreferences("history_prefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(incidents)
        prefs.edit().putString("incidents_list", json).apply()
    }

    private fun loadIncidents(context: Context): List<Incident> {
        val prefs = context.getSharedPreferences("history_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("incidents_list", null)
        return if (json != null) {
            val type = object : TypeToken<List<Incident>>() {}.type
            Gson().fromJson(json, type)
        } else {
            emptyList()
        }
    }

    private fun saveContacts(context: Context, contacts: List<EmergencyContact>) {
        val prefs = context.getSharedPreferences("contacts_prefs", Context.MODE_PRIVATE)
        val json = Gson().toJson(contacts)
        prefs.edit().putString("contacts_list", json).apply()
    }

    private fun loadContacts(context: Context): List<EmergencyContact> {
        val prefs = context.getSharedPreferences("contacts_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("contacts_list", null)
        return if (json != null) {
            val type = object : TypeToken<List<EmergencyContact>>() {}.type
            Gson().fromJson(json, type)
        } else {
            emptyList()
        }
    }

    @Composable
    fun BottomNavigationBar(navController: NavController) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        NavigationBar(
            containerColor = Color(0xFF2C2C2E),
            contentColor = Color.White
        ) {
            val items = listOf(
                Triple(Screen.Dashboard, "Home", Icons.Default.Home),
                Triple(Screen.Contacts, "Contacts", Icons.Default.Person),
                Triple(Screen.History, "History", Icons.Default.History),
                Triple(Screen.Settings, "Settings", Icons.Default.Settings)
            )
            items.forEach { (screen, label, icon) ->
                NavigationBarItem(
                    selected = currentRoute == screen.name,
                    onClick = {
                        navController.navigate(screen.name) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(icon, contentDescription = label) },
                    label = { Text(label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = Color.White,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.DarkGray
                    )
                )
            }
        }
    }

    @Composable
    fun DashboardScreen(
        isRunning: Boolean,
        isEmergencyTriggered: Boolean,
        userName: String,
        onToggleDetection: () -> Unit,
        onCancelSOS: () -> Unit,
        onManualSOS: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = if (isRunning) 1.2f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ), label = ""
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (userName.isNotBlank()) "Hi, $userName 👋" else "Welcome 👋",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Stay safe on the road 🚗",
                    color = Color.LightGray
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
            
            Box(contentAlignment = Alignment.Center) {
                if (isRunning) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(CircleShape)
                            .background(Color.Green.copy(alpha = 0.1f * pulseScale))
                    )
                }
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = if (isRunning) "SYSTEM ACTIVE" else "SYSTEM INACTIVE",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isRunning) Color.Green else Color.Gray
            )
            
            Text(
                text = if (isRunning) "Background monitoring enabled" else "Start monitoring before driving",
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(60.dp))

            Button(
                onClick = onToggleDetection,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFEF5350) else Color.White,
                    contentColor = if (isRunning) Color.White else Color.Black
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isRunning) "STOP MONITORING" else "START MONITORING", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedButton(
                onClick = onManualSOS,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color.Red),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
            ) {
                Text("SEND EMERGENCY ALERT 🚨", fontWeight = FontWeight.Bold)
            }
        }
    }

    @Composable
    fun EmergencyCountdownDialog(
        onCancel: () -> Unit,
        onConfirm: () -> Unit
    ) {
        val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedDelay = prefs.getInt("sos_delay", 5)
        var countdown by remember { mutableIntStateOf(savedDelay) }
        val context = androidx.compose.ui.platform.LocalContext.current

        LaunchedEffect(Unit) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(500)
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
            onConfirm()
        }

        AlertDialog(
            onDismissRequest = { /* Prevent dismiss */ },
            confirmButton = {},
            dismissButton = {
                Button(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("I AM SAFE ❌", fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text("🚨 Accident Detected", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Sending alert in $countdown seconds...",
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { if (savedDelay > 0) countdown.toFloat() / savedDelay else 0f },
                        color = Color.Red,
                        trackColor = Color.DarkGray,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            containerColor = Color(0xFF1B1B1F)
        )
    }

    @Composable
    fun ContactsScreen(
        contacts: List<EmergencyContact>,
        onAddContact: (String, String) -> Unit,
        onDeleteContact: (EmergencyContact) -> Unit
    ) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }

        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("Emergency Contacts", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Name") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text("Phone") }, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && phone.isNotBlank()) {
                                onAddContact(name, phone)
                                name = ""; phone = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("ADD NEW CONTACT")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                contacts.forEach { contact ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(contact.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(contact.phone, color = Color.Gray)
                            }
                            IconButton(onClick = { onDeleteContact(contact) }) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsScreen(prefs: android.content.SharedPreferences) {
        var sosDelay by remember { mutableIntStateOf(prefs.getInt("sos_delay", 5)) }
        var showDelayDialog by remember { mutableStateOf(false) }
        var sensitivity by remember { mutableFloatStateOf(prefs.getFloat("impact_sensitivity", 35f)) }
        var showSensitivityDialog by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clickable { showSensitivityDialog = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sensitivityLabel = when {
                    sensitivity <= 25f -> "High (Very Sensitive)"
                    sensitivity <= 40f -> "Medium (Normal)"
                    else -> "Low (Heavy Impact)"
                }
                Text("Impact Sensitivity", color = Color.White)
                Text(sensitivityLabel, color = Color.Gray)
            }
            HorizontalDivider(color = Color.DarkGray)
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
                    .clickable { showDelayDialog = true },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SOS Delay", color = Color.White)
                Text("$sosDelay Seconds", color = Color.Gray)
            }
            HorizontalDivider(color = Color.DarkGray)

            SettingsItem("Language", "English")
            SettingsItem("App Version", "1.0.5")
        }

        if (showSensitivityDialog) {
            AlertDialog(
                onDismissRequest = { showSensitivityDialog = false },
                title = { Text("Impact Sensitivity", color = Color.White) },
                text = {
                    Column {
                        Text("Higher sensitivity triggers alerts more easily. Lower sensitivity requires a harder impact.", color = Color.LightGray, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        val options = listOf(
                            Triple(20f, "High", "Triggers easily (Bumpy roads)"),
                            Triple(35f, "Medium", "Recommended for most cars"),
                            Triple(50f, "Low", "Only for severe impacts")
                        )

                        options.forEach { (value, label, desc) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sensitivity = value
                                        prefs.edit().putFloat("impact_sensitivity", value).apply()
                                        showSensitivityDialog = false
                                        Toast.makeText(this@MainActivity, "Sensitivity updated. Restart monitoring to apply.", Toast.LENGTH_LONG).show()
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(desc, color = Color.Gray, fontSize = 12.sp)
                                }
                                if (sensitivity == value) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSensitivityDialog = false }) {
                        Text("CANCEL")
                    }
                },
                containerColor = Color(0xFF2C2C2E)
            )
        }

        if (showDelayDialog) {
            AlertDialog(
                onDismissRequest = { showDelayDialog = false },
                title = { Text("Set SOS Delay", color = Color.White) },
                text = {
                    Column {
                        listOf(5, 10, 15, 20, 30).forEach { delay ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        sosDelay = delay
                                        prefs.edit().putInt("sos_delay", delay).apply()
                                        showDelayDialog = false
                                    }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("$delay Seconds", color = Color.White)
                                if (sosDelay == delay) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Green)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDelayDialog = false }) {
                        Text("CANCEL")
                    }
                },
                containerColor = Color(0xFF2C2C2E)
            )
        }
    }

    @Composable
    fun HistoryScreen(incidents: List<Incident>) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("Incident History", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            
            if (incidents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No incidents recorded", color = Color.Gray)
                }
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    incidents.reversed().forEach { incident ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(incident.date, color = Color.LightGray, fontSize = 12.sp)
                                    Text(incident.status, color = if (incident.status == "Sent") Color.Green else Color.Yellow, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(incident.location, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun SettingsItem(title: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = Color.White)
            Text(value, color = Color.Gray)
        }
        HorizontalDivider(color = Color.DarkGray)
    }

    @Composable
    fun LoadingScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painter = painterResource(id = R.drawable.app_logo), contentDescription = null, modifier = Modifier.size(150.dp).clip(CircleShape))
                Spacer(modifier = Modifier.height(32.dp))
                CircularProgressIndicator(color = Color.White)
            }
        }
    }

    @Composable
    fun RegisterScreen(onRegisterSuccess: () -> Unit) {
        var name by remember { mutableStateOf("") }
        val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Welcome", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(32.dp))
                OutlinedTextField(
                    value = name, 
                    onValueChange = { name = it }, 
                    label = { Text("Name", color = Color.Gray) }, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            prefs.edit()
                                .putString("user_name", name)
                                .putBoolean("is_registered", true)
                                .apply()
                            onRegisterSuccess()
                        }
                    }, 
                    modifier = Modifier.fillMaxWidth().height(56.dp), 
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                ) {
                    Text("START SAVING LIVES")
                }
            }
        }
    }

    @Composable
    fun OnboardingScreen(onFinished: () -> Unit) {
        val pagerState = rememberPagerState(pageCount = { 3 })
        val scope = rememberCoroutineScope()
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(modifier = Modifier.size(200.dp).background(Color.Gray.copy(alpha = 0.2f), CircleShape))
                    Spacer(modifier = Modifier.height(48.dp))
                    Text("Feature ${page + 1}", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                }
            }
            Button(onClick = { if (pagerState.currentPage < 2) scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } else onFinished() }, modifier = Modifier.padding(32.dp).fillMaxWidth()) {
                Text("NEXT")
            }
        }
    }

    private fun showStatusNotification(title: String, message: String) {
        val channelId = "emergency_status_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Emergency Status",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
