package com.visgupta.example.v8integrationandroidapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.visgupta.example.v8integrationandroidapp.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    
    companion object {
        init {
            System.loadLibrary("v8integration")
        }
    }
    
    private lateinit var v8Bridge: V8Bridge
    private lateinit var byteTransferBridge: ByteTransferBridge
    private lateinit var quickJSBridge: QuickJSBridge
    external fun stringFromJNI(): String
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        v8Bridge = V8Bridge()
        byteTransferBridge = ByteTransferBridge()
        quickJSBridge = QuickJSBridge()
        
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    V8IntegrationTestScreen(
                        v8Bridge = v8Bridge,
                        byteTransferBridge = byteTransferBridge,
                        quickJSBridge = quickJSBridge,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::v8Bridge.isInitialized) {
            v8Bridge.cleanup()
        }
        if (::byteTransferBridge.isInitialized) {
            byteTransferBridge.cleanup()
        }
        if (::quickJSBridge.isInitialized) {
            quickJSBridge.cleanup()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V8IntegrationTestScreen(
    v8Bridge: V8Bridge,
    byteTransferBridge: ByteTransferBridge,
    quickJSBridge: QuickJSBridge,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("V8 Engine", "ByteTransfer", "Integration", "Status", "QuickJS", "Remote JS")
    
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        
        when (selectedTab) {
            0 -> V8TestTab(v8Bridge)
            1 -> ByteTransferTestTab(byteTransferBridge)
            2 -> IntegrationTestTab(v8Bridge, byteTransferBridge)
            3 -> SystemStatusTab(v8Bridge, byteTransferBridge)
            4 -> QuickJSTestTab(quickJSBridge)
            5 -> RemoteJSTestTab(quickJSBridge)
        }
    }
}

@Composable
fun V8TestTab(v8Bridge: V8Bridge) {
    var isV8Initialized by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var customScript by remember { mutableStateOf("2 + 3 * 4") }
    var customResult by remember { mutableStateOf("") }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "V8 JavaScript Engine",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            SystemStatusCard(
                title = "V8 Engine Status",
                isInitialized = isV8Initialized,
                onInitialize = { isV8Initialized = v8Bridge.initialize() },
                onCleanup = {
                    v8Bridge.cleanup()
                    isV8Initialized = false
                    testResults = emptyMap()
                    customResult = ""
                }
            )
        }
        
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Custom JavaScript Execution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    OutlinedTextField(
                        value = customScript,
                        onValueChange = { customScript = it },
                        label = { Text("JavaScript Code") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    
                    Button(
                        onClick = {
                            customResult = if (isV8Initialized) {
                                v8Bridge.runJavaScript(customScript)
                            } else {
                                "❌ Please initialize V8 first"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Execute JavaScript")
                    }
                    
                    if (customResult.isNotEmpty()) {
                        ResultCard("Execution Result", customResult)
                    }
                }
            }
        }
        
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "JavaScript Test Suite",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Button(
                        onClick = {
                            testResults = if (isV8Initialized) {
                                v8Bridge.runTestSuite()
                            } else {
                                mapOf("error" to "❌ Please initialize V8 first")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Run Complete Test Suite")
                    }
                }
            }
        }
        
        items(testResults.toList()) { (testName, result) ->
            TestResultCard(testName, result)
        }
    }
}

