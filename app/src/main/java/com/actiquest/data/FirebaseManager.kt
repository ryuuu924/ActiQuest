package com.actiquest.data

import android.net.Uri
import com.actiquest.domain.*
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

sealed class AuthResult {
    data class Success(val user: User, val isAdmin: Boolean) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

object Repository {
    private var appContext: android.content.Context? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
    }

    private val firestore by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    private val storage by lazy {
        try {
            FirebaseStorage.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    private val auth by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            null
        }
    }

    // In-memory fallback state for immediate preview/offline functionality
    private val _localUsers = MutableStateFlow<Map<String, User>>(
        mapOf(
            "student_default" to User(
                uid = "student_default",
                name = "Juan de la Cruz",
                firstName = "Juan",
                lastName = "de la Cruz",
                email = "juan.delacruz@dlsl.edu.ph",
                college = "CITE",
                program = "BS Computer Science",
                yearLevel = "1st Year",
                section = "Section A",
                classId = "CITE-BSCS-1A",
                totalPoints = 230,
                streak = 4,
                approvedCount = 3,
                badges = listOf("First Steps"),
                consentGiven = true
            ),
            "admin_default" to User(
                uid = "admin_default",
                name = "Wellness Admin",
                firstName = "Wellness",
                lastName = "Admin",
                email = "admin.wellness@dlsl.edu.ph",
                college = "Staff",
                program = "Wellness & Sports Division",
                yearLevel = "Staff",
                section = "Admin",
                classId = "ADMIN-STAFF",
                totalPoints = 0,
                streak = 1,
                approvedCount = 0,
                badges = emptyList(),
                consentGiven = true
            )
        )
    )

    private val _localAdmins = MutableStateFlow<Set<String>>(
        setOf("admin_default", "admin_demo", "yuriyabut924@gmail.com", "admin.wellness@dlsl.edu.ph", "coach.sarah@dlsl.edu.ph")
    )

    private val _localPasswords = MutableStateFlow<Map<String, String>>(
        mapOf(
            "juan.delacruz@dlsl.edu.ph" to "pass123",
            "admin.wellness@dlsl.edu.ph" to "admin123"
        )
    )

    private val _localClasses = MutableStateFlow<List<ClassModel>>(
        listOf(
            ClassModel("CITE-BSCS-1A", "CITE - BSCS 1A", "CITE", "BS Computer Science", "1st Year", "Section A", 1950, 34),
            ClassModel("CBEAM-BSA-2A", "CBEAM - BSA 2A", "CBEAM", "BS Accountancy", "2nd Year", "Section A", 1620, 29),
            ClassModel("CEAS-ABCOMM-1A", "CEAS - ABCOMM 1A", "CEAS", "AB Communication", "1st Year", "Section A", 1540, 26),
            ClassModel("CEAS-BMA-4A", "CEAS - BMMA 4A", "CEAS", "Bachelor of Multimedia Arts", "4th Year", "Section A", 1490, 28),
            ClassModel("CON-BSN-1B", "CON - BSN 1B", "CON", "BS Nursing", "1st Year", "Section B", 1320, 30),
            ClassModel("CIHTM-BSHM-2C", "CIHTM - BSHM 2C", "CIHTM", "BS Hospitality Management", "2nd Year", "Section C", 1180, 27),
            ClassModel("CCJE-BSFS-3A", "CCJE - BSFS 3A", "CCJE", "BS Forensic Science", "3rd Year", "Section A", 1040, 25),
            ClassModel("CITE-BSIT-3A", "CITE - BSIT 3A", "CITE", "BS Information Technology", "3rd Year", "Section A", 890, 26)
        )
    )

    private val _localNotifications = MutableStateFlow<List<AppNotification>>(
        listOf(
            AppNotification(
                id = "notif_01",
                title = "Quest Verified & Approved! 🏆",
                message = "Your submission for 'Diokno Steps Challenge' has been verified by Admin. +50 Points awarded to your class!",
                category = "Review",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 25,
                isRead = false
            ),
            AppNotification(
                id = "notif_02",
                title = "New Campus Quest Available! 🧭",
                message = "Explore the LRC Stair Climb challenge now active near Learning Resource Center.",
                category = "Quest",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 3,
                isRead = false
            ),
            AppNotification(
                id = "notif_03",
                title = "Active Streak Maintained! 🔥",
                message = "You have maintained an active fitness streak of 4 days. Keep going to earn the Stamina Master badge!",
                category = "Streak",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 8,
                isRead = true
            ),
            AppNotification(
                id = "notif_04",
                title = "Weekly Standings Updated 📊",
                message = "CITE - BSCS 1A is currently #1 in weekly campus class standings!",
                category = "Leaderboard",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 20,
                isRead = true
            ),
            AppNotification(
                id = "notif_05",
                title = "Welcome to ActiQuest! ✨",
                message = "Scan campus QR markers or upload pedometer fitness proof to contribute to your section's wellness score.",
                category = "System",
                timestamp = System.currentTimeMillis() - 1000 * 60 * 60 * 48,
                isRead = true
            )
        )
    )

    private val _localSubmissions = MutableStateFlow<List<Submission>>(
        listOf(
            Submission(
                submissionId = "sub_demo_01",
                userId = "student_default",
                studentName = "Juan de la Cruz",
                questCode = "QQ-DIOKNO",
                questTitle = "Diokno Steps Challenge",
                videoPath = "sample_video_local",
                pointValue = 50,
                status = "pending",
                submittedAt = DateTimeUtils.formatPhilippineTimestamp(System.currentTimeMillis() - 1000 * 60 * 15),
                submissionType = "video"
            ),
            Submission(
                submissionId = "sub_demo_02",
                userId = "student_default",
                studentName = "Juan de la Cruz",
                questCode = "",
                questTitle = "Daily 10,000 Steps Goal",
                pointValue = 100,
                status = "pending",
                submittedAt = DateTimeUtils.formatPhilippineTimestamp(System.currentTimeMillis() - 1000 * 60 * 35),
                submissionType = "screenshot",
                imagePath = "sample_screenshot_steps",
                activityType = "Steps Count",
                activityMetricValue = "10,480 Steps",
                studentNotes = "Logged morning campus walk and library laps via Google Fit pedometer."
            ),
            Submission(
                submissionId = "sub_demo_03",
                userId = "student_marcus",
                studentName = "Marcus Chen",
                questCode = "QQ-CBEAM",
                questTitle = "CBEAM Stretch",
                videoPath = "sample_video_local_2",
                pointValue = 50,
                status = "pending",
                submittedAt = DateTimeUtils.formatPhilippineTimestamp(System.currentTimeMillis() - 1000 * 60 * 60),
                submissionType = "video"
            ),
            Submission(
                submissionId = "sub_demo_04",
                userId = "student_clara",
                studentName = "Clara Santos",
                questCode = "",
                questTitle = "45-Minute Cardio & Active Minutes",
                pointValue = 90,
                status = "pending",
                submittedAt = DateTimeUtils.formatPhilippineTimestamp(System.currentTimeMillis() - 1000 * 60 * 120),
                submissionType = "screenshot",
                imagePath = "sample_screenshot_active",
                activityType = "Active Minutes",
                activityMetricValue = "45 Active Mins",
                studentNotes = "Tracked through Strava during campus fitness session."
            ),
            Submission(
                submissionId = "sub_demo_05",
                userId = "student_clara",
                studentName = "Clara Santos",
                questCode = "EQ-LRC",
                questTitle = "LRC Stair Climb",
                videoPath = "sample_video_local_3",
                pointValue = 100,
                status = "pending",
                submittedAt = DateTimeUtils.formatPhilippineTimestamp(System.currentTimeMillis() - 1000 * 60 * 180),
                submissionType = "video"
            )
        )
    )

    private val _localAcademicRequests = MutableStateFlow<List<AcademicChangeRequest>>(
        listOf(
            AcademicChangeRequest(
                requestId = "req_demo_01",
                userId = "student_clara",
                studentName = "Clara Santos",
                studentEmail = "clara.santos@dlsl.edu.ph",
                currentCollege = "CITE",
                currentProgram = "BS Information Technology",
                currentYearLevel = "2nd Year",
                currentSection = "2A",
                requestedCollege = "CITE",
                requestedProgram = "BS Computer Science",
                requestedYearLevel = "2nd Year",
                requestedSection = "2B",
                reason = "Shifting major track from IT to Computer Science for 2nd semester.",
                status = "pending",
                requestedAt = System.currentTimeMillis() - 1000 * 60 * 60 * 3
            )
        )
    )

    // Current active session role
    private val _currentUserId = MutableStateFlow("student_default")
    val currentUserId = _currentUserId.asStateFlow()

    private val _currentUser = MutableStateFlow<User?>(_localUsers.value["student_default"])
    val currentUser = _currentUser.asStateFlow()

