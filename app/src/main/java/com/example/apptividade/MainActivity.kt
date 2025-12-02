package com.example.apptividade

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.apptividade.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ApptividadeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                    AppNavigation()
                }
            }
        }
    }
}

// --- NAVEGAÇÃO PRINCIPAL ---

@Composable
fun AppNavigation() {
    val auth = FirebaseAuth.getInstance()
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    // Permissão de Notificação (Android 13+)
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var currentScreen by remember { mutableStateOf("loading") }

    LaunchedEffect(currentUser) {
        if (currentUser == null) {
            currentScreen = "login"
        } else {
            db.collection("users").document(currentUser!!.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val ultimoCheckIn = document.getLong("ultimoCheckIn")
                        val trintaDias = 30L * 24 * 60 * 60 * 1000
                        val agora = System.currentTimeMillis()

                        if (ultimoCheckIn == null || (agora - ultimoCheckIn) > trintaDias) {
                            currentScreen = "checkin"
                        } else {
                            currentScreen = "home"
                        }
                    } else {
                        currentScreen = "checkin"
                    }
                }
                .addOnFailureListener { currentScreen = "home" }
        }
    }

    when (currentScreen) {
        "loading" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AppGreen) }
        "login" -> LoginScreen(
            onLoginSuccess = { currentUser = auth.currentUser; currentScreen = "loading" },
            onNavigateToSignUp = { currentScreen = "signup" },
            onNavigateToForgot = { currentScreen = "forgot_password" }
        )
        "signup" -> SignUpScreen(
            onSignUpSuccess = { currentUser = auth.currentUser; currentScreen = "loading" },
            onBack = { currentScreen = "login" }
        )
        "forgot_password" -> ForgotPasswordScreen(onBack = { currentScreen = "login" })
        "checkin" -> CheckInScreen(onCheckInComplete = { currentScreen = "home" })
        "home" -> HomeScreen(
            userName = currentUser?.email ?: "Atleta",
            onLogout = {
                NotificationScheduler.cancelAll(context) // Cancela alarmes ao sair
                auth.signOut()
                currentUser = null
                currentScreen = "login"
            }
        )
    }
}

// --- TELA LOGIN ---
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit, onNavigateToSignUp: () -> Unit, onNavigateToForgot: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(100.dp).clip(CircleShape).background(AppYellowGreen), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(60.dp), tint = AppGreen) }
        Spacer(Modifier.height(32.dp))
        Text("Bem-vindo de volta!", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppTextBlack)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Senha") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onNavigateToForgot) { Text("Esqueci minha senha", color = AppTextGray, fontSize = 12.sp) } }
        Spacer(Modifier.height(16.dp))

        if (isLoading) CircularProgressIndicator(color = AppGreen) else {
            Button(onClick = { if (email.isNotEmpty() && password.isNotEmpty()) { isLoading = true; auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task -> isLoading = false; if (task.isSuccessful) onLoginSuccess() else Toast.makeText(context, "Erro: ${task.exception?.message}", Toast.LENGTH_LONG).show() } } else Toast.makeText(context, "Preencha tudo", Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGreen), shape = RoundedCornerShape(12.dp)) { Text("ENTRAR", color = AppTextBlack, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Text("Não tem uma conta?", color = AppTextGray); TextButton(onClick = onNavigateToSignUp) { Text("Cadastre-se", color = AppGreen, fontWeight = FontWeight.Bold) } }
        }
    }
}

// --- TELA CADASTRO ---
@Composable
fun SignUpScreen(onSignUpSuccess: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("Criar Conta", fontSize = 24.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Senha") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation()); Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it }, label = { Text("Confirmar Senha") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation()); Spacer(Modifier.height(32.dp))

        if (isLoading) CircularProgressIndicator(color = AppGreen) else {
            Button(onClick = {
                if (email.isNotEmpty() && password == confirmPassword && password.length >= 6) {
                    isLoading = true
                    auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val initialData = hashMapOf("nivel" to 1, "xp" to 0, "streak" to 0, "ultimoTreinoData" to "", "metaFrequencia" to 3)
                            db.collection("users").document(task.result.user!!.uid).set(initialData).addOnSuccessListener { isLoading = false; onSignUpSuccess() }
                        } else { isLoading = false; Toast.makeText(context, "Erro: ${task.exception?.message}", Toast.LENGTH_LONG).show() }
                    }
                } else Toast.makeText(context, "Verifique os dados", Toast.LENGTH_SHORT).show()
            }, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGreen)) { Text("CADASTRAR", color = AppTextBlack, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp)); TextButton(onClick = onBack) { Text("Voltar", color = AppTextGray) }
        }
    }
}