@Composable
fun ByteTransferTestTab(byteTransferBridge: ByteTransferBridge) {
    var isByteTransferInitialized by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var bufferInfo by remember { mutableStateOf<BufferInfo?>(null) }
    var customData by remember { mutableStateOf("Hello ByteTransfer!") }
    var customResult by remember { mutableStateOf("") }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "ByteTransfer System",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            SystemStatusCard(
                title = "ByteTransfer Status",
                isInitialized = isByteTransferInitialized,
                onInitialize = { isByteTransferInitialized = byteTransferBridge.initialize() },
                onCleanup = {
                    byteTransferBridge.cleanup()
                    isByteTransferInitialized = false
                    testResults = emptyMap()
                    bufferInfo = null
                    customResult = ""
                }
            )
        }
        
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Buffer Operations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    OutlinedTextField(
                        value = customData,
                        onValueChange = { customData = it },
                        label = { Text("Data to Transfer") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                customResult = if (isByteTransferInitialized) {
                                    val success = byteTransferBridge.writeToSharedBuffer(customData.toByteArray())
                                    if (success) "✅ Write successful" else "❌ Write failed"
                                } else {
                                    "❌ Please initialize ByteTransfer first"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Write")
                        }
                        
                        Button(
                            onClick = {
                                customResult = if (isByteTransferInitialized) {
                                    val bytes = byteTransferBridge.readFromSharedBuffer(customData.length)
                                    if (bytes != null) {
                                        "✅ Read: ${bytes.toString(Charsets.UTF_8)}"
                                    } else {
                                        "❌ Read failed"
                                    }
                                } else {
                                    "❌ Please initialize ByteTransfer first"
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Read")
                        }
                    }
                    
                    Button(
                        onClick = { bufferInfo = byteTransferBridge.getBufferInfo() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Get Buffer Info")
                    }
                    
                    if (customResult.isNotEmpty()) {
                        ResultCard("Operation Result", customResult)
                    }
                    
                    bufferInfo?.let { info ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Buffer Statistics",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Name: ${info.name}")
                                Text("Size: ${info.size} bytes")
                                Text("Capacity: ${info.capacity} bytes")
                                Text("Available: ${info.available} bytes")
                                Text("Usage: ${String.format("%.1f", info.usagePercentage)}%")
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ByteTransfer Test Suite",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    Button(
                        onClick = {
                            testResults = if (isByteTransferInitialized) {
                                byteTransferBridge.runByteTransferTests()
                            } else {
                                mapOf("error" to "❌ Please initialize ByteTransfer first")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Run ByteTransfer Tests")
                    }
                }
            }
        }
        
        items(testResults.toList()) { (testName, result) ->
            TestResultCard(testName, result)
        }
    }
}

@Composable
fun IntegrationTestTab(v8Bridge: V8Bridge, byteTransferBridge: ByteTransferBridge) {
    var integrationResults by remember { mutableStateOf("") }
    var v8ByteTransferResults by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "V8 ↔ ByteTransfer Integration",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Inter-Library Communication Tests",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Button(
                    onClick = {
                        integrationResults = "Running integration tests...\n"
                        
                        // First ensure both systems are initialized
                        if (!v8Bridge.isV8Initialized()) {
                            integrationResults += "❌ V8 Engine not initialized. Please initialize V8 first.\n"
                            return@Button
                        }
                        if (!byteTransferBridge.isInitialized()) {
                            integrationResults += "❌ ByteTransfer System not initialized. Please initialize ByteTransfer first.\n"
                            return@Button
                        }
                        
                        // Create a named buffer for V8 integration tests
                        val bufferName = "v8_test"
                        val bufferSize = 1024
                        val createBufferSuccess = byteTransferBridge.createBuffer(bufferName, bufferSize)
                        integrationResults += "Create Named Buffer '$bufferName': ${if (createBufferSuccess) "✅ SUCCESS" else "❌ FAILED"}\n"
                        
                        if (!createBufferSuccess) {
                            integrationResults += "❌ Cannot proceed without named buffer\n"
                            return@Button
                        }
                        
                        val testData = "V8 Integration Test Data"
                        val writeSuccess = v8Bridge.testByteTransfer(testData.toByteArray(), bufferName)
                        integrationResults += "V8 → ByteTransfer Write: ${if (writeSuccess) "✅ SUCCESS" else "❌ FAILED"}\n"
                        
                        val readBytes = v8Bridge.readBytesFromTransfer(testData.length, 0, bufferName)
                        val readSuccess = readBytes != null
                        val readData = readBytes?.toString(Charsets.UTF_8) ?: "FAILED"
                        integrationResults += "ByteTransfer → V8 Read: ${if (readSuccess) "✅ SUCCESS" else "❌ FAILED"}\n"
                        integrationResults += "Data Match: ${if (readData == testData) "✅ MATCH" else "❌ MISMATCH"}\n"
                        
                        val bufferInfo = v8Bridge.getByteTransferInfo(bufferName)
                        integrationResults += "Buffer Info: $bufferInfo\n"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test V8 ↔ ByteTransfer Communication")
                }
                
                Button(
                    onClick = { v8ByteTransferResults = v8Bridge.runV8ByteTransferTests() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Run V8 ByteTransfer Test Suite")
                }
            }
        }
        
        if (integrationResults.isNotEmpty()) {
            ResultCard("Integration Test Results", integrationResults)
        }
        
        if (v8ByteTransferResults.isNotEmpty()) {
            ResultCard("V8 ByteTransfer Test Results", v8ByteTransferResults)
        }
    }
}

