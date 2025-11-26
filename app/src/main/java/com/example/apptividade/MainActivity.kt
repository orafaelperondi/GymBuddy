package com.example.apptividade

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.apptividade.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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

// --- NAVEGAÇÃO ---

@Composable
fun AppNavigation() {
    val auth = FirebaseAuth.getInstance()
    // Observa o estado do usuário (se está logado ou não)
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    // Define a tela inicial baseada no login
    var currentScreen by remember {
        mutableStateOf(if (currentUser != null) "dashboard" else "login")
    }

    if (currentScreen == "login") {
        LoginScreen(
            onLoginSuccess = {
                currentUser = auth.currentUser
                currentScreen = "dashboard"
            }
        )
    } else {
        GymBuddyDashboard(
            userName = currentUser?.email ?: "Atleta",
            onLogout = {
                auth.signOut()
                currentUser = null
                currentScreen = "login"
            }
        )
    }
}

// --- TELA DE LOGIN ---

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo
        Box(
            modifier = Modifier.size(100.dp).clip(CircleShape).background(AppYellowGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = "Logo",
                modifier = Modifier.size(60.dp),
                tint = AppGreen
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("GymBuddy Access", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppTextBlack)
        Spacer(modifier = Modifier.height(32.dp))

        // Campos
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator(color = AppGreen)
        } else {
            // Botão Entrar
            Button(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        isLoading = true
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    onLoginSuccess()
                                } else {
                                    Toast.makeText(context, "Erro: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    } else {
                        Toast.makeText(context, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppGreen)
            ) {
                Text("ENTRAR", color = AppTextBlack, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botão Criar Conta
            OutlinedButton(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        isLoading = true
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val userId = task.result.user!!.uid
                                    // Cria documento no Firestore
                                    val userMap = hashMapOf(
                                        "nivel" to 1,
                                        "xp" to 0,
                                        "streak" to 0
                                    )
                                    db.collection("users").document(userId)
                                        .set(userMap)
                                        .addOnSuccessListener {
                                            isLoading = false
                                            onLoginSuccess()
                                        }
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "Erro ao criar: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                    } else {
                        Toast.makeText(context, "Preencha e-mail e senha para criar conta", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                border = BorderStroke(1.dp, AppGreen)
            ) {
                Text("CRIAR NOVA CONTA", color = AppGreen, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- DASHBOARD (TELA PRINCIPAL) ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymBuddyDashboard(userName: String, onLogout: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GymBuddy", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Sair", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppGreen, titleContentColor = AppTextBlack)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            // Saudação
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppYellowGreen)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(id = R.drawable.ic_launcher_foreground), null, Modifier.size(40.dp), tint = AppGreen)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Olá, $userName! 👋", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AppTextBlack)
                        Text("Pronto para o treino?", fontSize = 14.sp, color = AppTextGray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Cards de Status (Agora eles vão funcionar porque as funções estão lá embaixo!)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StreakCard(modifier = Modifier.weight(1f))
                LevelCard(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                StatCard("Esta semana", "4", "treinos", Icons.Default.CalendarToday, Modifier.weight(1f))
                StatCard("Meta mensal", "100%", "completo", Icons.Default.Flag, Modifier.weight(1f))
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

// --- SEUS COMPONENTES VISUAIS (Eles estão de volta!) ---

@Composable
fun StreakCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Default.LocalFireDepartment, "Streak", tint = AppStreakOrange)
            Spacer(modifier = Modifier.height(8.dp))
            Text("12", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = AppStreakOrange)
            Text("dias seguidos", fontSize = 14.sp, color = AppTextGray)
            Spacer(modifier = Modifier.height(4.dp))
            Text("🚀 Pegando ritmo!", fontSize = 12.sp, color = AppTextBlack)
        }
    }
}

@Composable
fun LevelCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppGray)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, "Nível", tint = AppLevelGreen)
                Spacer(Modifier.width(4.dp))
                Text("Nível", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppTextBlack)
                Spacer(Modifier.width(4.dp))
                Text("8", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppLevelGreen)
            }
            Text("680/800 XP", fontSize = 12.sp, color = AppTextGray)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { 0.85f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = AppLevelGreen,
                trackColor = Color.LightGray,
                strokeCap = StrokeCap.Round
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("🎯 Quase lá!", fontSize = 12.sp, color = AppTextBlack)
        }
    }
}

@Composable
fun StatCard(title: String, value: String, unit: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppGray)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = AppTextGray)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, fontSize = 12.sp, color = AppTextGray)
            Text(text = value, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AppTextBlack)
            Text(text = unit, fontSize = 12.sp, color = AppTextGray)
        }
    }
}

@Composable
fun DailyChallengeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(2.dp, AppOrange)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🎯 Desafio Diário", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppTextBlack)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Complete qualquer treino para manter sua sequência!", fontSize = 14.sp, color = AppTextGray)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AppOrange),
                shape = RoundedCornerShape(12.dp)
            ) {
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