// --- TELA RECUPERAR SENHA ---
@Composable
fun ForgotPasswordScreen(onBack: () -> Unit) {
    val context = LocalContext.current; val auth = FirebaseAuth.getInstance(); var email by remember { mutableStateOf("") }; var isLoading by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.LockReset, null, Modifier.size(60.dp), tint = AppGreen); Spacer(Modifier.height(16.dp)); Text("Recuperar Senha", fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail cadastrado") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(24.dp))
        if (isLoading) CircularProgressIndicator(color = AppGreen) else {
            Button(onClick = { if (email.isNotEmpty()) { isLoading = true; auth.sendPasswordResetEmail(email).addOnCompleteListener { isLoading = false; Toast.makeText(context, if (it.isSuccessful) "E-mail enviado!" else "Erro", Toast.LENGTH_LONG).show(); if (it.isSuccessful) onBack() } } }, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGreen)) { Text("ENVIAR", color = AppTextBlack, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp)); TextButton(onClick = onBack) { Text("Voltar", color = AppTextGray) }
        }
    }
}

// --- CARD CHECK-IN ---
@Composable
fun DailyWorkoutCheckCard() {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current
    val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    var treinosNaSemana by remember { mutableIntStateOf(0) }
    var metaSemanal by remember { mutableIntStateOf(3) }
    var treinoHojeFeito by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                metaSemanal = doc.getLong("metaFrequencia")?.toInt() ?: 3
                val ultimoTreino = doc.getString("ultimoTreinoData")
                if (ultimoTreino == hoje) treinoHojeFeito = true
            }
            val calendar = Calendar.getInstance()
            calendar.firstDayOfWeek = Calendar.MONDAY
            calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            val inicioSemanaMs = calendar.timeInMillis
            db.collection("users").document(uid).collection("historico_treinos")
                .whereGreaterThanOrEqualTo("ts", inicioSemanaMs).get()
                .addOnSuccessListener { result -> treinosNaSemana = result.size(); isLoading = false }
        }
    }

    val metaAtingida = treinosNaSemana >= metaSemanal

    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = if (metaAtingida) AppOrange else if (treinoHojeFeito) AppGreen else Color.White), border = if (metaAtingida || treinoHojeFeito) null else BorderStroke(2.dp, AppGray), elevation = CardDefaults.cardElevation(4.dp)) {
        Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = when { metaAtingida -> "Meta Semanal Batida! 🏆"; treinoHojeFeito -> "Treino de Hoje Feito ✅"; else -> "Treino de Hoje" }, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (metaAtingida || treinoHojeFeito) Color.White else AppTextBlack)
                Text(text = when { metaAtingida -> "Você completou $treinosNaSemana/$metaSemanal treinos."; treinoHojeFeito -> "Faltam ${if (metaSemanal > treinosNaSemana) metaSemanal - treinosNaSemana else 0} para a meta."; else -> "Progresso: $treinosNaSemana/$metaSemanal" }, fontSize = 12.sp, color = if (metaAtingida || treinoHojeFeito) Color.White.copy(alpha = 0.9f) else AppTextGray)
            }
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AppTextGray)
            else {
                if (!metaAtingida || treinoHojeFeito) {
                    Checkbox(checked = treinoHojeFeito, onCheckedChange = { isChecked ->
                        if (isChecked && !treinoHojeFeito && !metaAtingida) {
                            val uid = auth.currentUser?.uid
                            if (uid != null) {
                                treinoHojeFeito = true; treinosNaSemana += 1
                                val updates = hashMapOf<String, Any>("ultimoTreinoData" to hoje, "xp" to FieldValue.increment(50), "streak" to FieldValue.increment(1))
                                db.collection("users").document(uid).update(updates)
                                val reg = hashMapOf("data" to hoje, "ts" to System.currentTimeMillis())
                                db.collection("users").document(uid).collection("historico_treinos").document(hoje).set(reg).addOnSuccessListener { Toast.makeText(context, "+50 XP!", Toast.LENGTH_SHORT).show() }
                            }
                        }
                    }, colors = CheckboxDefaults.colors(checkedColor = Color.White, checkmarkColor = if (metaAtingida) AppOrange else AppGreen, uncheckedColor = AppTextGray), enabled = !treinoHojeFeito)
                } else { Icon(Icons.Default.EmojiEvents, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
            }
        }
    }
}