@Composable
fun SystemStatusTab(v8Bridge: V8Bridge, byteTransferBridge: ByteTransferBridge) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "System Status",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "System Components",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                StatusRow("V8 Engine", v8Bridge.isV8Initialized())
                StatusRow("ByteTransfer System", byteTransferBridge.isInitialized())
                StatusRow("Native Library", true)
                StatusRow("JNI Integration", true)
            }
        }
        
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Features Available",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                FeatureRow("JavaScript Execution", "V8 mock engine with full API")
                FeatureRow("Byte Transfer", "Shared memory buffers")
                FeatureRow("Named Buffers", "Multiple buffer management")
                FeatureRow("Inter-Library Communication", "V8 ↔ ByteTransfer")
                FeatureRow("Buffer Statistics", "Real-time monitoring")
                FeatureRow("Comprehensive Testing", "Full test suites")
            }
        }
    }
}

@Composable
fun SystemStatusCard(
    title: String,
    isInitialized: Boolean,
    onInitialize: () -> Unit,
    onCleanup: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isInitialized) "✅ Initialized" else "❌ Not Initialized",
                    color = if (isInitialized) Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontWeight = FontWeight.Medium
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onInitialize,
                    enabled = !isInitialized,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Initialize")
                }
                
                Button(
                    onClick = onCleanup,
                    enabled = isInitialized,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cleanup")
                }
            }
        }
    }
}

