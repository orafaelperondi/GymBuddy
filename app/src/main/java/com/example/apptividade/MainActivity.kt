package com.example.apptividade

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apptividade.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

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
        "login" -> LoginScreen(onLoginSuccess = { currentUser = auth.currentUser; currentScreen = "loading" })
        "checkin" -> CheckInScreen(onCheckInComplete = { currentScreen = "home" })
        "home" -> HomeScreen(userName = currentUser?.email ?: "Atleta", onLogout = { auth.signOut(); currentUser = null; currentScreen = "login" })
    }
}

// --- TELA COM ABAS (HOME) ---
@Composable
fun HomeScreen(userName: String, onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Dashboard, 1 = Objetivos

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Dashboard") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = AppGreen, indicatorColor = AppYellowGreen)
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Flag, contentDescription = "Objetivos") },
                    label = { Text("Objetivos") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = AppGreen, indicatorColor = AppYellowGreen)
                )
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (selectedTab == 0) {
                GymBuddyDashboard(userName, onLogout)
            } else {
                ObjectivesScreen()
            }
        }
    }
}

// --- NOVA TELA: OBJETIVOS ---
@Composable
fun ObjectivesScreen() {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val context = LocalContext.current

    var frequenciaSemanal by remember { mutableStateOf(3) }
    var tipoObjetivo by remember { mutableStateOf("perda_peso") }
    var pesoAtual by remember { mutableStateOf(0f) }
    var periodoAtivoInicio by remember { mutableStateOf("") }
    var periodoAtivoFim by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    frequenciaSemanal = doc.getLong("metaFrequencia")?.toInt() ?: 3
                    tipoObjetivo = doc.getString("metaTipo") ?: "perda_peso"
                    pesoAtual = doc.getDouble("pesoAtual")?.toFloat() ?: 0f
                    periodoAtivoInicio = doc.getString("periodoAtivoInicio") ?: "08:00"
                    periodoAtivoFim = doc.getString("periodoAtivoFim") ?: "22:00"
                }
                isLoading = false
            }
        }
    }

    val metaAguaMl = (pesoAtual * 35).toInt()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text("Meus Objetivos 🎯", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AppTextBlack)
        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(color = AppGreen)
        } else {
            // 1. FREQUÊNCIA
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, AppGray)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Frequência de Treino", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Quantas vezes você quer treinar por semana?", fontSize = 12.sp, color = AppTextGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (i in 1..7) {
                            Box(
                                modifier = Modifier.size(35.dp).clip(CircleShape)
                                    .background(if (frequenciaSemanal == i) AppGreen else AppGray)
                                    .clickable { frequenciaSemanal = i },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = i.toString(), color = if (frequenciaSemanal == i) Color.White else AppTextBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Meta: ${frequenciaSemanal}x na semana", color = AppGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 2. OBJETIVO DE PESO
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, AppGray)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Foco Principal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Vamos monitorar sua evolução baseada nisso.", fontSize = 12.sp, color = AppTextGray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = tipoObjetivo == "perda_peso", onClick = { tipoObjetivo = "perda_peso" }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen))
                        Text("Perda de Peso")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = tipoObjetivo == "ganho_peso", onClick = { tipoObjetivo = "ganho_peso" }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen))
                        Text("Ganho de Peso")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 3. HIDRATAÇÃO
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_launcher_foreground), null, tint = Color(0xFF2196F3), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hidratação Inteligente", fontWeight = FontWeight.Bold, color = Color(0xFF1565C0))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Baseado no seu peso ($pesoAtual kg), você precisa de:", fontSize = 12.sp, color = Color(0xFF1976D2))
                    Text("$metaAguaMl ml / dia", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D47A1))
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = Color(0xFFBBDEFB))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Distribuiremos lembretes de 200ml entre $periodoAtivoInicio e $periodoAtivoFim.", fontSize = 12.sp, color = Color(0xFF1976D2), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            // BOTÃO SALVAR
            Button(
                onClick = {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        val updates = mapOf("metaFrequencia" to frequenciaSemanal, "metaTipo" to tipoObjetivo)
                        db.collection("users").document(uid).update(updates)
                            .addOnSuccessListener { Toast.makeText(context, "Objetivos salvos!", Toast.LENGTH_SHORT).show() }
                            .addOnFailureListener { Toast.makeText(context, "Erro ao salvar.", Toast.LENGTH_SHORT).show() }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SALVAR OBJETIVOS", color = AppTextBlack, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- DASHBOARD ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymBuddyDashboard(userName: String, onLogout: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    var imcInicial by remember { mutableStateOf(0f) }
    var imcAtual by remember { mutableStateOf(0f) }
    var pesoPerdido by remember { mutableStateOf(0f) }
    var carregouEvolucao by remember { mutableStateOf(false) }
    var showImcTable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).collection("historico_imc")
                .orderBy("data", Query.Direction.ASCENDING).get()
                .addOnSuccessListener { result ->
                    if (!result.isEmpty) {
                        val primeiro = result.documents.first()
                        val ultimo = result.documents.last()
                        val peso1 = primeiro.getDouble("peso")?.toFloat() ?: 0f
                        val peso2 = ultimo.getDouble("peso")?.toFloat() ?: 0f
                        imcInicial = primeiro.getDouble("imc")?.toFloat() ?: 0f
                        imcAtual = ultimo.getDouble("imc")?.toFloat() ?: 0f
                        pesoPerdido = peso1 - peso2
                        carregouEvolucao = true
                    }
                }
        }
    }

    if (showImcTable) { IMCTableDialog(onDismiss = { showImcTable = false }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GymBuddy", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, "Sair", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppGreen, titleContentColor = AppTextBlack)
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppYellowGreen)) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(id = R.drawable.ic_launcher_foreground), null, Modifier.size(40.dp), tint = AppGreen)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Olá, $userName!", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppTextBlack)
                        Text("Vamos treinar?", fontSize = 14.sp, color = AppTextGray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            if (carregouEvolucao) {
                Card(modifier = Modifier.fillMaxWidth().clickable { showImcTable = true }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, AppGreen)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, modifier = Modifier.size(16.dp), tint = AppGreen)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("📊 Sua Evolução (Toque para ver tabela)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppTextBlack)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column { Text("Início", fontSize = 12.sp, color = AppTextGray); Text("IMC: ${String.format("%.1f", imcInicial)}", fontWeight = FontWeight.Bold) }
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = AppGreen)
                            Column(horizontalAlignment = Alignment.End) { Text("Atual", fontSize = 12.sp, color = AppTextGray); Text("IMC: ${String.format("%.1f", imcAtual)}", fontWeight = FontWeight.Bold) }
                        }
                        if (pesoPerdido > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("🎉 Você já perdeu ${String.format("%.1f", pesoPerdido)} kg!", color = AppGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StreakCard(modifier = Modifier.weight(1f))
                LevelCard(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("Semana", "4", "treinos", Icons.Default.CalendarToday, Modifier.weight(1f))
                StatCard("Meta", "100%", "completo", Icons.Default.Flag, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("Total geral", "89", "treinos", Icons.AutoMirrored.Default.TrendingUp, Modifier.weight(1f))
                StatCard("Amigos ativos", "7", "hoje", Icons.Default.People, Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            DailyChallengeCard()
        }
    }
}

// --- POP-UP IMC ---
@Composable
fun IMCTableDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Referência de IMC", fontWeight = FontWeight.Bold, color = AppTextBlack) },
        text = {
            Column {
                Text("Entenda seu resultado:", fontSize = 14.sp, color = AppTextGray)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth().background(AppYellowGreen, RoundedCornerShape(4.dp)).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("IMC", fontWeight = FontWeight.Bold, fontSize = 14.sp); Text("Classificação", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                IMCTableRow("< 18.5", "Abaixo do peso", Color.Gray)
                IMCTableRow("18.5 - 24.9", "Peso normal", AppLevelGreen)
                IMCTableRow("25.0 - 29.9", "Sobrepeso", AppOrange)
                IMCTableRow("30.0 - 34.9", "Obesidade I", Color(0xFFEF5350))
                IMCTableRow("≥ 35.0", "Obesidade II/III", Color(0xFFC62828))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Entendi", color = AppGreen, fontWeight = FontWeight.Bold) } },
        containerColor = Color.White
    )
}
@Composable
fun IMCTableRow(range: String, category: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(range, fontSize = 14.sp, color = AppTextBlack); Text(category, fontSize = 14.sp, color = color, fontWeight = FontWeight.Bold)
    }
    HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
}

// --- TELA CHECK-IN (Mantida) ---
@Composable
fun CheckInScreen(onCheckInComplete: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    var peso by remember { mutableStateOf("") }
    var altura by remember { mutableStateOf("") }
    var sexo by remember { mutableStateOf("Masculino") }
    var horarioInicio by remember { mutableStateOf("") }
    var horarioFim by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Top) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Default.ManageAccounts, "Dados", Modifier.size(60.dp), tint = AppGreen)
        Text("Configuração de Perfil", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Precisamos de alguns dados para personalizar seu treino e hidratação.", fontSize = 14.sp, color = AppTextGray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        // Card Perfil Bio
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppGray.copy(alpha = 0.5f))) {
            Column(Modifier.padding(16.dp)) {
                Text("Perfil Biológico", fontWeight = FontWeight.Bold); Text("Importante para cálculos.", fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = sexo == "Masculino", onClick = { sexo = "Masculino" }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen)); Text("Homem", Modifier.padding(end=16.dp))
                    RadioButton(selected = sexo == "Feminino", onClick = { sexo = "Feminino" }, colors = RadioButtonDefaults.colors(selectedColor = AppGreen)); Text("Mulher")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = peso, onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) peso = it }, label = { Text("Peso (kg)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, focusedLabelColor = AppGreen))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = altura, onValueChange = { if (it.all { c -> c.isDigit() }) altura = it }, label = { Text("Altura (cm)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, focusedLabelColor = AppGreen))
        Spacer(Modifier.height(16.dp))

        // Card Periodo Ativo
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AppYellowGreen.copy(alpha = 0.5f))) {
            Column(Modifier.padding(16.dp)) {
                Text("Período Ativo", fontWeight = FontWeight.Bold)
                Text("Horário para lembretes de hidratação.", fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = horarioInicio, onValueChange = { if (it.length <= 5) horarioInicio = it }, label = { Text("Início (07:00)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, focusedLabelColor = AppGreen))
                    OutlinedTextField(value = horarioFim, onValueChange = { if (it.length <= 5) horarioFim = it }, label = { Text("Fim (22:00)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppGreen, focusedLabelColor = AppGreen))
                }
            }
        }
        Spacer(Modifier.height(32.dp))

        if (isLoading) CircularProgressIndicator(color = AppGreen) else {
            Button(onClick = {
                if (peso.isNotEmpty() && altura.isNotEmpty() && horarioInicio.isNotEmpty() && horarioFim.isNotEmpty()) {
                    isLoading = true
                    val pesoFloat = peso.toFloatOrNull(); val alturaInt = altura.toIntOrNull()
                    if (pesoFloat != null && alturaInt != null && alturaInt > 0) {
                        val imc = pesoFloat / ((alturaInt/100f) * (alturaInt/100f))
                        val uid = auth.currentUser!!.uid
                        val ts = System.currentTimeMillis()
                        val histData = hashMapOf("data" to ts, "peso" to pesoFloat, "altura" to alturaInt, "imc" to imc)
                        val userData = mapOf("ultimoCheckIn" to ts, "pesoAtual" to pesoFloat, "alturaAtual" to alturaInt, "imcAtual" to imc, "sexo" to sexo, "periodoAtivoInicio" to horarioInicio, "periodoAtivoFim" to horarioFim)
                        db.collection("users").document(uid).update(userData).addOnSuccessListener { db.collection("users").document(uid).collection("historico_imc").add(histData).addOnSuccessListener { isLoading = false; onCheckInComplete() } }
                            .addOnFailureListener {
                                val novoMap = userData.toMutableMap(); novoMap["nivel"] = 1; novoMap["xp"] = 0
                                db.collection("users").document(uid).set(novoMap).addOnSuccessListener { db.collection("users").document(uid).collection("historico_imc").add(histData).addOnSuccessListener { isLoading = false; onCheckInComplete() } }
                            }
                    } else Toast.makeText(context, "Números inválidos", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(context, "Preencha tudo", Toast.LENGTH_SHORT).show()
            }, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGreen), shape = RoundedCornerShape(12.dp)) { Text("SALVAR PERFIL", color = AppTextBlack, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

// --- TELA LOGIN (Modificada para mostrar erro completo) ---
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(100.dp).clip(CircleShape).background(AppYellowGreen), contentAlignment = Alignment.Center) { Icon(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(60.dp), tint = AppGreen) }
        Spacer(Modifier.height(32.dp)); Text("GymBuddy Access", fontSize = 22.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(32.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("E-mail") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Senha") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        Spacer(Modifier.height(24.dp))
        if (isLoading) CircularProgressIndicator(color = AppGreen) else {
            Button(onClick = { if (email.isNotEmpty() && password.isNotEmpty()) { isLoading = true; auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task -> isLoading = false; if (task.isSuccessful) onLoginSuccess() else Toast.makeText(context, "Erro: ${task.exception?.message}", Toast.LENGTH_LONG).show() } } }, Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = AppGreen)) { Text("ENTRAR", color = AppTextBlack, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { if (email.isNotEmpty() && password.isNotEmpty()) { isLoading = true; auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task -> if (task.isSuccessful) onLoginSuccess() else { isLoading = false; Toast.makeText(context, "Erro: ${task.exception?.message}", Toast.LENGTH_LONG).show() } } } }, Modifier.fillMaxWidth().height(50.dp), border = BorderStroke(1.dp, AppGreen)) { Text("CRIAR NOVA CONTA", color = AppGreen, fontWeight = FontWeight.Bold) }
        }
    }
}

// --- COMPONENTES VISUAIS CORRIGIDOS ---

@Composable
fun StreakCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppGray)) {
        Column(Modifier.padding(16.dp)) {
            Icon(Icons.Default.LocalFireDepartment, null, tint = AppStreakOrange)
            Text("12", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = AppStreakOrange)
            Text("dias seguidos", fontSize = 14.sp)
        }
    }
}

@Composable
fun LevelCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppGray)) {
        Column(Modifier.padding(16.dp)) {
            Icon(Icons.Default.Star, null, tint = AppLevelGreen)
            Text("Nível 8", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            LinearProgressIndicator({ 0.85f }, Modifier.fillMaxWidth().height(8.dp), color = AppLevelGreen, trackColor = Color.LightGray)
        }
    }
}

@Composable
fun StatCard(title: String, value: String, unit: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier.height(130.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppGray)) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = AppTextGray)
            Spacer(Modifier.height(8.dp))
            Text(title, fontSize = 12.sp, color = AppTextGray)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(unit, fontSize = 12.sp, color = AppTextGray)
        }
    }
}

@Composable
fun DailyChallengeCard() {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(2.dp, AppOrange)) {
        Column(Modifier.padding(16.dp)) {
            Text("🎯 Desafio Diário", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Complete qualquer treino para manter sua sequência!", fontSize = 14.sp, color = AppTextGray)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {}, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AppOrange), shape = RoundedCornerShape(12.dp)) {
                Text("Ver Treinos (+50 XP)", color = AppTextBlack, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ApptividadeTheme {
        AppNavigation()
    }
}