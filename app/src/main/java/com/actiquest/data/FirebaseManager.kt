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
                submittedAt = System.currentTimeMillis() - 1000 * 60 * 15,
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
                submittedAt = System.currentTimeMillis() - 1000 * 60 * 35,
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
                submittedAt = System.currentTimeMillis() - 1000 * 60 * 60,
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
                submittedAt = System.currentTimeMillis() - 1000 * 60 * 120,
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
                submittedAt = System.currentTimeMillis() - 1000 * 60 * 180,
                submissionType = "video"
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
        val classId = snap.getString("classId") ?: buildClassId(college, program, yearLevel, section)
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
            classId = classId,
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

                // 4. Upsert class in Firestore 'classes' collection
                try {
                    val classRef = firestore?.collection("classes")?.document(classId)
                    if (classRef != null) {
                        val classSnap = classRef.get().await()
                        if (classSnap.exists()) {
                            classRef.update("memberCount", FieldValue.increment(1)).await()
                        } else {
                            val className = "$college - ${getProgramAbbreviation(program)} ${getYearDigit(yearLevel)}${getSectionLetter(section)}"
                            val classDoc = ClassModel(
                                classId = classId,
                                name = className,
                                college = college,
                                program = program,
                                yearLevel = yearLevel,
                                section = section,
                                totalPoints = 0,
                                memberCount = 1
                            )
                            classRef.set(classDoc).await()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

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

    private fun buildClassId(college: String, program: String, year: String, sec: String): String {
        val progCode = getProgramAbbreviation(program)
        val yearNum = getYearDigit(year)
        val secCode = getSectionLetter(sec)
        return "$college-$progCode-$yearNum$secCode"
    }

    private fun getProgramAbbreviation(program: String): String {
        return when {
            program.contains("Information Technology") -> "BSIT"
            program.contains("Computer Science") -> "BSCS"
            program.contains("Entertainment and Multimedia", ignoreCase = true) || program.contains("BSEMC", ignoreCase = true) -> "BSEMC"
            program.contains("Electronics Engineering", ignoreCase = true) || program.contains("BSECE", ignoreCase = true) -> "BSECE"
            program.contains("Electrical Engineering") -> "BSEE"
            program.contains("Computer Engineering") -> "BSCPE"
            program.contains("Industrial Engineering") -> "BSIE"
            program.contains("Accountancy") -> "BSA"
            program.contains("Accounting Information", ignoreCase = true) || program.contains("BSAIS", ignoreCase = true) -> "BSAIS"
            program.contains("Business Administration") -> "BSBA"
            program.contains("Legal Management") -> "BSLM"
            program.contains("Entrepreneurship") -> "BSEntrep"
            program.contains("Multimedia Arts") -> "BMMA"
            program.contains("Communication") -> "ABComm"
            program.contains("Psychology") -> "BSPsych"
            program.contains("Secondary Education") -> "BSED"
            program.contains("Elementary Education") -> "BEED"
            program.contains("Nursing") -> "BSN"
            program.contains("Hospitality") -> "BSHM"
            program.contains("Tourism") -> "BSTM"
            program.contains("Forensic") || program.contains("Criminology") -> "BSFS"
            else -> program.take(4).uppercase()
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
        return when {
            section.contains("A", ignoreCase = true) -> "A"
            section.contains("B", ignoreCase = true) -> "B"
            section.contains("C", ignoreCase = true) -> "C"
            section.contains("1") -> "1"
            section.contains("2") -> "2"
            else -> "A"
        }
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

    fun listenClasses(): Flow<List<ClassModel>> = callbackFlow {
        val db = firestore
        if (db != null) {
            val listener = db.collection("classes")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.toObjects(ClassModel::class.java).sortedByDescending { it.totalPoints }
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
                        val remoteList = snapshot.toObjects(Submission::class.java)
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
                        val remoteList = snapshot.toObjects(Submission::class.java)
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
        videoUri: Uri?
    ): Submission {
        val timestamp = System.currentTimeMillis()
        var videoStorageUrl = videoUri?.toString() ?: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"

        try {
            val st = storage
            if (st != null && videoUri != null && !videoUri.toString().startsWith("http")) {
                val ref = st.reference.child("submissions/$userId/$questCode/${timestamp}.mp4")
                ref.putFile(videoUri).await()
                videoStorageUrl = ref.downloadUrl.await().toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val submission = Submission(
            submissionId = UUID.randomUUID().toString(),
            userId = userId,
            studentName = studentName,
            questCode = questCode,
            questTitle = questTitle,
            videoPath = videoStorageUrl,
            pointValue = pointValue,
            status = "pending",
            submittedAt = timestamp,
            submissionType = "video"
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

    suspend fun submitScreenshotProof(
        userId: String,
        studentName: String,
        activityType: String,
        metricValue: String,
        requestedPoints: Int,
        studentNotes: String,
        imageUri: Uri?
    ): Submission {
        val timestamp = System.currentTimeMillis()
        var imageStorageUrl = imageUri?.toString() ?: "sample_screenshot_steps"

        try {
            val st = storage
            if (st != null && imageUri != null && !imageUri.toString().startsWith("http")) {
                val ref = st.reference.child("submissions/$userId/screenshots/${timestamp}.jpg")
                ref.putFile(imageUri).await()
                imageStorageUrl = ref.downloadUrl.await().toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val submission = Submission(
            submissionId = UUID.randomUUID().toString(),
            userId = userId,
            studentName = studentName,
            questCode = "",
            questTitle = "$activityType: $metricValue",
            videoPath = "",
            imagePath = imageStorageUrl,
            pointValue = requestedPoints,
            status = "pending",
            submittedAt = timestamp,
            submissionType = "screenshot",
            activityType = activityType,
            activityMetricValue = metricValue,
            studentNotes = studentNotes
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
        val timestamp = System.currentTimeMillis()
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

        val currentUser = _localUsers.value[submission.userId] 
            ?: _currentUser.value?.takeIf { it.uid == submission.userId }
            ?: User(uid = submission.userId, name = submission.studentName)
        val newApprovedCount = currentUser.approvedCount + 1
        val newBadges = currentUser.badges.toMutableList()
        val newlyEarned = BadgeCatalog.checkNewBadges(newApprovedCount, currentUser.badges)
        newBadges.addAll(newlyEarned)

        val updatedUser = currentUser.copy(
            totalPoints = currentUser.totalPoints + finalPoints,
            approvedCount = newApprovedCount,
            badges = newBadges.distinct()
        )
        val currentUsers = _localUsers.value.toMutableListMap()
        currentUsers[submission.userId] = updatedUser
        _localUsers.value = currentUsers

        // CRITICAL: Immediately update _currentUser if the affected student is currently logged in!
        if (_currentUserId.value == submission.userId || _currentUser.value?.uid == submission.userId) {
            _currentUser.value = updatedUser
        }

        _localClasses.value = _localClasses.value.map {
            if (it.classId == updatedUser.classId) {
                it.copy(totalPoints = it.totalPoints + finalPoints)
            } else it
        }

        // 2. Persist to Firestore
        if (db != null) {
            try {
                val subRef = db.collection("submissions").document(submissionId)
                val userRef = db.collection("users").document(submission.userId)

                val subUpdates = mutableMapOf<String, Any>(
                    "status" to "approved",
                    "reviewedAt" to timestamp,
                    "awardedPoints" to finalPoints
                )
                if (reviewerNote != null) {
                    subUpdates["reviewerNote"] = reviewerNote
                }
                subRef.set(subUpdates, SetOptions.merge()).await()

                val userUpdates = mapOf<String, Any>(
                    "totalPoints" to updatedUser.totalPoints.toLong(),
                    "approvedCount" to updatedUser.approvedCount.toLong(),
                    "badges" to updatedUser.badges
                )
                userRef.set(userUpdates, SetOptions.merge()).await()

                if (updatedUser.classId.isNotEmpty()) {
                    try {
                        db.collection("classes").document(updatedUser.classId)
                            .set(mapOf("totalPoints" to FieldValue.increment(finalPoints.toLong())), SetOptions.merge()).await()
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
        val timestamp = System.currentTimeMillis()
        
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