    private val _isAdminMode = MutableStateFlow(false)
    val isAdminMode = _isAdminMode.asStateFlow()

    fun switchUserSession(uid: String, asAdmin: Boolean = false) {
        _currentUserId.value = uid
        val user = _localUsers.value[uid] ?: User(uid = uid, name = if (asAdmin) "Admin" else "Student $uid")
        _currentUser.value = user
        _isAdminMode.value = asAdmin || _localAdmins.value.contains(uid)
    }

    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _currentUserId.value = ""
        _currentUser.value = null
        _isAdminMode.value = false
    }

    fun formatFirebaseErrorMessage(e: Exception): String {
        return when (e) {
            is FirebaseAuthInvalidUserException -> "No account found with this email. Please check your email or create a new account."
            is FirebaseAuthInvalidCredentialsException -> "Incorrect credentials. Please verify your email and password."
            is FirebaseAuthUserCollisionException -> "An account with this email already exists. Please sign in instead."
            is FirebaseAuthWeakPasswordException -> "Password is too weak. Please use at least 6 characters."
            is FirebaseNetworkException -> "Network error connecting to Firebase. Please check your internet connection."
            else -> {
                val msg = e.localizedMessage ?: "Authentication failed."
                when {
                    msg.contains("password", ignoreCase = true) && msg.contains("invalid", ignoreCase = true) ->
                        "Invalid password or email address."
                    msg.contains("user-not-found", ignoreCase = true) ->
                        "No account found with this email."
                    msg.contains("email-already-in-use", ignoreCase = true) ->
                        "This email is already registered. Please sign in."
                    msg.contains("network", ignoreCase = true) ->
                        "Network error. Please verify your connection."
                    else -> msg
                }
            }
        }
    }

    suspend fun sendPasswordReset(email: String): Result<String> {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return Result.failure(Exception("Please enter an email address."))
        val fa = auth
        if (fa != null) {
            return try {
                fa.sendPasswordResetEmail(trimmed).await()
                Result.success("Password reset email sent to $trimmed. Check your inbox.")
            } catch (e: Exception) {
                Result.failure(Exception(formatFirebaseErrorMessage(e)))
            }
        }
        return Result.success("Password reset instructions simulated for $trimmed.")
    }

    fun parseUserFromSnapshot(snap: DocumentSnapshot, uid: String, fallbackEmail: String = ""): User {
        val email = snap.getString("email") ?: fallbackEmail
        val rawName = snap.getString("name") ?: email.substringBefore("@").replace(".", " ").capitalizeWords()
        val firstName = snap.getString("firstName") ?: rawName.split(" ").firstOrNull() ?: "Student"
        val lastName = snap.getString("lastName") ?: rawName.split(" ").drop(1).joinToString(" ")
        val college = snap.getString("college") ?: "CITE"
        val program = snap.getString("program") ?: "BS Computer Science"
        val yearLevel = snap.getString("yearLevel") ?: "1st Year"
        val section = snap.getString("section") ?: "Section A"
        val computedClassId = buildClassId(college, program, yearLevel, section)
        val rawStoredClassId = snap.getString("classId") ?: ""
        val effectiveClassId = if (rawStoredClassId.isNotBlank() && normalizeClassId(rawStoredClassId) == normalizeClassId(computedClassId)) {
            rawStoredClassId
        } else {
            computedClassId
        }
        val totalPoints = snap.getLong("totalPoints")?.toInt() ?: 0
        val targetPoints = snap.getLong("targetPoints")?.toInt() ?: 500
        val streak = snap.getLong("streak")?.toInt() ?: 1
        val badges = (snap.get("badges") as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val approvedCount = snap.getLong("approvedCount")?.toInt() ?: 0
        val profilePicUri = snap.getString("profilePicUri") ?: ""

        return User(
            uid = uid,
            name = rawName,
            firstName = firstName,
            lastName = lastName,
            email = email,
            college = college,
            program = program,
            yearLevel = yearLevel,
            section = section,
            classId = effectiveClassId,
            totalPoints = totalPoints,
            targetPoints = targetPoints,
            streak = streak,
            badges = badges,
            approvedCount = approvedCount,
            profilePicUri = profilePicUri
        )
    }

    suspend fun checkIsAdmin(uid: String, email: String = ""): Boolean {
        val lowerEmail = email.trim().lowercase()
        if (_localAdmins.value.contains(uid)) return true
        if (lowerEmail.isNotEmpty() && _localAdmins.value.contains(lowerEmail)) return true

        val db = firestore ?: return _localAdmins.value.contains(uid) || (lowerEmail.isNotEmpty() && _localAdmins.value.contains(lowerEmail))
        return try {
            // 1. Check admins/{uid} document
            val docByUid = db.collection("admins").document(uid).get().await()
            if (docByUid.exists()) return true

            // 2. Check admins/{email} document
            if (lowerEmail.isNotEmpty()) {
                val docByEmail = db.collection("admins").document(lowerEmail).get().await()
                if (docByEmail.exists()) return true
            }

            // 3. Check users/{uid} for role == "admin" or isAdmin == true
            val userDoc = db.collection("users").document(uid).get().await()
            if (userDoc.exists()) {
                val role = userDoc.getString("role")
                val isAdminFlag = userDoc.getBoolean("isAdmin") ?: false
                if (role.equals("admin", ignoreCase = true) || isAdminFlag) return true
            }

            false
        } catch (e: Exception) {
            _localAdmins.value.contains(uid) || (lowerEmail.isNotEmpty() && _localAdmins.value.contains(lowerEmail))
        }
    }

    suspend fun signInWithEmail(email: String, pass: String, selectedRoleIsAdmin: Boolean): AuthResult {
        val trimmedEmail = email.trim()
        val lowerEmail = trimmedEmail.lowercase()
        
        // 1. Try real Firebase Auth if initialized
        val fa = auth
        if (fa != null && trimmedEmail.isNotEmpty() && pass.isNotEmpty()) {
            try {
                val res = fa.signInWithEmailAndPassword(trimmedEmail, pass).await()
                val uid = res.user?.uid ?: ""
                val isAdmin = checkIsAdmin(uid, trimmedEmail)

                if (selectedRoleIsAdmin && !isAdmin) {
                    return AuthResult.Error("Access Denied: Your account ($trimmedEmail) is not registered as an authorized Admin. Please switch to the Student tab.")
                }
                if (!selectedRoleIsAdmin && isAdmin) {
                    return AuthResult.Error("This is an Admin account. Please switch to the Admin tab above to sign in with your admin credentials.")
                }

                val db = firestore
                var userDoc: User? = null
                if (db != null && uid.isNotEmpty()) {
                    try {
                        val snap = db.collection("users").document(uid).get().await()
                        if (snap.exists()) {
                            userDoc = parseUserFromSnapshot(snap, uid, trimmedEmail)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (userDoc == null) {
                    val defaultName = res.user?.displayName?.ifEmpty { null }
                        ?: trimmedEmail.substringBefore("@").replace(".", " ").capitalizeWords()
                    val parts = defaultName.split(" ")
                    val fName = parts.firstOrNull() ?: defaultName
                    val lName = parts.drop(1).joinToString(" ")

                    userDoc = User(
                        uid = uid,
                        name = defaultName,
                        firstName = fName,
                        lastName = lName,
                        email = trimmedEmail,
                        college = if (isAdmin) "Staff" else "CITE",
                        program = if (isAdmin) "Wellness & Sports Division" else "BS Computer Science",
                        yearLevel = if (isAdmin) "Staff" else "1st Year",
                        section = if (isAdmin) "Admin" else "Section A",
                        classId = if (isAdmin) "ADMIN-STAFF" else "CITE-BSCS-1A"
                    )

                    // Write user to Firestore so subsequent loads retrieve the full profile
                    try {
                        db?.collection("users")?.document(uid)?.set(userDoc, SetOptions.merge())?.await()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                _currentUserId.value = uid
                _currentUser.value = userDoc
                _isAdminMode.value = isAdmin && selectedRoleIsAdmin
                val current = _localUsers.value.toMutableListMap()
                current[uid] = userDoc
                _localUsers.value = current

                return AuthResult.Success(userDoc, _isAdminMode.value)
            } catch (e: Exception) {
                return AuthResult.Error(formatFirebaseErrorMessage(e))
            }
        }

        // 2. Demo / offline fallback
        if (selectedRoleIsAdmin) {
            val isAuthorized = _localAdmins.value.contains(lowerEmail) || lowerEmail.contains("admin") || lowerEmail.contains("coach")
            if (isAuthorized) {
                val storedPass = _localPasswords.value[lowerEmail] ?: "admin123"
                if (pass.isNotEmpty() && pass != storedPass && pass != "admin123") {
                    return AuthResult.Error("Incorrect admin password. Please verify your credentials.")
                }
                switchUserSession("admin_default", asAdmin = true)
                return AuthResult.Success(_localUsers.value["admin_default"]!!, true)
            } else {
                return AuthResult.Error("Access Denied: Your account ($trimmedEmail) is not authorized as an Admin.")
            }
        } else {
            val isAttemptingAdmin = _localAdmins.value.contains(lowerEmail) || lowerEmail.contains("admin") || lowerEmail.contains("coach")
            if (isAttemptingAdmin) {
                return AuthResult.Error("This is an Admin account. Please switch to the Admin tab above to sign in with your admin credentials.")
            }

            // Check if user already registered locally in this session
            val existing = _localUsers.value.values.find { it.email.equals(trimmedEmail, ignoreCase = true) }
            val user = if (existing != null) {
                val storedPass = _localPasswords.value[lowerEmail]
                if (storedPass != null && storedPass != pass) {
                    return AuthResult.Error("Incorrect password. Please verify your credentials.")
                }
                existing
            } else {
                val uid = "student_" + UUID.randomUUID().toString().take(6)
                val rawName = trimmedEmail.substringBefore("@").replace(".", " ").capitalizeWords().ifEmpty { "Student Warrior" }
                val u = User(
                    uid = uid,
                    name = rawName,
                    firstName = rawName.split(" ").firstOrNull() ?: "Student",
                    lastName = rawName.split(" ").drop(1).joinToString(" "),
                    email = trimmedEmail,
                    college = "CITE",
                    program = "BS Computer Science",
                    yearLevel = "1st Year",
                    section = "Section A",
                    classId = "CITE-BSCS-1A"
                )
                val passMap = _localPasswords.value.toMutableMap()
                passMap[lowerEmail] = pass
                _localPasswords.value = passMap
                u
            }

            val current = _localUsers.value.toMutableListMap()
            current[user.uid] = user
            _localUsers.value = current
            switchUserSession(user.uid, asAdmin = false)
            return AuthResult.Success(user, false)
        }
    }

    suspend fun signUpStudent(
        name: String = "",
        firstName: String = "",
        lastName: String = "",
        email: String,
        pass: String,
        college: String,
        program: String,
        yearLevel: String,
        section: String
    ): AuthResult {
        val trimmedEmail = email.trim()
        val lowerEmail = trimmedEmail.lowercase()
        val classId = buildClassId(college, program, yearLevel, section)

        val resolvedFirstName = firstName.trim().ifEmpty { name.trim().split(" ").firstOrNull() ?: "" }
        val resolvedLastName = lastName.trim().ifEmpty { name.trim().split(" ").drop(1).joinToString(" ") }
        val resolvedFullName = if (resolvedFirstName.isNotBlank() || resolvedLastName.isNotBlank()) {
            "$resolvedFirstName $resolvedLastName".trim()
        } else {
            name.trim().ifEmpty { "New Student" }
        }

        val fa = auth
        if (fa != null && trimmedEmail.isNotEmpty() && pass.isNotEmpty()) {
            try {
                // 1. Create account with Firebase Auth
                val res = fa.createUserWithEmailAndPassword(trimmedEmail, pass).await()
                val firebaseUser = res.user
                val uid = firebaseUser?.uid ?: UUID.randomUUID().toString()

                // 2. Set Firebase Auth display name
                try {
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(resolvedFullName)
                        .build()
                    firebaseUser?.updateProfile(profileUpdates)?.await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val newUser = User(
                    uid = uid,
                    name = resolvedFullName,
                    firstName = resolvedFirstName.ifEmpty { "Student" },
                    lastName = resolvedLastName,
                    email = trimmedEmail,
                    college = college,
                    program = program,
                    yearLevel = yearLevel,
                    section = section,
                    classId = classId,
                    totalPoints = 0,
                    targetPoints = 500,
                    streak = 1,
                    badges = emptyList(),
                    approvedCount = 0,
                    consentGiven = true
                )

                // 3. Save student profile to Firestore 'users' collection
                val userMap = hashMapOf<String, Any>(
                    "uid" to uid,
                    "name" to resolvedFullName,
                    "firstName" to resolvedFirstName.ifEmpty { "Student" },
                    "lastName" to resolvedLastName,
                    "email" to trimmedEmail,
                    "college" to college,
                    "program" to program,
                    "yearLevel" to yearLevel,
                    "section" to section,
                    "classId" to classId,
                    "role" to "student",
                    "totalPoints" to 0L,
                    "targetPoints" to 500L,
                    "streak" to 1L,
                    "badges" to emptyList<String>(),
                    "approvedCount" to 0L,
                    "consentGiven" to true,
                    "profilePicUri" to "",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore?.collection("users")?.document(uid)?.set(userMap, SetOptions.merge())?.await()

                // 4. Upsert class in Firestore 'classes' collection with exact 8 fields and live memberCount
                try {
                    val classRef = firestore?.collection("classes")?.document(classId)
                    if (classRef != null) {
                        // Query live count of users registered in this class in Firestore
                        val usersInClassSnap = firestore?.collection("users")
                            ?.whereEqualTo("classId", classId)
                            ?.get()?.await()
                        val exactMemberCount = maxOf(1, usersInClassSnap?.size() ?: 1)

                        val classSnap = classRef.get().await()
                        val existingTotalPoints = classSnap.getLong("totalPoints")?.toInt() ?: 0
                        val className = "$college - ${getProgramAbbreviation(program)} ${getYearDigit(yearLevel)}${getSectionLetter(section)}"

                        val classData = ClassModel(
                            classId = classId,
                            college = college,
                            memberCount = exactMemberCount,
                            name = className,
                            program = program,
                            section = section,
                            totalPoints = existingTotalPoints,
                            yearLevel = yearLevel
                        )
                        classRef.set(classData, SetOptions.merge()).await()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                try { reconcileClassAggregates() } catch (_: Exception) {}

                _currentUserId.value = uid
                _currentUser.value = newUser
                _isAdminMode.value = false
                val currentUsers = _localUsers.value.toMutableListMap()
                currentUsers[uid] = newUser
                _localUsers.value = currentUsers

                return AuthResult.Success(newUser, false)
            } catch (e: Exception) {
                return AuthResult.Error(formatFirebaseErrorMessage(e))
            }
        }

        // Local registration fallback
        val uid = "student_" + UUID.randomUUID().toString().take(6)
        val newUser = User(
            uid = uid,
            name = resolvedFullName,
            firstName = resolvedFirstName.ifEmpty { "Student" },
            lastName = resolvedLastName,
            email = trimmedEmail,
            college = college,
            program = program,
            yearLevel = yearLevel,
            section = section,
            classId = classId,
            totalPoints = 0,
            targetPoints = 500,
            streak = 1,
            badges = emptyList(),
            approvedCount = 0,
            consentGiven = true
        )
        val currentUsers = _localUsers.value.toMutableListMap()
        currentUsers[uid] = newUser
        _localUsers.value = currentUsers

        val passMap = _localPasswords.value.toMutableMap()
        passMap[lowerEmail] = pass
        _localPasswords.value = passMap

        // Update or add to local classes list
        val currentClasses = _localClasses.value.toMutableList()
        val classIndex = currentClasses.indexOfFirst { it.classId == classId }
        if (classIndex >= 0) {
            val existingClass = currentClasses[classIndex]
            currentClasses[classIndex] = existingClass.copy(memberCount = existingClass.memberCount + 1)
        } else {
            val className = "$college - ${getProgramAbbreviation(program)} ${getYearDigit(yearLevel)}${getSectionLetter(section)}"
            currentClasses.add(ClassModel(classId, className, college, program, yearLevel, section, 0, 1))
        }
        _localClasses.value = currentClasses

        switchUserSession(uid, asAdmin = false)
        return AuthResult.Success(newUser, false)
    }

    fun buildClassId(college: String, program: String, year: String, sec: String): String {
        val cleanCollege = college.trim().replace("[^A-Za-z0-9]".toRegex(), "").uppercase()
        val progCode = getProgramAbbreviation(program).trim().replace(" ", "")
        val yearNum = getYearDigit(year).trim()
        val secCode = getSectionLetter(sec).trim()
        return "$cleanCollege-$progCode-$yearNum$secCode"
    }

    fun normalizeClassId(classId: String): String {
        val trimmed = classId.trim()
        return when {
            trimmed.equals("CITE-BS A-1A", ignoreCase = true) -> "CITE-BSARCH-1A"
            trimmed.contains("BS A-", ignoreCase = true) -> trimmed.replace("BS A-", "BSARCH-")
            trimmed.contains("BS A ", ignoreCase = true) -> trimmed.replace("BS A ", "BSARCH-")
            else -> trimmed.replace(" ", "")
        }
    }

    fun getProgramAbbreviation(program: String): String {
        val p = program.trim()
        return when {
            p.contains("Architecture", ignoreCase = true) -> "BSARCH"
            p.contains("Information Technology", ignoreCase = true) -> "BSIT"
            p.contains("Computer Science", ignoreCase = true) -> "BSCS"
            p.contains("Entertainment and Multimedia", ignoreCase = true) || p.contains("BSEMC", ignoreCase = true) -> "BSEMC"
            p.contains("Electronics Engineering", ignoreCase = true) || p.contains("BSECE", ignoreCase = true) -> "BSECE"
            p.contains("Electrical Engineering", ignoreCase = true) -> "BSEE"
            p.contains("Computer Engineering", ignoreCase = true) -> "BSCPE"
            p.contains("Industrial Engineering", ignoreCase = true) -> "BSIE"
            p.contains("Associate in Computer Technology", ignoreCase = true) || p.contains("ACT", ignoreCase = true) -> "ACT"
            p.contains("Accounting Information", ignoreCase = true) || p.contains("BSAIS", ignoreCase = true) -> "BSAIS"
            p.contains("Accountancy", ignoreCase = true) -> "BSA"
            p.contains("Legal Management", ignoreCase = true) -> "BSLM"
            p.contains("Financial Management", ignoreCase = true) -> "BSBA-FM"
            p.contains("Marketing Management", ignoreCase = true) -> "BSBA-MM"
            p.contains("Management Technology", ignoreCase = true) -> "BSMT"
            p.contains("Certificate in Entrepreneurship", ignoreCase = true) -> "CERT-ENTREP"
            p.contains("Entrepreneurship", ignoreCase = true) -> "BSENTREP"
            p.contains("Business Administration", ignoreCase = true) -> "BSBA"
            p.contains("Elementary Education", ignoreCase = true) -> "BEED"
            p.contains("Secondary Education", ignoreCase = true) -> "BSED"
            p.contains("Multimedia Arts", ignoreCase = true) -> "BMMA"
            p.contains("Communication", ignoreCase = true) -> "ABCOMM"
            p.contains("Biology", ignoreCase = true) -> "BSBIO"
            p.contains("Psychology", ignoreCase = true) -> "BSPSYCH"
            p.contains("Master of Arts in Education", ignoreCase = true) || p.contains("MAED", ignoreCase = true) -> "MAED"
            p.contains("Hospitality", ignoreCase = true) -> if (p.contains("Certificate", ignoreCase = true)) "CERT-HM" else "BSHM"
            p.contains("Tourism", ignoreCase = true) -> "BSTM"
            p.contains("Cookery", ignoreCase = true) -> "COOKERY"
            p.contains("Nursing", ignoreCase = true) -> "BSN"
            p.contains("Forensic", ignoreCase = true) -> "BSFS"
            p.contains("Criminology", ignoreCase = true) -> "BSCRIM"
            else -> p.replace("[^A-Za-z0-9]".toRegex(), "").take(6).uppercase().ifEmpty { "GEN" }
        }
    }

    private fun getYearDigit(year: String): String {
        return when {
            year.startsWith("1") -> "1"
            year.startsWith("2") -> "2"
            year.startsWith("3") -> "3"
            year.startsWith("4") -> "4"
            else -> "1"
        }
    }

    private fun getSectionLetter(section: String): String {
        val clean = section.trim().uppercase()
        val stripped = clean.replace("SECTION", "").replace("SEC", "").trim()
        val letterMatch = Regex("[A-J]").find(stripped)?.value
        if (letterMatch != null) return letterMatch
        val digitMatch = Regex("[1-9]").find(stripped)?.value
        if (digitMatch != null) return digitMatch
        return "A"
    }

    suspend fun getQuest(questCode: String): Quest? {
        val trimmedCode = questCode.trim().uppercase()
        val normalizedCode = when (trimmedCode) {
            "QQ-SIDE-GATE", "SIDE-GATE", "SIDEGATE" -> "QQ-SIDEGATE"
            else -> trimmedCode
        }
        try {
            val db = firestore
            if (db != null) {
                val doc = db.collection("quests").document(normalizedCode).get().await()
                if (doc.exists()) {
                    val q = doc.toObject(Quest::class.java)
                    if (q != null && q.active) return q
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        val seed = SeedQuests.QUEST_MAP[normalizedCode]
        if (seed != null && seed.active) return seed

        return null
    }

    fun listenUser(userId: String): Flow<User?> = callbackFlow {
        val db = firestore
        if (db != null && userId.isNotEmpty()) {
            val listener = db.collection("users").document(userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(_localUsers.value[userId])
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        val u = parseUserFromSnapshot(snapshot, userId)
                        val localUser = _localUsers.value[userId]
                        // Ensure total points never regress if locally approved points are higher
                        val resolvedPoints = maxOf(u.totalPoints, localUser?.totalPoints ?: 0)
                        val resolvedApprovedCount = maxOf(u.approvedCount, localUser?.approvedCount ?: 0)
                        val resolvedBadges = (u.badges + (localUser?.badges ?: emptyList())).distinct()
                        val mergedUser = u.copy(
                            totalPoints = resolvedPoints,
                            approvedCount = resolvedApprovedCount,
                            badges = resolvedBadges
                        )
                        val usersMap = _localUsers.value.toMutableListMap()
                        usersMap[userId] = mergedUser
                        _localUsers.value = usersMap
                        if (_currentUserId.value == userId) {
                            _currentUser.value = mergedUser
                        }
                        trySend(mergedUser)

                        // If academic info was manually edited in console, persist updated computed classId back to user document
                        val storedClassId = snapshot.getString("classId") ?: ""
                        if (storedClassId.isBlank() || normalizeClassId(storedClassId) != normalizeClassId(u.classId)) {
                            db.collection("users").document(userId).set(mapOf("classId" to u.classId), SetOptions.merge())
                        }

                        // Trigger class reconciliation to automatically reflect any manual database edits to academic profile
                        launch {
                            try { reconcileClassAggregates() } catch (_: Exception) {}
                        }
                    } else {
                        trySend(_localUsers.value[userId])
                    }
                }
            awaitClose { listener.remove() }
        } else {
            val user = _localUsers.value[userId] ?: User(uid = userId, name = "Student $userId")
            trySend(user)
            awaitClose {}
        }
    }

    private var lastReconcileTime = 0L

    fun listenClasses(): Flow<List<ClassModel>> = callbackFlow {
        val db = firestore
        if (db != null) {
            val now = System.currentTimeMillis()
            if (now - lastReconcileTime > 30_000L) {
                lastReconcileTime = now
                launch {
                    try {
                        reconcileClassAggregates()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val listener = db.collection("classes")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(_localClasses.value.sortedByDescending { it.totalPoints })
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                val model = doc.toObject(ClassModel::class.java) ?: return@mapNotNull null
                                val effId = model.classId.ifEmpty { doc.id }
                                model.copy(classId = effId)
                            } catch (e: Exception) { null }
                        }.sortedByDescending { it.totalPoints }
                        trySend(list)
                    } else {
                        trySend(_localClasses.value.sortedByDescending { it.totalPoints })
                    }
                }
            awaitClose { listener.remove() }
        } else {
            trySend(_localClasses.value.sortedByDescending { it.totalPoints })
            awaitClose {}
        }
    }

    fun parseSubmissionFromSnapshot(doc: DocumentSnapshot): Submission {
        return try {
            val data = doc.data ?: emptyMap<String, Any>()
            val submissionId = doc.id.ifEmpty { data["submissionId"]?.toString() ?: UUID.randomUUID().toString() }
            val userId = data["userId"]?.toString() ?: ""
            val studentName = data["studentName"]?.toString() ?: ""
            val classId = data["classId"]?.toString() ?: ""
            val questCode = data["questCode"]?.toString() ?: ""
            val questTitle = data["questTitle"]?.toString() ?: ""
            val videoPath = data["videoPath"]?.toString() ?: ""
            val pointValue = (data["pointValue"] as? Number)?.toInt() ?: 0
            val status = data["status"]?.toString() ?: "pending"

            val rawSubmitted = data["submittedAt"]
            val submittedAtStr = when (rawSubmitted) {
                is Number -> DateTimeUtils.formatPhilippineTimestamp(rawSubmitted.toLong())
                is String -> rawSubmitted.ifBlank { DateTimeUtils.getPhilippineCurrentTimestamp() }
                else -> DateTimeUtils.getPhilippineCurrentTimestamp()
            }

            val rawReviewed = data["reviewedAt"]
            val reviewedAtStr = when (rawReviewed) {
                is Number -> DateTimeUtils.formatPhilippineTimestamp(rawReviewed.toLong())
                is String -> rawReviewed.ifBlank { null }
                else -> null
            }

            val reviewerNote = data["reviewerNote"]?.toString()
            val submissionType = data["submissionType"]?.toString() ?: "video"
            val imagePath = data["imagePath"]?.toString() ?: ""
            val activityType = data["activityType"]?.toString() ?: ""
            val activityMetricValue = data["activityMetricValue"]?.toString() ?: ""
            val studentNotes = data["studentNotes"]?.toString() ?: ""
            val awardedPoints = (data["awardedPoints"] as? Number)?.toInt()
            val videoProvider = data["videoProvider"]?.toString() ?: "local"
            val driveFileId = data["driveFileId"]?.toString() ?: ""
            val driveUrl = data["driveUrl"]?.toString() ?: ""

            val sub = Submission(
                submissionId = submissionId,
                userId = userId,
                studentName = studentName,
                classId = classId,
                questCode = questCode,
                questTitle = questTitle,
                videoPath = videoPath,
                pointValue = pointValue,
                status = status,
                submittedAt = submittedAtStr,
                reviewedAt = reviewedAtStr,
                reviewerNote = reviewerNote,
                submissionType = submissionType,
                imagePath = imagePath,
                activityType = activityType,
                activityMetricValue = activityMetricValue,
                studentNotes = studentNotes,
                awardedPoints = awardedPoints,
                videoProvider = videoProvider,
                driveFileId = driveFileId,
                driveUrl = driveUrl
            )

            sub.copy(
                activityType = sub.getEffectiveActivityType(),
                activityMetricValue = sub.getEffectiveMetricValue()
            )
        } catch (e: Exception) {
            Submission(submissionId = doc.id)
        }
    }

    private fun mergeRemoteSubmissions(remoteList: List<Submission>) {
        if (remoteList.isEmpty()) return
        val current = _localSubmissions.value.toMutableList()
        val hasActualRemote = remoteList.any { !it.submissionId.startsWith("sub_demo_") }
        
        // When real submissions exist, purge the demo placeholder submissions to eliminate flickering
        val baseList = if (hasActualRemote) {
            current.filter { !it.submissionId.startsWith("sub_demo_") }.toMutableList()
        } else {
            current
        }

        for (remote in remoteList) {
            if (hasActualRemote && remote.submissionId.startsWith("sub_demo_")) continue

            val idx = baseList.indexOfFirst { it.submissionId == remote.submissionId }
            if (idx >= 0) {
                val local = baseList[idx]
                val isLocalFinal = local.status.equals("approved", ignoreCase = true) || local.status.equals("rejected", ignoreCase = true)
                val isRemotePending = remote.status.equals("pending", ignoreCase = true) || remote.status.equals("in review", ignoreCase = true)
                // Retain optimistic approval/rejection so it never flickers back to "in review"
                if (isLocalFinal && isRemotePending) {
                    // Do not revert local approved status
                } else {
                    baseList[idx] = remote
                }
            } else {
                baseList.add(remote)
            }
        }
        _localSubmissions.value = baseList
    }

    fun listenPendingSubmissions(): Flow<List<Submission>> = callbackFlow {
        val db = firestore
        if (db != null) {
            val listener = db.collection("submissions")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        val localPending = _localSubmissions.value.filter {
                            it.status.equals("pending", ignoreCase = true) || it.status.equals("in review", ignoreCase = true)
                        }.sortedBy { it.submittedAt }
                        trySend(localPending)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val remoteList = snapshot.documents.mapNotNull { parseSubmissionFromSnapshot(it) }
                        mergeRemoteSubmissions(remoteList)
                    }
                    val pendingList = _localSubmissions.value.filter {
                        it.status.equals("pending", ignoreCase = true) || it.status.equals("in review", ignoreCase = true)
                    }.sortedBy { it.submittedAt }
                    trySend(pendingList)
                }
            awaitClose { listener.remove() }
        } else {
            val localPending = _localSubmissions.value.filter {
                it.status.equals("pending", ignoreCase = true) || it.status.equals("in review", ignoreCase = true)
            }.sortedBy { it.submittedAt }
            trySend(localPending)
            awaitClose {}
        }
    }

    fun listenUserSubmissions(userId: String): Flow<List<Submission>> = callbackFlow {
        val db = firestore
        if (db != null && userId.isNotEmpty()) {
            val listener = db.collection("submissions")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        val localSubs = _localSubmissions.value.filter { it.userId == userId }
                            .sortedByDescending { it.submittedAt }
                        trySend(localSubs)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val remoteList = snapshot.documents.mapNotNull { parseSubmissionFromSnapshot(it) }
                        mergeRemoteSubmissions(remoteList)
                    }
                    val userSubs = _localSubmissions.value.filter { it.userId == userId }
                        .sortedByDescending { it.submittedAt }
                    trySend(userSubs)
                }
            awaitClose { listener.remove() }
        } else {
            val userSubs = _localSubmissions.value.filter { it.userId == userId }
                .sortedByDescending { it.submittedAt }
            trySend(userSubs)
            awaitClose {}
        }
    }

    suspend fun submitQuestVideo(
        userId: String,
        studentName: String,
        questCode: String,
        questTitle: String,
        pointValue: Int,
        videoUri: Uri?,
        context: android.content.Context? = null,
        classId: String = ""
    ): Submission {
        val targetContext = context ?: appContext
        val timestamp = DateTimeUtils.getPhilippineCurrentTimestamp()
        val submissionId = UUID.randomUUID().toString()
        var videoStorageUrl = videoUri?.toString() ?: VideoStorageManager.DEFAULT_FALLBACK_VIDEO

        if (videoUri != null && targetContext != null) {
            try {
                videoStorageUrl = VideoStorageManager.uploadVideoProof(
                    context = targetContext,
                    videoUri = videoUri,
                    userId = userId,
                    questCode = questCode,
                    submissionId = submissionId
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val resolvedClassId = if (classId.isNotBlank()) {
            normalizeClassId(classId)
        } else {
            normalizeClassId(
                _localUsers.value[userId]?.classId
                    ?: _currentUser.value?.takeIf { it.uid == userId }?.classId
                    ?: ""
            )
        }

        val submission = Submission(
            submissionId = submissionId,
            userId = userId,
            studentName = studentName,
            classId = resolvedClassId,
            questCode = questCode,
            questTitle = questTitle,
            videoPath = videoStorageUrl,
            pointValue = pointValue,
            status = "pending",
            submittedAt = timestamp,
            submissionType = "video",
            activityType = "Campus Video Quest",
            activityMetricValue = "QR Video Proof ($pointValue pts)",
            videoProvider = "local",
            driveFileId = "",
            driveUrl = ""
        )

        try {
            firestore?.collection("submissions")?.document(submission.submissionId)?.set(submission)?.await()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val current = _localSubmissions.value.filter { !it.submissionId.startsWith("sub_demo_") }.toMutableList()
        current.add(0, submission)
        _localSubmissions.value = current

        return submission
    }

    suspend fun submitQuestDriveVideo(
        userId: String,
        studentName: String,
        questCode: String,
        questTitle: String,
        pointValue: Int,
        driveUrl: String,
        context: android.content.Context? = null,
        classId: String = ""
    ): Submission {
        val canonicalUrl = GoogleDriveUtils.buildCanonicalDriveUrl(driveUrl)
        val fileId = GoogleDriveUtils.extractDriveFileId(driveUrl) ?: ""
        val timestamp = DateTimeUtils.getPhilippineCurrentTimestamp()
        val submissionId = java.util.UUID.randomUUID().toString()

        val resolvedClassId = if (classId.isNotBlank()) {
            normalizeClassId(classId)
        } else {
            normalizeClassId(
                _localUsers.value[userId]?.classId
                    ?: _currentUser.value?.takeIf { it.uid == userId }?.classId
                    ?: ""
            )
        }

        val submission = Submission(
            submissionId = submissionId,
            userId = userId,
            studentName = studentName,
            classId = resolvedClassId,
            questCode = questCode,
            questTitle = questTitle,
            videoPath = canonicalUrl,
            pointValue = pointValue,
            status = "pending",
            submittedAt = timestamp,
            submissionType = "video",
            activityType = "Campus Video Quest",
            activityMetricValue = "QR Video Proof ($pointValue pts)",
            videoProvider = "google_drive",
            driveFileId = fileId,
            driveUrl = canonicalUrl
        )

        try {
            val db = firestore
            if (db != null) {
                db.collection("submissions").document(submission.submissionId).set(submission).await()
                android.util.Log.d("FirebaseManager", "Successfully saved Drive submission to Firestore: $submissionId")
            } else {
                android.util.Log.w("FirebaseManager", "Firestore not connected, saved Drive submission to local session.")
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseManager", "Firestore error saving Drive submission: ${e.message}", e)
        }

        val current = _localSubmissions.value.filter { !it.submissionId.startsWith("sub_demo_") }.toMutableList()
        current.add(0, submission)
        _localSubmissions.value = current

        return submission
    }

    suspend fun submitScreenshotProof(
        userId: String,
        studentName: String,
        activityType: String,
        metricValue: String,
        requestedPoints: Int,
        studentNotes: String,
        imageUri: Uri? = null,
        context: android.content.Context? = null,
        driveUrl: String = "",
        driveFileId: String = "",
        classId: String = ""
    ): Submission {
        val targetContext = context ?: appContext
        val timestamp = DateTimeUtils.getPhilippineCurrentTimestamp()
        val submissionId = UUID.randomUUID().toString()
        val hasDriveLink = driveUrl.isNotBlank()
        val resolvedDriveFileId = if (driveFileId.isNotBlank()) driveFileId else (GoogleDriveUtils.extractDriveFileId(driveUrl) ?: "")
        var imageStorageUrl = if (hasDriveLink && imageUri == null) driveUrl.trim() else (imageUri?.toString() ?: "sample_screenshot_steps")

        if (imageUri != null && targetContext != null) {
            try {
                imageStorageUrl = VideoStorageManager.uploadScreenshotProof(
                    context = targetContext,
                    imageUri = imageUri,
                    userId = userId,
                    activityType = activityType,
                    submissionId = submissionId
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val resolvedClassId = if (classId.isNotBlank()) {
            normalizeClassId(classId)
        } else {
            normalizeClassId(
                _localUsers.value[userId]?.classId
                    ?: _currentUser.value?.takeIf { it.uid == userId }?.classId
                    ?: ""
            )
        }

        val submission = Submission(
            submissionId = submissionId,
            userId = userId,
            studentName = studentName,
            classId = resolvedClassId,
            questCode = "",
            questTitle = "$activityType: $metricValue",
            videoPath = if (hasDriveLink) driveUrl.trim() else "",
            imagePath = imageStorageUrl,
            pointValue = requestedPoints,
            status = "pending",
            submittedAt = timestamp,
            submissionType = "screenshot",
            activityType = activityType,
            activityMetricValue = metricValue,
            studentNotes = studentNotes,
            videoProvider = if (hasDriveLink) "google_drive" else "local",
            driveFileId = resolvedDriveFileId,
            driveUrl = if (hasDriveLink) driveUrl.trim() else ""
        )

        try {
            firestore?.collection("submissions")?.document(submission.submissionId)?.set(submission)?.await()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val current = _localSubmissions.value.filter { !it.submissionId.startsWith("sub_demo_") }.toMutableList()
        current.add(0, submission)
        _localSubmissions.value = current

        return submission
    }

    suspend fun approveSubmission(
        submission: Submission,
        awardedPoints: Int? = null,
        reviewerNote: String? = null
    ): Boolean {
        val db = firestore
        val submissionId = submission.submissionId
        val timestamp = DateTimeUtils.getPhilippineCurrentTimestamp()
        val finalPoints = awardedPoints ?: submission.pointValue

        // 1. Immediately update local memory state so UI responds instantly without flickering
        val updatedSubs = _localSubmissions.value.map {
            if (it.submissionId == submissionId) {
                it.copy(
                    status = "approved",
                    reviewedAt = timestamp,
                    awardedPoints = finalPoints,
                    reviewerNote = reviewerNote ?: it.reviewerNote
                )
            } else it
        }
        _localSubmissions.value = updatedSubs

        // 2. Fetch authoritative student profile directly from Firestore
        var studentDoc: User? = null
        if (db != null && submission.userId.isNotEmpty()) {
            try {
                val userSnap = db.collection("users").document(submission.userId).get().await()
                if (userSnap.exists()) {
                    studentDoc = parseUserFromSnapshot(userSnap, submission.userId)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val baseUser = studentDoc 
            ?: _localUsers.value[submission.userId]
            ?: _currentUser.value?.takeIf { it.uid == submission.userId }
            ?: User(
                uid = submission.userId, 
                name = submission.studentName,
                classId = normalizeClassId(submission.classId.ifEmpty { "CITE-BSCS-1A" })
            )

        // Ensure classId is computed directly from student's true academic fields
        val computedClassId = buildClassId(baseUser.college, baseUser.program, baseUser.yearLevel, baseUser.section)
        val studentClassId = normalizeClassId(
            if (computedClassId.isNotBlank()) computedClassId
            else if (baseUser.classId.isNotBlank()) baseUser.classId 
            else if (submission.classId.isNotBlank()) submission.classId 
            else "CITE-BSCS-1A"
        )

        val newApprovedCount = baseUser.approvedCount + 1
        val newTotalPoints = baseUser.totalPoints + finalPoints
        val newBadges = baseUser.badges.toMutableList()
        val newlyEarned = BadgeCatalog.checkNewBadges(newApprovedCount, baseUser.badges)
        newBadges.addAll(newlyEarned)

        val updatedUser = baseUser.copy(
            classId = studentClassId,
            totalPoints = newTotalPoints,
            approvedCount = newApprovedCount,
            badges = newBadges.distinct()
        )
        val currentUsers = _localUsers.value.toMutableListMap()
        currentUsers[submission.userId] = updatedUser
        _localUsers.value = currentUsers

        // Immediately update _currentUser if the affected student is currently logged in!
        if (_currentUserId.value == submission.userId || _currentUser.value?.uid == submission.userId) {
            _currentUser.value = updatedUser
        }

        // Update local class leaderboard state
        _localClasses.value = _localClasses.value.map {
            if (it.classId == studentClassId || normalizeClassId(it.classId) == studentClassId) {
                it.copy(totalPoints = it.totalPoints + finalPoints)
            } else it
        }

        // 3. Persist to Firestore with atomic operations
        if (db != null) {
            try {
                val subRef = db.collection("submissions").document(submissionId)
                val userRef = db.collection("users").document(submission.userId)

                val subUpdates = mutableMapOf<String, Any>(
                    "status" to "approved",
                    "reviewedAt" to timestamp,
                    "awardedPoints" to finalPoints,
                    "classId" to studentClassId
                )
                if (reviewerNote != null) {
                    subUpdates["reviewerNote"] = reviewerNote
                }
                subRef.set(subUpdates, SetOptions.merge()).await()

                val userUpdates = mapOf<String, Any>(
                    "totalPoints" to FieldValue.increment(finalPoints.toLong()),
                    "approvedCount" to FieldValue.increment(1L),
                    "badges" to updatedUser.badges,
                    "classId" to studentClassId
                )
                userRef.set(userUpdates, SetOptions.merge()).await()

                // Persist class total points to the student's true class
                if (studentClassId.isNotEmpty()) {
                    try {
                        val classRef = db.collection("classes").document(studentClassId)
                        val classSnap = classRef.get().await()
                        if (classSnap.exists()) {
                            classRef.update("totalPoints", FieldValue.increment(finalPoints.toLong())).await()
                        } else {
                            val parsed = parseClassMetadataFromId(studentClassId)
                            val newClass = parsed.copy(
                                totalPoints = finalPoints,
                                memberCount = 1
                            )
                            classRef.set(newClass, SetOptions.merge()).await()
                        }

                        // Also sync legacy document alias if studentClassId is CITE-BSARCH-1A or CITE-BS A-1A
                        if (studentClassId == "CITE-BSARCH-1A" || studentClassId == "CITE-BS A-1A") {
                            try {
                                val legRef = db.collection("classes").document("CITE-BS A-1A")
                                if (legRef.get().await().exists()) {
                                    legRef.update("totalPoints", FieldValue.increment(finalPoints.toLong())).await()
                                }
                            } catch (_: Exception) {}
                        }
                    } catch (ce: Exception) {
                        ce.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return true
    }

    suspend fun rejectSubmission(submissionId: String, reason: String): Boolean {
        val timestamp = DateTimeUtils.getPhilippineCurrentTimestamp()
        
        // 1. Immediately update local memory state
        val updatedSubs = _localSubmissions.value.map {
            if (it.submissionId == submissionId) {
                it.copy(status = "rejected", reviewerNote = reason, reviewedAt = timestamp)
            } else it
        }
        _localSubmissions.value = updatedSubs

        // 2. Persist to Firestore
        val db = firestore
        if (db != null) {
            try {
                db.collection("submissions").document(submissionId).set(
                    mapOf(
                        "status" to "rejected",
                        "reviewerNote" to reason,
                        "reviewedAt" to timestamp
                    ),
                    SetOptions.merge()
                ).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return true
    }

    suspend fun updateUserProfilePic(uid: String, uriString: String): Boolean {
        val user = _localUsers.value[uid] ?: _currentUser.value ?: return false
        val updated = user.copy(profilePicUri = uriString)
        
        val db = firestore
        if (db != null) {
            try {
                db.collection("users").document(uid).update("profilePicUri", uriString).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val currentUsers = _localUsers.value.toMutableListMap()
        currentUsers[uid] = updated
        _localUsers.value = currentUsers
        if (_currentUserId.value == uid) {
            _currentUser.value = updated
        }
        return true
    }

    suspend fun updateTargetPoints(uid: String, targetPoints: Int): Boolean {
        val user = _localUsers.value[uid] ?: _currentUser.value ?: return false
        val updated = user.copy(targetPoints = targetPoints.coerceAtLeast(50))

        val db = firestore
        if (db != null) {
            try {
                db.collection("users").document(uid).update("targetPoints", targetPoints).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val currentUsers = _localUsers.value.toMutableListMap()
        currentUsers[uid] = updated
        _localUsers.value = currentUsers
        if (_currentUserId.value == uid) {
            _currentUser.value = updated
        }
        return true
    }

    suspend fun updateUserProfile(
        uid: String,
        name: String,
        college: String,
        program: String,
        yearLevel: String,
        section: String,
        firstName: String = "",
        lastName: String = ""
    ): Boolean {
        val user = _localUsers.value[uid] ?: _currentUser.value ?: return false
        val classId = buildClassId(college, program, yearLevel, section)

        val resolvedFirstName = firstName.trim().ifEmpty { name.trim().split(" ").firstOrNull() ?: user.firstName }
        val resolvedLastName = lastName.trim().ifEmpty { name.trim().split(" ").drop(1).joinToString(" ") }.ifEmpty { user.lastName }
        val resolvedFullName = if (resolvedFirstName.isNotBlank() || resolvedLastName.isNotBlank()) {
            "$resolvedFirstName $resolvedLastName".trim()
        } else {
            name.trim().ifEmpty { user.name }
        }

        val updated = user.copy(
            name = resolvedFullName,
            firstName = resolvedFirstName,
            lastName = resolvedLastName,
            college = college,
            program = program,
            yearLevel = yearLevel,
            section = section,
            classId = classId
        )
        
        val db = firestore
        if (db != null) {
            try {
                db.collection("users").document(uid).set(updated).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        val currentUsers = _localUsers.value.toMutableListMap()
        currentUsers[uid] = updated
        _localUsers.value = currentUsers
        if (_currentUserId.value == uid) {
            _currentUser.value = updated
        }
        return true
    }

    fun listenAcademicRequests(): Flow<List<AcademicChangeRequest>> = callbackFlow {
        val db = firestore
        if (db != null) {
            val listener = db.collection("academic_requests")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(_localAcademicRequests.value)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val remoteList = snapshot.toObjects(AcademicChangeRequest::class.java)
                        val merged = _localAcademicRequests.value.toMutableList()
                        for (remote in remoteList) {
                            val idx = merged.indexOfFirst { it.requestId == remote.requestId }
                            if (idx >= 0) {
                                merged[idx] = remote
                            } else {
                                merged.add(remote)
                            }
                        }
                        _localAcademicRequests.value = merged
                    }
                    trySend(_localAcademicRequests.value)
                }
            awaitClose { listener.remove() }
        } else {
            trySend(_localAcademicRequests.value)
            awaitClose {}
        }
    }

    fun listenUserAcademicRequest(userId: String): Flow<AcademicChangeRequest?> = callbackFlow {
        val db = firestore
        if (db != null && userId.isNotEmpty()) {
            val listener = db.collection("academic_requests")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        val local = _localAcademicRequests.value.filter { it.userId == userId }.maxByOrNull { it.requestedAt }
                        trySend(local)
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val list = snapshot.toObjects(AcademicChangeRequest::class.java)
                        val latest = list.maxByOrNull { it.requestedAt } ?: _localAcademicRequests.value.filter { it.userId == userId }.maxByOrNull { it.requestedAt }
                        trySend(latest)
                    } else {
                        val local = _localAcademicRequests.value.filter { it.userId == userId }.maxByOrNull { it.requestedAt }
                        trySend(local)
                    }
                }
            awaitClose { listener.remove() }
        } else {
            val local = _localAcademicRequests.value.filter { it.userId == userId }.maxByOrNull { it.requestedAt }
            trySend(local)
            awaitClose {}
        }
    }

    suspend fun submitAcademicChangeRequest(request: AcademicChangeRequest): Boolean {
        val currentList = _localAcademicRequests.value.toMutableList()
        currentList.removeAll { it.requestId == request.requestId }
        currentList.add(0, request)
        _localAcademicRequests.value = currentList

        val user = _localUsers.value[request.userId] ?: _currentUser.value
        val detailsString = "${request.requestedCollege} • ${request.requestedProgram} (${request.requestedSection})"
        if (user != null) {
            val updatedUser = user.copy(
                pendingAcademicUpdate = true,
                pendingAcademicDetails = detailsString
            )
            val currentUsers = _localUsers.value.toMutableListMap()
            currentUsers[request.userId] = updatedUser
            _localUsers.value = currentUsers
            if (_currentUserId.value == request.userId) {
                _currentUser.value = updatedUser
            }
        }

        val db = firestore
        if (db != null) {
            try {
                db.collection("academic_requests").document(request.requestId)
                    .set(request, SetOptions.merge()).await()
                db.collection("users").document(request.userId).set(
                    mapOf(
                        "pendingAcademicUpdate" to true,
                        "pendingAcademicDetails" to detailsString
                    ),
                    SetOptions.merge()
                ).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return true
    }

    suspend fun approveAcademicChangeRequest(requestId: String, reviewerNote: String? = null): Boolean {
        val timestamp = System.currentTimeMillis()
        val request = _localAcademicRequests.value.find { it.requestId == requestId } ?: return false

        val updatedRequests = _localAcademicRequests.value.map {
            if (it.requestId == requestId) {
                it.copy(status = "approved", reviewedAt = timestamp, reviewerNote = reviewerNote)
            } else it
        }
        _localAcademicRequests.value = updatedRequests

        val user = _localUsers.value[request.userId] ?: _currentUser.value
        val newClassId = buildClassId(
            request.requestedCollege,
            request.requestedProgram,
            request.requestedYearLevel,
            request.requestedSection
        )
        val oldClassId = normalizeClassId(user?.classId ?: "${request.currentCollege}-${getProgramAbbreviation(request.currentProgram)}-${getYearDigit(request.currentYearLevel)}${getSectionLetter(request.currentSection)}")
        
        if (user != null) {
            val updatedUser = user.copy(
                college = request.requestedCollege,
                program = request.requestedProgram,
                yearLevel = request.requestedYearLevel,
                section = request.requestedSection,
                classId = newClassId,
                pendingAcademicUpdate = false,
                pendingAcademicDetails = ""
            )
            val currentUsers = _localUsers.value.toMutableMap()
            currentUsers[request.userId] = updatedUser
            _localUsers.value = currentUsers
            if (_currentUserId.value == request.userId) {
                _currentUser.value = updatedUser
            }
        }

        // Update local classes member counts
        if (oldClassId != newClassId) {
            _localClasses.value = _localClasses.value.map {
                when {
                    it.classId == oldClassId || normalizeClassId(it.classId) == oldClassId -> 
                        it.copy(memberCount = maxOf(0, it.memberCount - 1))
                    it.classId == newClassId || normalizeClassId(it.classId) == newClassId -> 
                        it.copy(memberCount = it.memberCount + 1)
                    else -> it
                }
            }
        }

        val db = firestore
        if (db != null) {
            try {
                val reqUpdates = mutableMapOf<String, Any>(
                    "status" to "approved",
                    "reviewedAt" to timestamp
                )
                if (reviewerNote != null) reqUpdates["reviewerNote"] = reviewerNote
                db.collection("academic_requests").document(requestId).set(reqUpdates, SetOptions.merge()).await()

                val userUpdates = mapOf<String, Any>(
                    "college" to request.requestedCollege,
                    "program" to request.requestedProgram,
                    "yearLevel" to request.requestedYearLevel,
                    "section" to request.requestedSection,
                    "classId" to newClassId,
                    "pendingAcademicUpdate" to false,
                    "pendingAcademicDetails" to ""
                )
                db.collection("users").document(request.userId).set(userUpdates, SetOptions.merge()).await()

                if (oldClassId != newClassId) {
                    try {
                        if (oldClassId.isNotBlank()) {
                            db.collection("classes").document(oldClassId).update("memberCount", FieldValue.increment(-1L))
                        }
                        val newClassRef = db.collection("classes").document(newClassId)
                        val newClassSnap = newClassRef.get().await()
                        if (newClassSnap.exists()) {
                            newClassRef.update("memberCount", FieldValue.increment(1L)).await()
                        } else {
                            val className = "${request.requestedCollege} - ${getProgramAbbreviation(request.requestedProgram)} ${getYearDigit(request.requestedYearLevel)}${getSectionLetter(request.requestedSection)}"
                            val newClass = ClassModel(
                                classId = newClassId,
                                name = className,
                                college = request.requestedCollege,
                                program = request.requestedProgram,
                                yearLevel = request.requestedYearLevel,
                                section = request.requestedSection,
                                totalPoints = 0,
                                memberCount = 1
                            )
                            newClassRef.set(newClass).await()
                        }
                    } catch (ce: Exception) {
                        ce.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return true
    }

    fun getProgramFullName(progCode: String): String {
        val code = progCode.trim().uppercase()
        return when {
            code.contains("BSCS") -> "BS Computer Science"
            code.contains("BSIT") -> "BS Information Technology"
            code.contains("BSARCH") || (code.contains("BSA") && code.contains("ARCH")) || code == "BS A" -> "BS Architecture"
            code.contains("BSEE") -> "BS Electrical Engineering"
            code.contains("BSA") -> "BS Accountancy"
            code.contains("ABCOMM") || code.contains("COMM") -> "AB Communication"
            code.contains("BMMA") || code.contains("BMA") -> "Bachelor of Multimedia Arts"
            code.contains("BSN") -> "BS Nursing"
            code.contains("BSHM") -> "BS Hospitality Management"
            code.contains("BSFS") -> "BS Forensic Science"
            code.contains("BSBIO") -> "BS Biology"
            code.contains("BSPSYCH") -> "BS Psychology"
            code.contains("BSCRIM") -> "BS Criminology"
            code.contains("BSTM") -> "BS Tourism Management"
            else -> "BS $code"
        }
    }

    fun parseClassMetadataFromId(rawClassId: String): ClassModel {
        val norm = normalizeClassId(rawClassId)
        val parts = norm.split("-")
        val college = parts.getOrNull(0)?.trim()?.ifEmpty { "CITE" } ?: "CITE"
        val progCode = parts.getOrNull(1)?.trim() ?: "BSCS"
        val ys = parts.getOrNull(2)?.trim() ?: "1A"

        val program = getProgramFullName(progCode)
        val yearDigit = ys.take(1)
        val yearLevel = when (yearDigit) {
            "1" -> "1st Year"
            "2" -> "2nd Year"
            "3" -> "3rd Year"
            "4" -> "4th Year"
            else -> "1st Year"
        }
        val secChar = ys.drop(1).take(1).ifEmpty { "A" }
        val section = if (secChar.matches("[A-Z]".toRegex())) "Section $secChar" else "Section A"
        val className = "$college - ${getProgramAbbreviation(program)} ${yearDigit}${secChar}"

        return ClassModel(
            classId = rawClassId,
            name = className,
            college = college,
            program = program,
            yearLevel = yearLevel,
            section = section,
            totalPoints = 0,
            memberCount = 0
        )
    }

    suspend fun reconcileClassAggregates() {
        val db = firestore ?: return
        try {
            // 1. Fetch all documents currently in 'classes' collection
            val existingClassDocs = try {
                val classesSnap = db.collection("classes").get().await()
                classesSnap.documents
            } catch (e: Exception) {
                emptyList()
            }

            // 2. Fetch all student profiles from Firestore
            val userList = try {
                val usersSnap = db.collection("users").get().await()
                usersSnap.documents.mapNotNull { doc ->
                    val role = doc.getString("role")
                    if (role == null || role.equals("student", ignoreCase = true)) {
                        parseUserFromSnapshot(doc, doc.id)
                    } else null
                }
            } catch (e: Exception) {
                emptyList()
            }

            // 3. Fetch all approved submissions
            val approvedSubs = try {
                val subsSnap = db.collection("submissions").whereEqualTo("status", "approved").get().await()
                subsSnap.documents.mapNotNull { doc ->
                    try {
                        parseSubmissionFromSnapshot(doc)
                    } catch (e: Exception) { null }
                }
            } catch (e: Exception) {
                emptyList()
            }

            // Map student approved points
            val pointsByStudent = mutableMapOf<String, Int>()
            for (sub in approvedSubs) {
                val pts = sub.awardedPoints ?: sub.pointValue
                pointsByStudent[sub.userId] = (pointsByStudent[sub.userId] ?: 0) + pts
            }

            // Group students by normalized class ID
            val studentsByClass = mutableMapOf<String, MutableList<User>>()
            for (user in userList) {
                val normClassId = normalizeClassId(user.classId)
                if (normClassId.isNotBlank() && normClassId != "ADMIN-STAFF") {
                    studentsByClass.getOrPut(normClassId) { mutableListOf() }.add(user)
                }
            }

            // Combine all class IDs (both existing class documents in Firestore and registered user class IDs)
            val allClassIds = mutableSetOf<String>()
            existingClassDocs.forEach { allClassIds.add(it.id) }
            studentsByClass.keys.forEach { allClassIds.add(it) }

            // Process every class document and fill all 8 fields to bridge all gaps
            for (docId in allClassIds) {
                val normClassId = normalizeClassId(docId)
                val members = studentsByClass[normClassId] ?: emptyList()
                val memberCount = members.size

                val existingDoc = existingClassDocs.find { it.id == docId }
                val existingPoints = existingDoc?.getLong("totalPoints")?.toInt() ?: 0

                val totalPoints = if (members.isNotEmpty()) {
                    members.sumOf { u ->
                        val fromSubs = pointsByStudent[u.uid] ?: 0
                        maxOf(u.totalPoints, fromSubs)
                    }
                } else {
                    existingPoints
                }

                val classDoc = if (members.isNotEmpty()) {
                    val firstMember = members.first()
                    val className = "${firstMember.college} - ${getProgramAbbreviation(firstMember.program)} ${getYearDigit(firstMember.yearLevel)}${getSectionLetter(firstMember.section)}"
                    ClassModel(
                        classId = docId,
                        name = className,
                        college = firstMember.college,
                        program = firstMember.program,
                        yearLevel = firstMember.yearLevel,
                        section = firstMember.section,
                        totalPoints = totalPoints,
                        memberCount = memberCount
                    )
                } else {
                    val parsed = parseClassMetadataFromId(docId)
                    parsed.copy(
                        classId = docId,
                        totalPoints = totalPoints,
                        memberCount = memberCount
                    )
                }

                // Upsert to Firestore document (Document ID == classId == docId)
                try {
                    db.collection("classes").document(docId).set(classDoc, SetOptions.merge()).await()
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            android.util.Log.w("FirebaseManager", "Reconciliation notice: ${e.message}")
        }
    }

    suspend fun rejectAcademicChangeRequest(requestId: String, reason: String): Boolean {
        val timestamp = System.currentTimeMillis()
        val request = _localAcademicRequests.value.find { it.requestId == requestId } ?: return false

        val updatedRequests = _localAcademicRequests.value.map {
            if (it.requestId == requestId) {
                it.copy(status = "rejected", reviewedAt = timestamp, reviewerNote = reason)
            } else it
        }
        _localAcademicRequests.value = updatedRequests

        val user = _localUsers.value[request.userId] ?: _currentUser.value
        if (user != null) {
            val updatedUser = user.copy(
                pendingAcademicUpdate = false,
                pendingAcademicDetails = ""
            )
            val currentUsers = _localUsers.value.toMutableListMap()
            currentUsers[request.userId] = updatedUser
            _localUsers.value = currentUsers
            if (_currentUserId.value == request.userId) {
                _currentUser.value = updatedUser
            }
        }

        val db = firestore
        if (db != null) {
            try {
                db.collection("academic_requests").document(requestId).set(
                    mapOf(
                        "status" to "rejected",
                        "reviewedAt" to timestamp,
                        "reviewerNote" to reason
                    ),
                    SetOptions.merge()
                ).await()
                db.collection("users").document(request.userId).set(
                    mapOf(
                        "pendingAcademicUpdate" to false,
                        "pendingAcademicDetails" to ""
                    ),
                    SetOptions.merge()
                ).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return true
    }

    fun listenNotifications(): Flow<List<AppNotification>> = callbackFlow {
        val db = firestore
        if (db != null) {
            val listener = db.collection("notifications")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snap, err ->
                    if (err != null || snap == null || snap.isEmpty) {
                        trySend(_localNotifications.value)
                        return@addSnapshotListener
                    }
                    val list = snap.toObjects(AppNotification::class.java)
                    trySend(list)
                }
            awaitClose { listener.remove() }
        } else {
            val job = launch {
                _localNotifications.collect {
                    trySend(it)
                }
            }
            trySend(_localNotifications.value)
            awaitClose { job.cancel() }
        }
    }

    fun markNotificationRead(id: String) {
        val current = _localNotifications.value.map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        _localNotifications.value = current
        firestore?.collection("notifications")?.document(id)?.update("isRead", true)
    }

    fun markAllNotificationsRead() {
        val current = _localNotifications.value.map { it.copy(isRead = true) }
        _localNotifications.value = current
    }

    fun clearNotifications() {
        _localNotifications.value = emptyList()
    }

    fun addNotification(title: String, message: String, category: String = "Quest") {
        val notif = AppNotification(
            id = "notif_" + UUID.randomUUID().toString().take(8),
            title = title,
            message = message,
            category = category,
            timestamp = System.currentTimeMillis(),
            isRead = false
        )
        val current = listOf(notif) + _localNotifications.value
        _localNotifications.value = current
        firestore?.collection("notifications")?.document(notif.id)?.set(notif)
    }

    private fun <K, V> Map<K, V>.toMutableListMap(): MutableMap<K, V> = this.toMutableMap()
    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
}