@Composable
fun ResultCard(title: String, content: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun TestResultCard(testName: String, result: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = testName.uppercase().replace("_", " "),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = result,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun StatusRow(label: String, isActive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label)
        Text(
            text = if (isActive) "✅ Active" else "❌ Inactive",
            color = if (isActive) Color(0xFF4CAF50) else Color(0xFFF44336),
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun FeatureRow(feature: String, description: String) {
    Column {
        Text(
            text = "✅ $feature",
            fontWeight = FontWeight.Medium,
            color = Color(0xFF4CAF50)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun QuickJSTestTab(quickJSBridge: QuickJSBridge) {
    var isQuickJSInitialized by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var customScript by remember { mutableStateOf("const sum = (a, b) => a + b; sum(15, 27)") }
    var customResult by remember { mutableStateOf("") }
    var engineInfo by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "QuickJS JavaScript Engine",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "QuickJS Engine Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isQuickJSInitialized) "✅ Initialized" else "❌ Not Initialized",
                            color = if (isQuickJSInitialized) Color(0xFF4CAF50) else Color(
                                0xFFF44336
                            ),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                isQuickJSInitialized = quickJSBridge.initialize()
                                engineInfo = quickJSBridge.getEngineInfo()
                            },
                            enabled = !isQuickJSInitialized,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Initialize")
                        }

                        Button(
                            onClick = {
                                quickJSBridge.cleanup()
                                isQuickJSInitialized = false
                                testResults = emptyMap()
                                customResult = ""
                                engineInfo = ""
                            },
                            enabled = isQuickJSInitialized,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cleanup")
                        }
                    }

                    if (engineInfo.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Engine Information",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(text = engineInfo)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Custom JavaScript Execution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = customScript,
                        onValueChange = { customScript = it },
                        label = { Text("JavaScript Code (ES2023 supported)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )

                    Button(
                        onClick = {
                            customResult = if (isQuickJSInitialized) {
                                quickJSBridge.runJavaScript(customScript)
                            } else {
                                "❌ Please initialize QuickJS first"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Execute JavaScript")
                    }

                    if (customResult.isNotEmpty()) {
                        ResultCard("Execution Result", customResult)
                    }
                }
            }
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "QuickJS Test Suite",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Button(
                        onClick = {
                            testResults = if (isQuickJSInitialized) {
                                quickJSBridge.runTestSuite()
                            } else {
                                mapOf("error" to "❌ Please initialize QuickJS first")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Run Complete Test Suite")
                    }

                    Button(
                        onClick = {
                            val quickJSResults = if (isQuickJSInitialized) {
                                quickJSBridge.runQuickJSSpecificTests()
                            } else {
                                "❌ Please initialize QuickJS first"
                            }
                            testResults = mapOf("quickjs_specific" to quickJSResults)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Run QuickJS Specific Tests")
                    }

                    Button(
                        onClick = {
                            val memoryStats = if (isQuickJSInitialized) {
                                quickJSBridge.getMemoryStats()
                            } else {
                                "❌ Please initialize QuickJS first"
                            }
                            testResults = mapOf("memory_stats" to memoryStats)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Get Memory Statistics")
                    }
                }
            }
        }

        items(testResults.toList()) { (testName, result) ->
            TestResultCard(testName, result)
        }
    }
}

@Composable
fun RemoteJSTestTab(quickJSBridge: QuickJSBridge) {
    var isQuickJSInitialized by remember { mutableStateOf(false) }
    var remoteUrl by remember { mutableStateOf("https://cdn.jsdelivr.net/npm/lodash@4.17.21/lodash.min.js") }
    var progressMessage by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    var executionResults by remember { mutableStateOf<List<QuickJSBridge.RemoteExecutionResult>>(emptyList()) }
    var selectedPopularUrl by remember { mutableStateOf("") }
    
    // Local server configuration
    var localServerIp by remember { mutableStateOf("192.168.1.100") }
    var localServerPort by remember { mutableStateOf("8000") }
    var showLocalServerConfig by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Remote JavaScript Execution",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "QuickJS Engine Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isQuickJSInitialized) "✅ Ready for Remote Execution" else "❌ Not Initialized",
                            color = if (isQuickJSInitialized) Color(0xFF4CAF50) else Color(0xFFF44336),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    if (!isQuickJSInitialized) {
                        Button(
                            onClick = { isQuickJSInitialized = quickJSBridge.initialize() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Initialize QuickJS")
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    progressMessage = "🔄 Resetting JavaScript context..."
                                    val success = quickJSBridge.resetContext()
                                    if (success) {
                                        progressMessage = "✅ Context reset successfully"
                                    } else {
                                        progressMessage = "❌ Failed to reset context"
                                        isQuickJSInitialized = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear Context")
                            }
                            
                            Button(
                                onClick = {
                                    quickJSBridge.cleanup()
                                    isQuickJSInitialized = false
                                    executionResults = emptyList()
                                    progressMessage = ""
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cleanup")
                            }
                        }
                    }
                }
            }
        }

        if (isQuickJSInitialized) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Popular JavaScript Libraries",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        quickJSBridge.getPopularJavaScriptUrls().forEach { (name, url) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (name == "Test Remote Script") {
                                        Text(
                                            text = "Local server: test_remote_script.js",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        if (name == "Test Remote Script") {
                                            // Handle local server option
                                            remoteUrl = quickJSBridge.buildLocalServerUrl(localServerIp, localServerPort)
                                            selectedPopularUrl = name
                                            showLocalServerConfig = true
                                        } else if (name == "Test HTTP Polyfills") {
                                            // Handle HTTP polyfills test
                                            remoteUrl = quickJSBridge.buildLocalServerUrl(localServerIp, localServerPort, "test_fetch_polyfill.js")
                                            selectedPopularUrl = name
                                            showLocalServerConfig = true
                                        } else {
                                            remoteUrl = url
                                            selectedPopularUrl = name
                                            showLocalServerConfig = false
                                        }
                                    },
                                    enabled = !isExecuting
                                ) {
                                    Text("Use")
                                }
                            }
                        }
                    }
                }
            }

            // Local Server Configuration Card
            if (showLocalServerConfig) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "🖥️ Local Server Configuration",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            Text(
                                text = "Configure your local server IP and port for test_remote_script.js",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = localServerIp,
                                    onValueChange = { 
                                        localServerIp = it
                                        if (selectedPopularUrl == "Test Remote Script") {
                                            remoteUrl = quickJSBridge.buildLocalServerUrl(localServerIp, localServerPort)
                                        } else if (selectedPopularUrl == "Test HTTP Polyfills") {
                                            remoteUrl = quickJSBridge.buildLocalServerUrl(localServerIp, localServerPort, "test_fetch_polyfill.js")
                                        }
                                    },
                                    label = { Text("IP Address") },
                                    placeholder = { Text("192.168.1.100") },
                                    modifier = Modifier.weight(2f),
                                    enabled = !isExecuting,
                                    singleLine = true
                                )
                                
                                OutlinedTextField(
                                    value = localServerPort,
                                    onValueChange = { 
                                        localServerPort = it
                                        if (selectedPopularUrl == "Test Remote Script") {
                                            remoteUrl = quickJSBridge.buildLocalServerUrl(localServerIp, localServerPort)
                                        } else if (selectedPopularUrl == "Test HTTP Polyfills") {
                                            remoteUrl = quickJSBridge.buildLocalServerUrl(localServerIp, localServerPort, "test_fetch_polyfill.js")
                                        }
                                    },
                                    label = { Text("Port") },
                                    placeholder = { Text("8000") },
                                    modifier = Modifier.weight(1f),
                                    enabled = !isExecuting,
                                    singleLine = true
                                )
                            }

                            // Show current URL preview
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Generated URL:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = quickJSBridge.buildLocalServerUrl(localServerIp, localServerPort),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Server instructions
                            Text(
                                text = "💡 Make sure your server is running:\n" +
                                      "• Python: python3 -m http.server ${localServerPort}\n" +
                                      "• Node.js: node js_server.js\n" +
                                      "• Auto: ./start_server.sh",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Remote JavaScript URL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (selectedPopularUrl.isNotEmpty()) {
                            Text(
                                text = "Selected: $selectedPopularUrl",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = remoteUrl,
                            onValueChange = { 
                                remoteUrl = it
                                selectedPopularUrl = ""
                            },
                            label = { Text("JavaScript URL (HTTP/HTTPS)") },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isExecuting,
                            minLines = 2
                        )

                        if (progressMessage.isNotEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Text(
                                    text = progressMessage,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    isExecuting = true
                                    progressMessage = ""
                                    
                                    quickJSBridge.executeRemoteJavaScript(remoteUrl, object : QuickJSBridge.RemoteExecutionCallback {
                                        override fun onProgress(message: String) {
                                            progressMessage = message
                                        }

                                        override fun onSuccess(result: QuickJSBridge.RemoteExecutionResult) {
                                            isExecuting = false
                                            progressMessage = "✅ Execution completed successfully!"
                                            executionResults = quickJSBridge.getExecutionHistory()
                                        }

                                        override fun onError(url: String, error: String) {
                                            isExecuting = false
                                            progressMessage = "❌ $error"
                                        }
                                    })
                                },
                                enabled = !isExecuting && remoteUrl.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isExecuting) {
                                    Text("Executing...")
                                } else {
                                    Text("Execute Remote JS")
                                }
                            }

                            Button(
                                onClick = {
                                    isExecuting = true
                                    progressMessage = ""
                                    
                                    quickJSBridge.testRemoteExecution(object : QuickJSBridge.RemoteExecutionCallback {
                                        override fun onProgress(message: String) {
                                            progressMessage = message
                                        }

                                        override fun onSuccess(result: QuickJSBridge.RemoteExecutionResult) {
                                            isExecuting = false
                                            progressMessage = "✅ Test completed successfully!"
                                            executionResults = quickJSBridge.getExecutionHistory()
                                        }

                                        override fun onError(url: String, error: String) {
                                            isExecuting = false
                                            progressMessage = "❌ $error"
                                        }
                                    })
                                },
                                enabled = !isExecuting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Run Test")
                            }
                        }
                    }
                }
            }

            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Execution History",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            
                            if (executionResults.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        quickJSBridge.clearExecutionHistory()
                                        executionResults = emptyList()
                                    }
                                ) {
                                    Text("Clear")
                                }
                            }
                        }

                        if (executionResults.isEmpty()) {
                            Text(
                                text = "No executions yet. Try executing some remote JavaScript!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        items(executionResults) { result ->
            RemoteExecutionResultCard(result)
        }
    }
}

@Composable
fun RemoteExecutionResultCard(result: QuickJSBridge.RemoteExecutionResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (result.success) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = result.fileName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (result.success) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = if (result.success) "✅" else "❌",
                    style = MaterialTheme.typography.labelMedium
                )
            }
            
            Text(
                text = "URL: ${result.url}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "Size: ${result.contentLength} chars | Time: ${result.executionTimeMs}ms",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (result.result.isNotEmpty()) {
                Text(
                    text = "Result: ${result.result.take(200)}${if (result.result.length > 200) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun V8IntegrationTestScreenPreview() {
    MyApplicationTheme {
        val mockV8Bridge = V8Bridge()
        val mockByteTransferBridge = ByteTransferBridge()
        val mockQuickJSBridge = QuickJSBridge()
        V8IntegrationTestScreen(
            v8Bridge = mockV8Bridge,
            byteTransferBridge = mockByteTransferBridge,
            quickJSBridge = mockQuickJSBridge
        )
    }
}