// --- HOME SCREEN ---
@Composable
fun HomeScreen(userName: String, onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    Scaffold(bottomBar = { NavigationBar(containerColor = Color.White) { NavigationBarItem(icon = { Icon(Icons.Default.Home, "Home") }, label = { Text("Dashboard") }, selected = selectedTab == 0, onClick = { selectedTab = 0 }, colors = NavigationBarItemDefaults.colors(selectedIconColor = AppGreen, indicatorColor = AppYellowGreen)); NavigationBarItem(icon = { Icon(Icons.Default.Flag, "Objetivos") }, label = { Text("Objetivos") }, selected = selectedTab == 1, onClick = { selectedTab = 1 }, colors = NavigationBarItemDefaults.colors(selectedIconColor = AppGreen, indicatorColor = AppYellowGreen)) } }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) { if (selectedTab == 0) GymBuddyDashboard(userName, onLogout) else ObjectivesScreen() }
    }
}

// --- DASHBOARD ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymBuddyDashboard(userName: String, onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance(); val db = FirebaseFirestore.getInstance()
    var realStreak by remember { mutableIntStateOf(0) }; var realXp by remember { mutableIntStateOf(0) }; var realLevel by remember { mutableIntStateOf(1) }
    var imcInicial by remember { mutableStateOf(0f) }; var imcAtual by remember { mutableStateOf(0f) }; var pesoPerdido by remember { mutableStateOf(0f) }; var carregouEvolucao by remember { mutableStateOf(false) }; var showImcTable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).addSnapshotListener { s, _ -> if (s != null && s.exists()) { realStreak = s.getLong("streak")?.toInt() ?: 0; realXp = s.getLong("xp")?.toInt() ?: 0; realLevel = s.getLong("nivel")?.toInt() ?: 1 } }
            db.collection("users").document(uid).collection("historico_imc").orderBy("data", Query.Direction.ASCENDING).get().addOnSuccessListener { r -> if (!r.isEmpty) { val p = r.documents.first(); val u = r.documents.last(); val p1 = p.getDouble("peso")?.toFloat() ?: 0f; val p2 = u.getDouble("peso")?.toFloat() ?: 0f; imcInicial = p.getDouble("imc")?.toFloat() ?: 0f; imcAtual = u.getDouble("imc")?.toFloat() ?: 0f; pesoPerdido = p1 - p2; carregouEvolucao = true } }
        }
    }
    if (showImcTable) IMCTableDialog { showImcTable = false }

    Scaffold(topBar = { TopAppBar(title = { Text("GymBuddy", fontWeight = FontWeight.Bold) }, actions = { IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, "Sair", tint = Color.White) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = AppGreen, titleContentColor = AppTextBlack)) }) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState())) {
            DailyWorkoutCheckCard(); Spacer(Modifier.height(16.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppYellowGreen)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(40.dp), tint = AppGreen); Spacer(Modifier.width(16.dp)); Column { Text("Olá, $userName!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppTextBlack); Text("Mantenha o foco!", fontSize = 14.sp, color = AppTextGray) } } }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) { StreakCard(Modifier.weight(1f), realStreak); LevelCard(Modifier.weight(1f), realLevel, realXp) }
            Spacer(Modifier.height(16.dp))
            if (carregouEvolucao) { Card(Modifier.fillMaxWidth().clickable { showImcTable = true }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, AppGreen)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, null, Modifier.size(16.dp), tint = AppGreen); Spacer(Modifier.width(4.dp)); Text("📊 Sua Evolução (Ver tabela)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppTextBlack) }; Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("Início", fontSize = 12.sp, color = AppTextGray); Text("IMC: ${String.format("%.1f", imcInicial)}", fontWeight = FontWeight.Bold) }; Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = AppGreen); Column(horizontalAlignment = Alignment.End) { Text("Atual", fontSize = 12.sp, color = AppTextGray); Text("IMC: ${String.format("%.1f", imcAtual)}", fontWeight = FontWeight.Bold) } } } }; Spacer(Modifier.height(16.dp)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) { StatCard("Semana", "4", "treinos", Icons.Default.CalendarToday, Modifier.weight(1f)); StatCard("Meta", "100%", "completo", Icons.Default.Flag, Modifier.weight(1f)) }
        }
    }
}

// --- TELA OBJETIVOS (Com BOTÃO DE TESTE) ---
@Composable
fun ObjectivesScreen() {
    val auth = FirebaseAuth.getInstance(); val db = FirebaseFirestore.getInstance(); val context = LocalContext.current
    var frequenciaSemanal by remember { mutableIntStateOf(3) }; var tipoObjetivo by remember { mutableStateOf("perda_peso") }; var pesoAtual by remember { mutableFloatStateOf(0f) }; var periodoAtivoInicio by remember { mutableStateOf("") }; var periodoAtivoFim by remember { mutableStateOf("") }; var isLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { val uid = auth.currentUser?.uid; if (uid != null) db.collection("users").document(uid).get().addOnSuccessListener { d -> if (d.exists()) { frequenciaSemanal = d.getLong("metaFrequencia")?.toInt() ?: 3; tipoObjetivo = d.getString("metaTipo") ?: "perda_peso"; pesoAtual = d.getDouble("pesoAtual")?.toFloat() ?: 0f; periodoAtivoInicio = d.getString("periodoAtivoInicio") ?: "08:00"; periodoAtivoFim = d.getString("periodoAtivoFim") ?: "22:00" }; isLoading = false } }
    val metaAguaMl = (pesoAtual * 35).toInt()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Meus Objetivos 🎯", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppTextBlack); Spacer(Modifier.height(24.dp))
        if (isLoading) CircularProgressIndicator(color = AppGreen) else {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, AppGray)) { Column(Modifier.padding(16.dp)) { Text("Frequência de Treino", fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.height(16.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { for (i in 1..7) { Box(Modifier.size(35.dp).clip(CircleShape).background(if (frequenciaSemanal == i) AppGreen else AppGray).clickable { frequenciaSemanal = i }, contentAlignment = Alignment.Center) { Text(text = i.toString(), color = if (frequenciaSemanal == i) Color.White else AppTextBlack, fontWeight = FontWeight.Bold) } } }; Spacer(Modifier.height(8.dp)); Text("Meta: $frequenciaSemanal x na semana", color = AppGreen, fontWeight = FontWeight.Bold) } }
            Spacer(Modifier.height(16.dp)); Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, AppGray)) { Column(Modifier.padding(16.dp)) { Text("Foco Principal", fontWeight = FontWeight.Bold); Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = tipoObjetivo == "perda_peso", onClick = { tipoObjetivo = "perda_peso" }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen)); Text("Perda de Peso") }; Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = tipoObjetivo == "ganho_peso", onClick = { tipoObjetivo = "ganho_peso" }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen)); Text("Ganho de Peso") } } }
            Spacer(Modifier.height(16.dp)); Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { Text("Hidratação Inteligente", fontWeight = FontWeight.Bold, color = Color(0xFF1565C0)); Text("$metaAguaMl ml / dia", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1)); Text("Lembretes entre $periodoAtivoInicio e $periodoAtivoFim.", fontSize = 12.sp, color = Color(0xFF1976D2)) } }
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                val uid = auth.currentUser?.uid; if (uid != null) { val up = mapOf("metaFrequencia" to frequenciaSemanal, "metaTipo" to tipoObjetivo); db.collection("users").document(uid).update(up).addOnSuccessListener { NotificationScheduler.scheduleWaterReminders(context, pesoAtual, periodoAtivoInicio, periodoAtivoFim); Toast.makeText(context, "Salvo!", Toast.LENGTH_SHORT).show() } }
            }, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGreen), shape = RoundedCornerShape(12.dp)) { Text("SALVAR OBJETIVOS", color = AppTextBlack, fontWeight = FontWeight.Bold) }

            // --- BOTÃO DE TESTE (Mantido a pedido) ---
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, NotificationReceiver::class.java)
                val pi = PendingIntent.getBroadcast(context, 999, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                try { am.setExact(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 5000, pi); Toast.makeText(context, "Teste em 5s!", Toast.LENGTH_LONG).show() } catch (e: Exception) { Toast.makeText(context, "Erro", Toast.LENGTH_SHORT).show() }
            }, Modifier.fillMaxWidth().height(50.dp), border = BorderStroke(1.dp, Color.Red), shape = RoundedCornerShape(12.dp)) { Text("TESTAR NOTIFICAÇÃO (5s)", color = Color.Red, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// --- CHECK-IN (Mantido) ---
@Composable
fun CheckInScreen(onCheckInComplete: () -> Unit) {
    val context = LocalContext.current; val auth = FirebaseAuth.getInstance(); val db = FirebaseFirestore.getInstance()
    var peso by remember { mutableStateOf("") }; var altura by remember { mutableStateOf("") }; var sexo by remember { mutableStateOf("Masculino") }; var horarioInicio by remember { mutableStateOf("") }; var horarioFim by remember { mutableStateOf("") }; var isLoading by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
        Spacer(Modifier.height(24.dp)); Icon(Icons.Default.ManageAccounts, "Dados", Modifier.size(60.dp), tint = AppGreen); Text("Configuração de Perfil", fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppGray.copy(alpha = 0.5f))) { Column(Modifier.padding(16.dp)) { Text("Perfil Biológico", fontWeight = FontWeight.Bold); Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = sexo == "Masculino", onClick = { sexo = "Masculino" }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen)); Text("Homem", Modifier.padding(end=16.dp)); RadioButton(selected = sexo == "Feminino", onClick = { sexo = "Feminino" }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen)); Text("Mulher") } } }
        Spacer(Modifier.height(16.dp)); OutlinedTextField(value = peso, onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) peso = it }, label = { Text("Peso (kg)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, focusedLabelColor = AppGreen)); Spacer(Modifier.height(8.dp)); OutlinedTextField(value = altura, onValueChange = { if (it.all { c -> c.isDigit() }) altura = it }, label = { Text("Altura (cm)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, focusedLabelColor = AppGreen)); Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppYellowGreen.copy(alpha = 0.5f))) { Column(Modifier.padding(16.dp)) { Text("Período Ativo", fontWeight = FontWeight.Bold); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(value = horarioInicio, onValueChange = { if (it.length <= 5) horarioInicio = it }, label = { Text("Início (07:00)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, focusedLabelColor = AppGreen)); OutlinedTextField(value = horarioFim, onValueChange = { if (it.length <= 5) horarioFim = it }, label = { Text("Fim (22:00)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, focusedLabelColor = AppGreen)) } } }
        Spacer(Modifier.height(32.dp))
        if (isLoading) CircularProgressIndicator(color = AppGreen) else { Button(onClick = { if (peso.isNotEmpty() && altura.isNotEmpty() && horarioInicio.isNotEmpty() && horarioFim.isNotEmpty()) { isLoading = true; val pesoFloat = peso.toFloatOrNull(); val alturaInt = altura.toIntOrNull(); if (pesoFloat != null && alturaInt != null) { val imc = pesoFloat / ((alturaInt/100f) * (alturaInt/100f)); val uid = auth.currentUser!!.uid; val ts = System.currentTimeMillis(); val histData = hashMapOf("data" to ts, "peso" to pesoFloat, "altura" to alturaInt, "imc" to imc); val userData = mapOf("ultimoCheckIn" to ts, "pesoAtual" to pesoFloat, "alturaAtual" to alturaInt, "imcAtual" to imc, "sexo" to sexo, "periodoAtivoInicio" to horarioInicio, "periodoAtivoFim" to horarioFim); db.collection("users").document(uid).update(userData).addOnSuccessListener { db.collection("users").document(uid).collection("historico_imc").add(histData).addOnSuccessListener { isLoading = false; onCheckInComplete() } }.addOnFailureListener { val novoMap = userData.toMutableMap(); novoMap["nivel"] = 1; novoMap["xp"] = 0; db.collection("users").document(uid).set(novoMap).addOnSuccessListener { db.collection("users").document(uid).collection("historico_imc").add(histData).addOnSuccessListener { isLoading = false; onCheckInComplete() } } } } else Toast.makeText(context, "Inválido", Toast.LENGTH_SHORT).show() } else Toast.makeText(context, "Preencha tudo", Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGreen), shape = RoundedCornerShape(12.dp)) { Text("SALVAR PERFIL", color = AppTextBlack, fontWeight = FontWeight.Bold) } }
    }
}

