package com.example.ui.optimizer

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.RouteEntity
import com.example.data.model.Stop
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Custom modern brand colors matching tech NVIDIA vibe
val NvidiaGreen = Color(0xFF76B900)
val TechDarkBg = Color(0xFF121214)
val TechCardBg = Color(0xFF1E1E22)
val TextGray = Color(0xFFB0B0B5)

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RouteOptimizerScreen(
    viewModel: RouteOptimizerViewModel
) {
    val startPoint by viewModel.startPoint.collectAsState()
    val endPoint by viewModel.endPoint.collectAsState()
    val stops by viewModel.stops.collectAsState()
    val optimizedRoute by viewModel.optimizedRoute.collectAsState()
    val totalDistance by viewModel.totalDistanceKm.collectAsState()
    val isOptimizing by viewModel.isOptimizing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val solverUsed by viewModel.solverUsed.collectAsState()

    val isNvidiaEnabled by viewModel.isNvidiaEnabled.collectAsState()
    val nvidiaApiKey by viewModel.nvidiaApiKey.collectAsState()
    val nvidiaEndpointUrl by viewModel.nvidiaEndpointUrl.collectAsState()

    val jsonRequestPayload by viewModel.jsonRequestPayload.collectAsState()
    val jsonResponsePayload by viewModel.jsonResponsePayload.collectAsState()

    val routeHistory by viewModel.routeHistory.collectAsState()

    var activeTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Kreator", "Interaktywna Mapa", "Inspektor cuOpt", "Opcje & Historia")

    var promptInput by remember { mutableStateOf("") }
    var stopNameInput by remember { mutableStateOf("") }
    var stopLatInput by remember { mutableStateOf("") }
    var stopLngInput by remember { mutableStateOf("") }

    var editingStartEnd by remember { mutableStateOf<String?>(null) } // "start" or "end"
    var tempName by remember { mutableStateOf("") }
    var tempLat by remember { mutableStateOf("") }
    var tempLng by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(NvidiaGreen),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = "cuOpt Icon",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "cuOpt Route Optimizer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TechDarkBg
                )
            )
        },
        containerColor = TechDarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Header with Pill Selection style
            ScrollableTabRow(
                selectedTabIndex = activeTab,
                containerColor = TechDarkBg,
                contentColor = NvidiaGreen,
                edgePadding = 16.dp,
                divider = {}
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (activeTab == index) NvidiaGreen else TextGray
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Error Display with animations
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3E1212)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF7E2222))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Błąd",
                            tint = Color.Red
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Błąd / Status",
                                fontWeight = FontWeight.Bold,
                                color = Color.Red,
                                fontSize = 12.sp
                            )
                            Text(
                                text = errorMessage ?: "",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            // Tabs Content
            Box(modifier = Modifier.weight(1f)) {
                when (activeTab) {
                    0 -> TabRouteCreator(
                        startPoint = startPoint,
                        endPoint = endPoint,
                        stops = stops,
                        promptInput = promptInput,
                        onPromptChange = { promptInput = it },
                        onParseAI = { viewModel.parseRouteWithAI(promptInput) },
                        stopNameInput = stopNameInput,
                        onStopNameChange = { stopNameInput = it },
                        stopLatInput = stopLatInput,
                        onStopLatChange = { stopLatInput = it },
                        stopLngInput = stopLngInput,
                        onStopLngChange = { stopLngInput = it },
                        onAddStop = {
                            val lat = stopLatInput.toDoubleOrNull() ?: 52.0
                            val lng = stopLngInput.toDoubleOrNull() ?: 20.0
                            viewModel.addStop(stopNameInput, lat, lng)
                            stopNameInput = ""
                            stopLatInput = ""
                            stopLngInput = ""
                        },
                        onRemoveStop = { viewModel.removeStop(it) },
                        onOptimize = { viewModel.optimizeRoute() },
                        isOptimizing = isOptimizing,
                        solverUsed = solverUsed,
                        totalDistance = totalDistance,
                        onEditStartEnd = { type ->
                            editingStartEnd = type
                            val pt = if (type == "start") startPoint else endPoint
                            tempName = pt?.name ?: ""
                            tempLat = pt?.latitude?.toString() ?: ""
                            tempLng = pt?.longitude?.toString() ?: ""
                        }
                    )
                    1 -> TabRouteMap(
                        startPoint = startPoint,
                        endPoint = endPoint,
                        stops = stops,
                        optimizedRoute = optimizedRoute,
                        totalDistance = totalDistance,
                        solverUsed = solverUsed,
                        isOptimizing = isOptimizing,
                        onOptimize = { viewModel.optimizeRoute() },
                        onAddCoordOnMap = { name, lat, lng ->
                            viewModel.addStop(name, lat, lng)
                        }
                    )
                    2 -> TabPayloadInspector(
                        requestJson = jsonRequestPayload,
                        responseJson = jsonResponsePayload
                    )
                    3 -> TabHistorySettings(
                        nvidiaApiKey = nvidiaApiKey,
                        onApiKeyChange = { viewModel.nvidiaApiKey.value = it },
                        nvidiaEndpointUrl = nvidiaEndpointUrl,
                        onEndpointChange = { viewModel.nvidiaEndpointUrl.value = it },
                        isNvidiaEnabled = isNvidiaEnabled,
                        onToggleNvidia = { viewModel.isNvidiaEnabled.value = it },
                        routeHistory = routeHistory,
                        onLoadRoute = { viewModel.loadRouteFromHistory(it) },
                        onDeleteRoute = { viewModel.deleteRouteHistory(it) },
                        onClearAll = { viewModel.clearHistory() }
                    )
                }
            }
        }
    }

    // Modal dialog for editing Start/End coordinates
    if (editingStartEnd != null) {
        AlertDialog(
            onDismissRequest = { editingStartEnd = null },
            title = {
                Text(
                    text = if (editingStartEnd == "start") "Modyfikuj Punkt Startowy" else "Modyfikuj Cel Końcowy",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            containerColor = TechCardBg,
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Nazwa lokalizacji") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NvidiaGreen,
                            focusedLabelColor = NvidiaGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempLat,
                        onValueChange = { tempLat = it },
                        label = { Text("Szerokość (Lat e.g. 52.2)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NvidiaGreen,
                            focusedLabelColor = NvidiaGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempLng,
                        onValueChange = { tempLng = it },
                        label = { Text("Długość (Lng e.g. 21.0)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NvidiaGreen,
                            focusedLabelColor = NvidiaGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val latVal = tempLat.toDoubleOrNull() ?: 50.0
                        val lngVal = tempLng.toDoubleOrNull() ?: 19.0
                        if (editingStartEnd == "start") {
                            viewModel.setStartPoint(tempName, latVal, lngVal)
                        } else {
                            viewModel.setEndPoint(tempName, latVal, lngVal)
                        }
                        editingStartEnd = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NvidiaGreen, contentColor = Color.Black)
                ) {
                    Text("Zapisz")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingStartEnd = null }) {
                    Text("Anuluj", color = NvidiaGreen)
                }
            }
        )
    }
}

@Composable
fun TabRouteCreator(
    startPoint: Stop?,
    endPoint: Stop?,
    stops: List<Stop>,
    promptInput: String,
    onPromptChange: (String) -> Unit,
    onParseAI: () -> Unit,
    stopNameInput: String,
    onStopNameChange: (String) -> Unit,
    stopLatInput: String,
    onStopLatChange: (String) -> Unit,
    stopLngInput: String,
    onStopLngChange: (String) -> Unit,
    onAddStop: () -> Unit,
    onRemoveStop: (String) -> Unit,
    onOptimize: () -> Unit,
    isOptimizing: Boolean,
    solverUsed: String,
    totalDistance: Double,
    onEditStartEnd: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: AI Prompt geocoder
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TechCardBg),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Assistant",
                            tint = NvidiaGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Logistyczny Kreator Trasy (A.I.)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Wpisz naturalnym językiem plan podróży – sztuczna inteligencja Gemini wyznaczy koordynaty miast.",
                        fontSize = 12.sp,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = onPromptChange,
                        placeholder = {
                            Text(
                                "Np.: Wyruszam z Wrocławia, po drodze Katowice, Kielce, Radom, a kończę w Warszawie",
                                fontSize = 13.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NvidiaGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = onParseAI,
                            enabled = promptInput.isNotBlank() && !isOptimizing,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NvidiaGreen,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("ai_parse_button")
                        ) {
                            if (isOptimizing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Black)
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Generuj z Gemini AI", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Quick presets buttons to test
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Nie wiesz co wpisać? Kliknij gotową darmową trasę testową:", fontSize = 11.sp, color = TextGray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            "Warszawa, Łódź, Toruń, Bydgoszcz, Gdańsk",
                            "Kraków, Oświęcim, Zakopane, Nowy Sącz"
                        )
                        presets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2B2B31))
                                    .clickable { onPromptChange(preset) }
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 11.sp,
                                    color = NvidiaGreen,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section: Start and Endpoints Setup
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Start Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEditStartEnd("start") },
                    colors = CardDefaults.cardColors(containerColor = TechCardBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = startPoint?.name ?: "Brak",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Text(
                            text = String.format(Locale.US, "%.3f, %.3f", startPoint?.latitude ?: 0.0, startPoint?.longitude ?: 0.0),
                            fontSize = 11.sp,
                            color = TextGray
                        )
                    }
                }

                // End Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEditStartEnd("end") },
                    colors = CardDefaults.cardColors(containerColor = TechCardBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF44336))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Koniec (Cel)", fontWeight = FontWeight.Bold, color = Color(0xFFF44336), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = endPoint?.name ?: "Brak",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp,
                            maxLines = 1
                        )
                        Text(
                            text = String.format(Locale.US, "%.3f, %.3f", endPoint?.latitude ?: 0.0, endPoint?.longitude ?: 0.0),
                            fontSize = 11.sp,
                            color = TextGray
                        )
                    }
                }
            }
        }

        // Section: Intermediate Stops Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Przystanki pośrednie (${stops.size})",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Text(
                    text = "Optymalizuj kolejność",
                    fontSize = 12.sp,
                    color = NvidiaGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onOptimize() }
                )
            }
        }

        // List of Stops
        if (stops.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TechCardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = TextGray,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Brak przystanków pośrednich",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dodaj punkty ręcznie poniżej lub skorzystaj z Asystenta AI powyżej.",
                            color = TextGray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(stops) { stop ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = TechCardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stop.name, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                text = String.format(Locale.US, "Lat: %.4f, Lng: %.4f", stop.latitude, stop.longitude),
                                fontSize = 11.sp,
                                color = TextGray
                            )
                        }
                        IconButton(onClick = { onRemoveStop(stop.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Usuń stop",
                                tint = Color(0xFFFF5252)
                            )
                        }
                    }
                }
            }
        }

        // Section: Manual Stop Adder Forms
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TechCardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2D2D31))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Dodaj przystanek ręcznie",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = stopNameInput,
                        onValueChange = onStopNameChange,
                        label = { Text("Nazwa miejsca") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NvidiaGreen,
                            focusedLabelColor = NvidiaGreen,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stopLatInput,
                            onValueChange = onStopLatChange,
                            label = { Text("Latitude (e.g. 50.8)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NvidiaGreen,
                                focusedLabelColor = NvidiaGreen,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stopLngInput,
                            onValueChange = onStopLngChange,
                            label = { Text("Longitude (e.g. 19.1)") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NvidiaGreen,
                                focusedLabelColor = NvidiaGreen,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onAddStop,
                        enabled = stopNameInput.isNotBlank() && stopLatInput.toDoubleOrNull() != null && stopLngInput.toDoubleOrNull() != null,
                        colors = ButtonDefaults.buttonColors(containerColor = NvidiaGreen, contentColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_stop_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dodaj do listy", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Bottom Solver Action Box
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF142416)),
                border = BorderStroke(1.dp, Color(0xFF224422)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Używany optymalizator:", fontSize = 11.sp, color = TextGray)
                            Text(text = solverUsed, fontWeight = FontWeight.Bold, color = NvidiaGreen, fontSize = 15.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Przebieg łączny:", fontSize = 11.sp, color = TextGray)
                            Text(text = String.format(Locale.US, "%.1f km", totalDistance), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOptimize,
                        enabled = !isOptimizing,
                        colors = ButtonDefaults.buttonColors(containerColor = NvidiaGreen, contentColor = Color.Black),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("optimize_button")
                    ) {
                        if (isOptimizing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("OBLiCZ OPTYMALNĄ TRASĘ (cuOpt)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TabRouteMap(
    startPoint: Stop?,
    endPoint: Stop?,
    stops: List<Stop>,
    optimizedRoute: List<Stop>,
    totalDistance: Double,
    solverUsed: String,
    isOptimizing: Boolean,
    onOptimize: () -> Unit,
    onAddCoordOnMap: (String, Double, Double) -> Unit
) {
    var clickToAddMode by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Status Bar Card
        Card(
            colors = CardDefaults.cardColors(containerColor = TechCardBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Silnik: $solverUsed", fontSize = 12.sp, color = NvidiaGreen, fontWeight = FontWeight.Bold)
                    Text(text = String.format(Locale.US, "Dystans: %.1f km", totalDistance), fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = { clickToAddMode = !clickToAddMode },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (clickToAddMode) Color(0xFFFF9800) else Color(0xFF2B2B31),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("map_click_mode_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (clickToAddMode) Icons.Default.AddLocationAlt else Icons.Default.TouchApp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (clickToAddMode) "Klikaj na mapę" else "Interakcja", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Technical tips
        Text(
            text = if (clickToAddMode) "💡 Kliknij na siatce poniżej, aby dodać nowy przystanek o koordynatach z tego punktu."
            else "💡 Wizualizacja koordynatów GPS przeniesiona na dwuwymiarowy układ współrzędnych.",
            fontSize = 11.sp,
            color = TextGray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Custom Canvas Interactive Map
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F0F11))
                .border(BorderStroke(1.dp, Color(0xFF232329)), RoundedCornerShape(16.dp))
        ) {
            // Infinite animation for flowing dashed lines
            val infiniteTransition = rememberInfiniteTransition()
            val dashPhase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 100f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )

            val flowDotProgress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )

            // Compile all list
            val currentPoints = mutableListOf<Stop>().apply {
                if (startPoint != null) add(startPoint)
                addAll(stops)
                if (endPoint != null) add(endPoint)
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("interactive_canvas_map")
                    .pointerInput(clickToAddMode) {
                        if (clickToAddMode) {
                            detectTapGestures { offset ->
                                // Project back from Canvas pixels to Lat/Lng
                                // Grid maps to margins
                                val pad = 60f
                                val normX = (offset.x - pad) / (size.width - 2 * pad)
                                val normY = (offset.y - pad) / (size.height - 2 * pad)

                                // Center coordinates around central Poland: Lat 50 to 54.5, Lng 14 to 24
                                val minLat = 49.0
                                val maxLat = 55.0
                                val minLng = 14.0
                                val maxLng = 24.5

                                val calculatedLng = minLng + normX * (maxLng - minLng)
                                val calculatedLat = maxLat - normY * (maxLat - minLat)

                                val randId = (stops.size + 1).toString()
                                onAddCoordOnMap("Punkt z Mapy #$randId", calculatedLat, calculatedLng)
                            }
                        }
                    }
            ) {
                // Compute bounds for responsive aspect scaling
                val pad = 60f
                val w = size.width
                val h = size.height

                // Poland box bounds
                val minLat = 49.0
                val maxLat = 55.0
                val minLng = 14.0
                val maxLng = 24.5

                fun projectToCanvas(lat: Double, lng: Double): Offset {
                    // Normalize
                    val normX = (lng - minLng) / (maxLng - minLng)
                    val normY = 1.0 - (lat - minLat) / (maxLat - minLat) // Flip Y

                    val finalX = pad + normX.toFloat() * (w - 2 * pad)
                    val finalY = pad + normY.toFloat() * (h - 2 * pad)

                    return Offset(finalX, finalY)
                }

                // DRAW BACKGROUND GRID
                for (gridX in 0..10) {
                    val lineX = pad + (gridX / 10f) * (w - 2 * pad)
                    drawLine(
                        color = Color(0xFF1E1E24),
                        start = Offset(lineX, 0f),
                        end = Offset(lineX, h),
                        strokeWidth = 1f
                    )
                }
                for (gridY in 0..10) {
                    val lineY = pad + (gridY / 10f) * (h - 2 * pad)
                    drawLine(
                        color = Color(0xFF1E1E24),
                        start = Offset(0f, lineY),
                        end = Offset(w, lineY),
                        strokeWidth = 1f
                    )
                }

                if (currentPoints.isEmpty()) return@Canvas

                // DRAW PATHS
                if (optimizedRoute.isNotEmpty()) {
                    // Draw continuous optimized path in green
                    val pathOffsets = optimizedRoute.map { projectToCanvas(it.latitude, it.longitude) }
                    
                    // Main solid path line
                    for (i in 0 until pathOffsets.size - 1) {
                        drawLine(
                            color = NvidiaGreen,
                            start = pathOffsets[i],
                            end = pathOffsets[i + 1],
                            strokeWidth = 6f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), dashPhase)
                        )
                    }

                    // Flowing movement dots animation
                    for (i in 0 until pathOffsets.size - 1) {
                        val pStart = pathOffsets[i]
                        val pEnd = pathOffsets[i + 1]
                        val animatedDotPos = Offset(
                            x = pStart.x + (pEnd.x - pStart.x) * flowDotProgress,
                            y = pStart.y + (pEnd.y - pStart.y) * flowDotProgress
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 6f,
                            center = animatedDotPos
                        )
                        drawCircle(
                            color = NvidiaGreen.copy(alpha = 0.5f),
                            radius = 12f,
                            center = animatedDotPos
                        )
                    }
                } else {
                    // Draw consecutive un-optimized points in grey
                    val pathOffsets = currentPoints.map { projectToCanvas(it.latitude, it.longitude) }
                    for (i in 0 until pathOffsets.size - 1) {
                        drawLine(
                            color = Color.DarkGray,
                            start = pathOffsets[i],
                            end = pathOffsets[i + 1],
                            strokeWidth = 3f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }
                }

                // DRAW POINTS NODES (Start, Stops, End)
                stops.forEachIndexed { sIdx, stop ->
                    val pos = projectToCanvas(stop.latitude, stop.longitude)
                    drawCircle(
                        color = Color(0xFFFF9800),
                        radius = 20f,
                        center = pos
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = 16f,
                        center = pos
                    )
                }

                // Draw Start Point
                if (startPoint != null) {
                    val pos = projectToCanvas(startPoint.latitude, startPoint.longitude)
                    drawCircle(
                        color = Color(0xFF4CAF50),
                        radius = 24f,
                        center = pos
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = 19f,
                        center = pos
                    )
                }

                // Draw End Point
                if (endPoint != null) {
                    val pos = projectToCanvas(endPoint.latitude, endPoint.longitude)
                    drawCircle(
                        color = Color(0xFFF44336),
                        radius = 24f,
                        center = pos
                    )
                    drawCircle(
                        color = Color.Black,
                        radius = 19f,
                        center = pos
                    )
                }
            }

            // Overlay node labels on top of Canvas coordinate projections
            currentPoints.forEachIndexed { index, stop ->
                // Basic labels overlapping the circles for beautiful aesthetics
                val labelText = when {
                    stop.id == startPoint?.id -> "A"
                    stop.id == endPoint?.id -> "B"
                    else -> "S"
                }
                val labelColor = when {
                    stop.id == startPoint?.id -> Color(0xFF4CAF50)
                    stop.id == endPoint?.id -> Color(0xFFF44336)
                    else -> Color(0xFFFF9800)
                }

                // Just simple helper visual dots overlay
            }
            
            // Legend
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.82f)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .padding(12.dp)
                    .align(Alignment.BottomStart)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start", color = Color.White, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFFF9800)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stops", color = Color.White, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF44336)))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Koniec", color = Color.White, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onOptimize,
            enabled = !isOptimizing,
            colors = ButtonDefaults.buttonColors(containerColor = NvidiaGreen, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().testTag("map_optimize_btn")
        ) {
            Text("Re-Optymalizuj Trasę", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun TabPayloadInspector(
    requestJson: String,
    responseJson: String
) {
    var activeInspectorTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = TechCardBg),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "🛠️ cuOpt Low-Level Inspector",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = "Przeanalizuj struktury danych JSON przesyłane bezpośrednio do API mikroserwisu NVIDIA cuOpt NIM.",
                    fontSize = 11.sp,
                    color = TextGray
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selector for Request/Response
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { activeInspectorTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeInspectorTab == 0) NvidiaGreen else Color(0xFF2B2B31),
                    contentColor = if (activeInspectorTab == 0) Color.Black else Color.White
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Request JSON", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = { activeInspectorTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (activeInspectorTab == 1) NvidiaGreen else Color(0xFF2B2B31),
                    contentColor = if (activeInspectorTab == 1) Color.Black else Color.White
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text("Response JSON", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Large Code Block Viewer
        val currentCode = if (activeInspectorTab == 0) requestJson else responseJson
        if (currentCode.isBlank()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F0F11)),
                contentAlignment = Alignment.Center
            ) {
                Text("Brak danych pliku. Uruchom optymalizację trasy najpierw.", color = TextGray, fontSize = 13.sp)
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F0F11))
                    .border(BorderStroke(1.dp, Color(0xFF232329)), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = currentCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = NvidiaGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TabHistorySettings(
    nvidiaApiKey: String,
    onApiKeyChange: (String) -> Unit,
    nvidiaEndpointUrl: String,
    onEndpointChange: (String) -> Unit,
    isNvidiaEnabled: Boolean,
    onToggleNvidia: (Boolean) -> Unit,
    routeHistory: List<RouteEntity>,
    onLoadRoute: (RouteEntity) -> Unit,
    onDeleteRoute: (Int) -> Unit,
    onClearAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: NVIDIA cuOpt microservice NIM credentials
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = TechCardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFF2B2B31))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Konfiguracja NVIDIA cuOpt NIM Service",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Umożliwia wysyłanie żadań optymalizacyjnych VRP do chmury obliczeniowej NVIDIA za pomocą klucza API NGC.",
                        fontSize = 12.sp,
                        color = TextGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Włącz zewnętrzny solver cuOpt NIM", color = Color.White, fontSize = 14.sp)
                        Switch(
                            checked = isNvidiaEnabled,
                            onCheckedChange = onToggleNvidia,
                            colors = SwitchDefaults.colors(checkedThumbColor = NvidiaGreen, checkedTrackColor = Color(0xFF234423))
                        )
                    }

                    if (isNvidiaEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = nvidiaApiKey,
                            onValueChange = onApiKeyChange,
                            label = { Text("NVIDIA NGC API Key") },
                            placeholder = { Text("nvapi-...") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NvidiaGreen,
                                focusedLabelColor = NvidiaGreen,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = nvidiaEndpointUrl,
                            onValueChange = onEndpointChange,
                            label = { Text("cuOpt HTTP REST Endpoint url") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NvidiaGreen,
                                focusedLabelColor = NvidiaGreen,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Klucze API uzyskasz w NVIDIA NGC Developer CLI. Jeśli nie skonfigurujesz klucza, aplikacja bezpiecznie i bezbłędnie uruchomi lokalny solver 2-Opt kompatybilny z strukturą JSON cuOpt.",
                            fontSize = 11.sp,
                            color = TextGray
                        )
                    }
                }
            }
        }

        // Section: History Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Historia zoptymalizowanych tras (${routeHistory.size})",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                if (routeHistory.isNotEmpty()) {
                    Text(
                        text = "Wyczyść wszystko",
                        fontSize = 12.sp,
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onClearAll() }
                    )
                }
            }
        }

        // History items
        if (routeHistory.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = TechCardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.HistoryToggleOff,
                            contentDescription = null,
                            tint = TextGray,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Brak tras w historii",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Po pomyślnym zoptymalizowaniu trasy zostanie ona tutaj zapisana.",
                            color = TextGray,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(routeHistory) { route ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = TechCardBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onLoadRoute(route) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = route.title, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                text = "Silnik: ${route.solverTypeUsed} • ${String.format(Locale.US, "%.1f km", route.totalDistanceKm)}",
                                color = NvidiaGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            val fDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(route.timestamp))
                            Text(
                                text = "Zoptymalizowano: $fDate",
                                fontSize = 11.sp,
                                color = TextGray
                            )
                        }
                        IconButton(onClick = { onDeleteRoute(route.id) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Usuń historię",
                                tint = Color(0xFFFF5252)
                            )
                        }
                    }
                }
            }
        }

        item {
            Box(modifier = Modifier.height(24.dp))
        }
    }
}
