package com.actiquest.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.actiquest.data.GoogleDriveConfig
import com.actiquest.data.GoogleDriveUtils
import com.actiquest.data.Repository
import com.actiquest.domain.Quest
import com.actiquest.ui.components.VideoPlayerView
import com.actiquest.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(navController: NavController, questCode: String, maxSeconds: Int) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var quest by remember { mutableStateOf<Quest?>(null) }
    LaunchedEffect(questCode) {
        quest = Repository.getQuest(questCode)
    }

    var hasPermissions by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        hasPermissions = perms[Manifest.permission.CAMERA] == true && perms[Manifest.permission.RECORD_AUDIO] == true
    }

    var activeTab by remember { mutableStateOf("drive") } // "drive" or "camera"
    var driveUrlInput by remember { mutableStateOf("") }

    LaunchedEffect(activeTab) {
        if (activeTab == "camera" && !hasPermissions) {
            launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    var recording by remember { mutableStateOf<Recording?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableIntStateOf(maxSeconds) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }

    val pickVideoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            videoUri = uri
        }
    }
    var isSubmitting by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
            } catch (e: Exception) {
                Log.e("RecordScreen", "Failed to get ProcessCameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Bind camera whenever lens facing, previewView, cameraProvider, or permissions change
    LaunchedEffect(lensFacing, previewView, cameraProvider, hasPermissions) {
        val pView = previewView ?: return@LaunchedEffect
        val provider = cameraProvider ?: return@LaunchedEffect
        if (!hasPermissions) return@LaunchedEffect

        try {
            provider.unbindAll()

            val desiredSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val finalSelector = if (provider.hasCamera(desiredSelector)) {
                desiredSelector
            } else {
                val fallbackSelector = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                if (provider.hasCamera(fallbackSelector)) {
                    fallbackSelector
                } else {
                    desiredSelector
                }
            }

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = pView.surfaceProvider
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD, FallbackStrategy.higherQualityOrLowerThan(Quality.SD)))
                .build()
            val capture = VideoCapture.withOutput(recorder)
            videoCapture = capture

            provider.bindToLifecycle(
                lifecycleOwner,
                finalSelector,
                preview,
                capture
            )
        } catch (exc: Exception) {
            Log.e("RecordScreen", "Use case binding failed for lens $lensFacing", exc)
        }
    }

    // Intercept hardware back button
    BackHandler {
        if (isRecording || videoUri != null) {
            showDiscardDialog = true
        } else {
            navController.popBackStack()
        }
    }

    // Countdown Timer logic
    LaunchedEffect(isRecording) {
        if (isRecording) {
            timeLeft = maxSeconds
            while (timeLeft > 0 && isRecording) {
                delay(1000)
                timeLeft--
            }
            if (timeLeft == 0 && isRecording) {
                recording?.stop()
                recording = null
                isRecording = false
            }
        }
    }

    // Discard Dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text("Discard Video Recording?", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    "Are you sure you want to exit? Any unsubmitted video for this quest will be discarded.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        recording?.stop()
                        navController.popBackStack()
                    }
                ) {
                    Text("Discard", color = StatusRejected, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep Recording", color = Color.White)
                }
            },
            containerColor = SurfaceCharcoal
        )
    }

    BackHandler {
        if (isRecording || videoUri != null) {
            showDiscardDialog = true
        } else {
            if (!navController.popBackStack()) {
                navController.navigate("home") { popUpTo(0) }
            }
        }
    }

    // 1. Google Drive Link Submission View
    if (activeTab == "drive" && videoUri == null) {
        val isValidDriveUrl = GoogleDriveUtils.isValidGoogleDriveUrl(driveUrlInput)
        val fileId = GoogleDriveUtils.extractDriveFileId(driveUrlInput)
        val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager

        Scaffold(
            containerColor = DarkNavyBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "SUBMIT QUEST EVIDENCE",
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (!navController.popBackStack()) {
                                navController.navigate("home") { popUpTo(0) }
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceCharcoal)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Quest Summary Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCharcoal),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CobaltBlue.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, CobaltBlue)
                            ) {
                                Text(
                                    text = questCode,
                                    color = ElectricBlue,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MintEmerald.copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MintEmerald)
                            ) {
                                Text(
                                    text = "+${quest?.pointValue ?: 50} PTS",
                                    color = MintEmerald,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = quest?.title ?: "Campus Fitness Quest",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = quest?.instructions ?: "Complete the exercise reps at the landmark location.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Submission Mode Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .padding(4.dp)
                ) {
                    Button(
                        onClick = { activeTab = "drive" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == "drive") CobaltBlue else Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Google Drive Link", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { activeTab = "camera" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeTab == "camera") CobaltBlue else Color.Transparent,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(40.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record in App", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Google Drive Instructions Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCharcoal),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateSecondary.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = ElectricBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Google Drive Video Sharing Guide",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val steps = listOf(
                            "1. Upload your exercise video to your personal Google Drive (via app or drive.google.com).",
                            "2. Open file sharing settings and keep General access set to Restricted.",
                            "3. Share the file with the designated ActiQuest Reviewer as Viewer:",
                            "4. Copy the link (e.g., https://drive.google.com/file/d/.../view) and paste it below."
                        )

                        steps.take(3).forEach { step ->
                            Text(
                                text = step,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }

                        // Highlighted Admin Email Chip with Copy Action
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CobaltBlue.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CobaltBlue.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "REVIEWER GOOGLE ACCOUNT (VIEWER):",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ElectricBlue
                                    )
                                    Text(
                                        GoogleDriveConfig.adminReviewerEmail,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboardManager?.setPrimaryClip(
                                            ClipData.newPlainText("Admin Email", GoogleDriveConfig.adminReviewerEmail)
                                        )
                                        Toast.makeText(context, "Reviewer email copied to clipboard!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Email", tint = MintEmerald, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Text(
                            text = steps[3],
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Self-Reported Notice
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = AmberWarning.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = AmberWarning,
                                    modifier = Modifier.size(16.dp).padding(top = 1.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Self-Reported Checklist: ActiQuest records your file link for faculty evaluation. The app cannot independently verify Google Drive file permissions, so please ensure access is granted to ${GoogleDriveConfig.adminReviewerEmail} as Viewer.",
                                    fontSize = 11.sp,
                                    color = AmberWarning,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Input Field for Drive Link
                Text(
                    "Paste Google Drive Video URL",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = driveUrlInput,
                    onValueChange = { driveUrlInput = it.trim() },
                    placeholder = {
                        Text(
                            "https://drive.google.com/file/d/...",
                            color = SlateLight,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Link,
                            contentDescription = null,
                            tint = if (isValidDriveUrl) MintEmerald else ElectricBlue
                        )
                    },
                    trailingIcon = {
                        if (driveUrlInput.isNotEmpty()) {
                            IconButton(onClick = { driveUrlInput = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = SlateLight)
                            }
                        }
                    },
                    isError = driveUrlInput.isNotEmpty() && !isValidDriveUrl,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CobaltBlue,
                        unfocusedBorderColor = SlateSecondary,
                        errorBorderColor = StatusRejected,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = SurfaceElevated,
                        unfocusedContainerColor = SurfaceElevated
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Validation Status Indicator
                if (driveUrlInput.isNotEmpty()) {
                    if (isValidDriveUrl) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MintEmerald.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MintEmerald.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MintEmerald, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Valid Google Drive File ID: ${fileId?.take(15)}...",
                                    color = MintEmerald,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = StatusRejected.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusRejected.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = StatusRejected, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Invalid Drive URL. Use https://drive.google.com/file/d/FILE_ID/view",
                                    color = StatusRejected,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Submit Button
                Button(
                    onClick = {
                        if (!isValidDriveUrl || isSubmitting) return@Button
                        isSubmitting = true
                        coroutineScope.launch {
                            try {
                                val currentUser = Repository.currentUser.value
                                val userId = currentUser?.uid ?: "student_default"
                                val studentName = currentUser?.name ?: "Juan de la Cruz"
                                val studentClassId = currentUser?.classId ?: ""

                                Repository.submitQuestDriveVideo(
                                    userId = userId,
                                    studentName = studentName,
                                    questCode = questCode,
                                    questTitle = quest?.title ?: questCode,
                                    pointValue = quest?.pointValue ?: 50,
                                    driveUrl = driveUrlInput,
                                    context = context,
                                    classId = studentClassId
                                )

                                Toast.makeText(
                                    context,
                                    "Google Drive Video Submitted for Verification!",
                                    Toast.LENGTH_LONG
                                ).show()

                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            } catch (e: Exception) {
                                Log.e("RecordScreen", "Submission failed", e)
                                Toast.makeText(context, "Submission failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = isValidDriveUrl && !isSubmitting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintEmerald,
                        disabledContainerColor = SlateSecondary.copy(alpha = 0.4f),
                        contentColor = Color.White,
                        disabledContentColor = SlateLight
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Submitting to Review Desk...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Submit Video Evidence", fontWeight = FontWeight.Black, fontSize = 15.sp)
                    }
                }
            }
        }
        return
    }

    if (!hasPermissions) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkNavyBackground)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceCharcoal),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = CobaltBlue,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Camera & Mic Required",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "To record your quest reps using the in-app camera, please grant camera and microphone access. Alternatively, you can upload your video to Google Drive and paste the link.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { launcher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
                        colors = ButtonDefaults.buttonColors(containerColor = CobaltBlue, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Grant Permissions", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { pickVideoLauncher.launch("video/*") },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CobaltBlue)
                    ) {
                        Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Video File from Device", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { activeTab = "drive" },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SlateSecondary)
                    ) {
                        Text("Switch to Google Drive Link", color = Color.White)
                    }
                }
            }
        }
        return
    }

    if (videoUri != null) {
        // Video Preview & Submission Screen
        Scaffold(
            containerColor = DarkNavyBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text("REVIEW VIDEO", fontWeight = FontWeight.Black, color = Color.White, fontSize = 16.sp)
                    },
                    navigationIcon = {
                        IconButton(onClick = { showDiscardDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceCharcoal)
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        VideoPlayerView(videoUriString = videoUri.toString())
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceCharcoal),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            quest?.title ?: "Quest Challenge",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Ready to submit for Admin review and earn +${quest?.pointValue ?: 50} pts for your class!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    videoUri = null
                                    timeLeft = maxSeconds
                                },
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, SlateSecondary),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retake", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    isSubmitting = true
                                    coroutineScope.launch {
                                        val currentUser = Repository.currentUser.value
                                        val userId = currentUser?.uid ?: "student_default"
                                        val studentName = currentUser?.name ?: "Juan de La Cruz"
                                        val studentClassId = currentUser?.classId ?: ""

                                        Repository.submitQuestVideo(
                                            userId = userId,
                                            studentName = studentName,
                                            questCode = questCode,
                                            questTitle = quest?.title ?: questCode,
                                            pointValue = quest?.pointValue ?: 50,
                                            videoUri = videoUri,
                                            context = context,
                                            classId = studentClassId
                                        )

                                        Toast.makeText(context, "Quest Video Submitted Successfully!", Toast.LENGTH_LONG).show()
                                        navController.navigate("home") {
                                            popUpTo("home") { inclusive = true }
                                        }
                                    }
                                },
                                enabled = !isSubmitting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintEmerald,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(50.dp)
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                                } else {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Submit Quest", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Live Camera Recording View
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }.also {
                    previewView = it
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Header Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isRecording) showDiscardDialog = true else navController.popBackStack()
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }

            // Countdown Timer Chip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isRecording) StatusRejected.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isRecording) StatusRejected else SlateSecondary)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) Color.White else MintEmerald)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "${timeLeft}s / ${maxSeconds}s",
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        fontSize = 13.sp
                    )
                }
            }

            // Flip Camera Toggle Button
            IconButton(
                onClick = {
                    if (!isRecording) {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    } else {
                        Toast.makeText(context, "Cannot flip camera during active recording", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    Icons.Default.FlipCameraAndroid,
                    contentDescription = "Flip Camera",
                    tint = if (isRecording) SlateSecondary else Color.White
                )
            }
        }

        // Bottom Controls (Record Shutter)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (isRecording) "Recording... Tap to stop" else "Tap button to start recording reps",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                if (!isRecording) {
                    IconButton(
                        onClick = { pickVideoLauncher.launch("video/*") },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.5.dp, CobaltBlue, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = "Pick Video from Device",
                            tint = ElectricBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(if (isRecording) StatusRejected.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.2f))
                        .border(4.dp, if (isRecording) StatusRejected else Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (!isRecording) {
                                val cap = videoCapture
                                if (cap != null) {
                                    val videoFile = File(context.cacheDir, "quest_${questCode}_${System.currentTimeMillis()}.mp4")
                                    val outputOptions = FileOutputOptions.Builder(videoFile).build()

                                    val activeRecording = cap.output
                                        .prepareRecording(context, outputOptions)
                                        .apply {
                                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                withAudioEnabled()
                                            }
                                        }
                                        .start(ContextCompat.getMainExecutor(context)) { event ->
                                            when (event) {
                                                is VideoRecordEvent.Start -> {
                                                    isRecording = true
                                                }
                                                is VideoRecordEvent.Finalize -> {
                                                    isRecording = false
                                                    if (!event.hasError()) {
                                                        videoUri = Uri.fromFile(videoFile)
                                                    } else {
                                                        Log.e("RecordScreen", "Video recording failed: ${event.error}")
                                                    }
                                                }
                                            }
                                        }
                                    recording = activeRecording
                                }
                            } else {
                                recording?.stop()
                                recording = null
                                isRecording = false
                            }
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isRecording) 28.dp else 52.dp)
                                .clip(if (isRecording) RoundedCornerShape(6.dp) else CircleShape)
                                .background(if (isRecording) StatusRejected else CobaltBlue)
                        )
                    }
                }

                if (!isRecording) {
                    IconButton(
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .border(1.5.dp, SlateSecondary, CircleShape)
                    ) {
                        Icon(
                            Icons.Default.FlipCameraAndroid,
                            contentDescription = "Flip Camera",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