// --- VISUAIS ---
@Composable fun StreakCard(modifier: Modifier = Modifier, days: Int) { Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppGray)) { Column(Modifier.padding(16.dp)) { Icon(Icons.Default.LocalFireDepartment, null, tint = AppStreakOrange); Text("$days", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = AppStreakOrange); Text("dias seguidos", fontSize = 14.sp, color = AppTextGray) } } }
@Composable fun LevelCard(modifier: Modifier = Modifier, level: Int, xp: Int) { val xpParaProximo = level * 100; val progresso = if (xpParaProximo > 0) xp.toFloat() / xpParaProximo else 0f; Card(modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppGray)) { Column(Modifier.padding(16.dp)) { Icon(Icons.Default.Star, null, tint = AppLevelGreen); Text("Nível $level", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppTextBlack); Spacer(Modifier.height(4.dp)); Text("$xp / $xpParaProximo XP", fontSize = 10.sp, color = AppTextGray); Spacer(Modifier.height(4.dp)); LinearProgressIndicator({ progresso.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(8.dp), color = AppLevelGreen, trackColor = Color.LightGray) } } }
@Composable fun StatCard(title: String, value: String, unit: String, icon: ImageVector, modifier: Modifier = Modifier) { Card(modifier.height(130.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppGray)) { Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(icon, null, tint = AppTextGray); Spacer(Modifier.height(8.dp)); Text(title, fontSize = 12.sp, color = AppTextGray); Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppTextBlack); Text(unit, fontSize = 12.sp, color = AppTextGray) } } }
@Composable fun IMCTableDialog(onDismiss: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, title = { Text("Referência de IMC", fontWeight = FontWeight.Bold, color = AppTextBlack) }, text = { Column { Text("Entenda seu resultado:", fontSize = 14.sp, color = AppTextGray); Spacer(modifier = Modifier.height(16.dp)); Row(modifier = Modifier.fillMaxWidth().background(AppYellowGreen, RoundedCornerShape(4.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text("IMC", fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("Classificação", fontWeight = FontWeight.Bold, fontSize = 14.sp) }; Spacer(modifier = Modifier.height(8.dp)); IMCTableRow("< 18.5", "Abaixo do peso", Color.Gray); IMCTableRow("18.5 - 24.9", "Peso normal", AppLevelGreen); IMCTableRow("25.0 - 29.9", "Sobrepeso", AppOrange); IMCTableRow("≥ 30.0", "Obesidade", Color(0xFFC62828)) } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Entendi", color = AppGreen, fontWeight = FontWeight.Bold) } }, containerColor = Color.White) }
@Composable fun IMCTableRow(r: String, c: String, color: Color) { Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(r, fontSize = 14.sp, color = AppTextBlack); Text(c, fontSize = 14.sp, color = color, fontWeight = FontWeight.Bold) }; HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray) }

@Preview(showBackground = true)
@Composable
fun GreetingPreview() { ApptividadeTheme { AppNavigation() } }